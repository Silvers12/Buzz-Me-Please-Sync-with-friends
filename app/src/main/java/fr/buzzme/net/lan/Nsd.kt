package fr.buzzme.net.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import fr.buzzme.net.GAME_PORT
import fr.buzzme.net.NSD_ATTR_CODE
import fr.buzzme.net.NSD_ATTR_HOST
import fr.buzzme.net.NSD_ATTR_PROTOCOL
import fr.buzzme.net.NSD_SERVICE_TYPE
import fr.buzzme.net.PROTOCOL_VERSION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DiscoveredRoom(
    val code: String,
    val hostName: String,
    val address: String,
    val port: Int,
)

/**
 * Annonce du salon sur le réseau local (mDNS / Bonjour), pour que « rejoindre avec le code »
 * fonctionne sans saisir la moindre adresse IP.
 */
class NsdAdvertiser(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun register(code: String, hostName: String) {
        val manager = nsdManager ?: return
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "$SERVICE_PREFIX$code"
            serviceType = NSD_SERVICE_TYPE
            port = GAME_PORT
            setAttribute(NSD_ATTR_CODE, code)
            setAttribute(NSD_ATTR_HOST, hostName.take(32))
            setAttribute(NSD_ATTR_PROTOCOL, PROTOCOL_VERSION.toString())
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "annonce impossible (code $errorCode)")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "salon annoncé : ${serviceInfo.serviceName}")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
        }
        listener = registration
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            .onFailure { Log.w(TAG, "registerService: ${it.message}") }
    }

    fun unregister() {
        val manager = nsdManager ?: return
        listener?.let { runCatching { manager.unregisterService(it) } }
        listener = null
    }

    private companion object {
        const val TAG = "NsdAdvertiser"
        const val SERVICE_PREFIX = "BuzzMe-"
    }
}

object NsdBrowser {

    private const val TAG = "NsdBrowser"

    /**
     * Flux des salons visibles sur le réseau local. Les résolutions sont sérialisées :
     * [NsdManager] n'en accepte qu'une à la fois sur la plupart des versions d'Android.
     */
    fun discover(context: Context): Flow<DiscoveredRoom> = callbackFlow {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (manager == null) {
            close()
            awaitClose { }
            return@callbackFlow
        }

        // Sans ce verrou, certains constructeurs filtrent le multicast et la découverte reste vide.
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = runCatching {
            wifi?.createMulticastLock("buzzme-nsd")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()

        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "découverte impossible (code $errorCode)")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                toResolve.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        val resolver = launch {
            for (info in toResolve) {
                if (!isActive) break
                val resolved = manager.resolveSuspending(info)
                    ?: run {
                        // FAILURE_ALREADY_ACTIVE et consorts : une seule nouvelle tentative.
                        delay(400)
                        manager.resolveSuspending(info)
                    }
                val room = resolved?.toDiscoveredRoom() ?: continue
                trySend(room)
            }
        }

        runCatching {
            manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            Log.w(TAG, "discoverServices: ${it.message}")
            close()
        }

        awaitClose {
            resolver.cancel()
            toResolve.close()
            runCatching { manager.stopServiceDiscovery(discoveryListener) }
            runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        }
    }

    /** Cherche un salon précis, jusqu'à [timeoutMillis]. */
    suspend fun findRoom(context: Context, code: String, timeoutMillis: Long): DiscoveredRoom? =
        try {
            withTimeoutOrNull(timeoutMillis) {
                discover(context).first { it.code.equals(code, ignoreCase = true) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "recherche du salon $code: ${e.message}")
            null
        }

    @Suppress("DEPRECATION")
    private suspend fun NsdManager.resolveSuspending(info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }
            }
            runCatching { resolveService(info, listener) }
                .onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.toDiscoveredRoom(): DiscoveredRoom? {
        val address = host?.hostAddress ?: return null
        val attributes = attributes ?: emptyMap()
        val code = attributes[NSD_ATTR_CODE]?.toString(Charsets.UTF_8)
            ?: serviceName?.substringAfter('-', "")?.takeIf { it.isNotBlank() }
            ?: return null
        val hostName = attributes[NSD_ATTR_HOST]?.toString(Charsets.UTF_8).orEmpty()
        return DiscoveredRoom(
            code = code.trim().uppercase(),
            hostName = hostName,
            address = address,
            port = if (port > 0) port else GAME_PORT,
        )
    }
}
