package com.osala.BuzzMePlease.net.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.osala.BuzzMePlease.core.CrashReporter
import com.osala.BuzzMePlease.net.GAME_PORT
import com.osala.BuzzMePlease.net.NSD_ATTR_CODE
import com.osala.BuzzMePlease.net.NSD_ATTR_HOST
import com.osala.BuzzMePlease.net.NSD_ATTR_PROTOCOL
import com.osala.BuzzMePlease.net.NSD_ATTR_VERSION
import com.osala.BuzzMePlease.net.NSD_ATTR_VERSION_CODE
import com.osala.BuzzMePlease.net.NSD_SERVICE_TYPE
import com.osala.BuzzMePlease.net.PROTOCOL_VERSION
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
    /** Version du jeu chez l'animateur, vide si son annonce ne la porte pas. */
    val version: String = "",
    /**
     * La même version en nombre, pour savoir qui est en avance. Zéro quand l'annonce ne la
     * porte pas : seules les versions qui connaissent ce contrôle la publient, une annonce
     * muette vient donc forcément d'une version antérieure.
     */
    val versionCode: Long = 0,
)

/**
 * Annonce du salon sur le réseau local (mDNS / Bonjour), pour que « rejoindre avec le code »
 * fonctionne sans saisir la moindre adresse IP.
 */
class NsdAdvertiser(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun register(code: String, hostName: String, version: String = "", versionCode: Long = 0) {
        val manager = nsdManager ?: run {
            // Aucun service NSD sur cet appareil : le salon existe et accepte les
            // connexions, mais reste introuvable par son code. Le joueur voit un
            // écran de recherche qui n'aboutit jamais, sans message.
            CrashReporter.recordAnomalyOnce(
                key = "nsd-service-absent",
                message = "NsdManager indisponible : le salon ne peut pas être annoncé sur le réseau",
                context = "NsdAdvertiser.register",
            )
            return
        }
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "$SERVICE_PREFIX$code"
            serviceType = NSD_SERVICE_TYPE
            port = GAME_PORT
            setAttribute(NSD_ATTR_CODE, code)
            setAttribute(NSD_ATTR_HOST, hostName.take(32))
            setAttribute(NSD_ATTR_PROTOCOL, PROTOCOL_VERSION.toString())
            setAttribute(NSD_ATTR_VERSION, version)
            setAttribute(NSD_ATTR_VERSION_CODE, versionCode.toString())
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "annonce impossible (code $errorCode)")
                // Rappel asynchrone : aucun try/catch ne peut voir cet échec. Le
                // salon tourne, mais « rejoindre avec le code » ne le trouvera
                // jamais — c'est le mode d'entrée principal du jeu qui tombe, en
                // silence complet. Le code d'erreur du framework est la seule
                // information exploitable ; ni le code du salon ni le nom de
                // l'animateur ne sont transmis.
                CrashReporter.recordAnomalyOnce(
                    key = "nsd-register-$errorCode",
                    message = "Annonce mDNS du salon refusée (${nsdErrorName(errorCode)})",
                    context = "NsdAdvertiser.onRegistrationFailed",
                )
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "salon annoncé : ${serviceInfo.serviceName}")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
        }
        listener = registration
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            .onFailure { failure ->
                Log.w(TAG, "registerService: ${failure.message}")
                CrashReporter.recordOnce(
                    key = "nsd-register-throw",
                    throwable = failure,
                    context = "NsdAdvertiser.registerService",
                )
            }
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

/**
 * Nomme un code d'erreur [NsdManager], pour que le rapport soit lisible sans
 * aller chercher la constante correspondante dans la documentation.
 */
internal fun nsdErrorName(errorCode: Int): String = when (errorCode) {
    NsdManager.FAILURE_INTERNAL_ERROR -> "FAILURE_INTERNAL_ERROR"
    NsdManager.FAILURE_ALREADY_ACTIVE -> "FAILURE_ALREADY_ACTIVE"
    NsdManager.FAILURE_MAX_LIMIT -> "FAILURE_MAX_LIMIT"
    else -> "code $errorCode"
}

object NsdBrowser {

    private const val TAG = "NsdBrowser"

    /**
     * Le balayage est relancé à ce rythme. `discoverServices` n'annonce un service qu'une fois :
     * si sa résolution échoue, ou si le téléphone ne signale jamais un salon ouvert après le
     * début du balayage, la seule issue est de recommencer. Sans cela, le joueur qui ouvre
     * « rejoindre » avant que l'animateur ne crée le salon ne le voit jamais apparaître.
     */
    private const val RESCAN_MILLIS = 6_000L

    /** Pause entre l'arrêt d'un balayage et le suivant : NsdManager libère le sien en différé. */
    private const val RESCAN_GAP_MILLIS = 400L

    private const val MAX_RESOLVE_ATTEMPTS = 4
    private const val RESOLVE_RETRY_MILLIS = 500L

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
        }.onFailure { failure ->
            // Sans ce verrou, plusieurs constructeurs filtrent le multicast et la
            // découverte reste vide sans erreur : c'est précisément le symptôme
            // « je ne vois aucun salon » impossible à reproduire au bureau.
            CrashReporter.recordOnce(
                key = "nsd-multicast-lock",
                throwable = failure,
                context = "NsdBrowser.multicastLock",
            )
        }.getOrNull()

        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        fun newListener() = object : NsdManager.DiscoveryListener {
            // Un balayage qui ne démarre pas n'est plus fatal : le suivant réessaiera.
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "découverte impossible (code $errorCode)")
                // Le repli par relance masque une panne durable : l'écran
                // « rejoindre » reste vide indéfiniment, et rien ne le signale.
                // `recordAnomalyOnce` est indispensable ici — le balayage repart
                // toutes les six secondes, ce serait dix rapports par minute.
                CrashReporter.recordAnomalyOnce(
                    key = "nsd-discover-$errorCode",
                    message = "Balayage mDNS refusé (${nsdErrorName(errorCode)}) : aucun salon ne sera trouvé",
                    context = "NsdBrowser.onStartDiscoveryFailed",
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                toResolve.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        // Combien de fois on a déjà buté sur ce service, par nom.
        val attempts = mutableMapOf<String, Int>()

        val resolver = launch {
            for (info in toResolve) {
                if (!isActive) break
                val name = info.serviceName.orEmpty()
                val room = manager.resolveSuspending(info)?.toDiscoveredRoom()
                if (room != null) {
                    attempts.remove(name)
                    trySend(room)
                    continue
                }
                // Une résolution ratée n'est pas rejouée par Android : le service reste « déjà
                // trouvé » et l'écran resterait vide jusqu'à ce qu'on en sorte. On réessaie
                // nous-mêmes, en espaçant — l'hôte finit souvent de s'annoncer une seconde après.
                val tries = (attempts[name] ?: 0) + 1
                attempts[name] = tries
                if (tries <= MAX_RESOLVE_ATTEMPTS) {
                    launch {
                        delay(RESOLVE_RETRY_MILLIS * tries)
                        toResolve.trySend(info)
                    }
                }
            }
        }

        // Le balayage se relance en boucle tant que l'écran écoute : c'est ce qui fait
        // apparaître un salon ouvert après coup, sans que le joueur ait à ressortir.
        var current: NsdManager.DiscoveryListener? = null
        val scanner = launch {
            while (isActive) {
                val listener = newListener()
                val started = runCatching {
                    manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                }.onFailure { failure ->
                    Log.w(TAG, "discoverServices: ${failure.message}")
                    CrashReporter.recordOnce(
                        key = "nsd-discover-throw",
                        throwable = failure,
                        context = "NsdBrowser.discoverServices",
                    )
                }.isSuccess
                if (!started) {
                    delay(RESCAN_MILLIS)
                    continue
                }
                current = listener
                delay(RESCAN_MILLIS)
                runCatching { manager.stopServiceDiscovery(listener) }
                current = null
                delay(RESCAN_GAP_MILLIS)
            }
        }

        awaitClose {
            scanner.cancel()
            resolver.cancel()
            toResolve.close()
            current?.let { runCatching { manager.stopServiceDiscovery(it) } }
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
            // Un salon introuvable n'arrive pas ici : `withTimeoutOrNull` rend
            // `null` sans lever. Une exception à ce niveau est donc un vrai défaut
            // de la découverte, pas une recherche infructueuse.
            // Le code du salon reste hors du rapport : c'est le secret qui permet
            // de rejoindre une partie.
            Log.w(TAG, "recherche du salon $code: ${e.message}")
            CrashReporter.recordOnce(
                key = "nsd-find-room",
                throwable = e,
                context = "NsdBrowser.findRoom",
            )
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
        val version = attributes[NSD_ATTR_VERSION]?.toString(Charsets.UTF_8).orEmpty()
        val versionCode = attributes[NSD_ATTR_VERSION_CODE]
            ?.toString(Charsets.UTF_8)?.toLongOrNull() ?: 0L
        return DiscoveredRoom(
            code = code.trim().uppercase(),
            hostName = hostName,
            version = version,
            versionCode = versionCode,
            address = address,
            port = if (port > 0) port else GAME_PORT,
        )
    }
}
