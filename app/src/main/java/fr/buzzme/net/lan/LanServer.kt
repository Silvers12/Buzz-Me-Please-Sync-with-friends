package fr.buzzme.net.lan

import android.util.Log
import fr.buzzme.net.GAME_PORT
import fr.buzzme.net.Hello
import fr.buzzme.net.NetMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Côté hôte : accepte les connexions des joueurs et leur diffuse l'état du salon.
 * Topologie en étoile — l'hôte est le seul point de vérité, ce qui supprime tout risque
 * de désaccord sur « qui a buzzé en premier ».
 */
class LanServer(
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onHello(peer: PeerLink, hello: Hello)
        fun onMessage(peer: PeerLink, message: NetMessage)
        fun onGone(peer: PeerLink)
        fun onListening()
        fun onError(message: String)
    }

    private val peers = CopyOnWriteArrayList<PeerLink>()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start(port: Int = GAME_PORT) {
        if (acceptJob != null) return
        acceptJob = scope.launch(Dispatchers.IO) {
            val socket = bind(port)
            if (socket == null) {
                callbacks.onError("Impossible d'ouvrir le port $port")
                return@launch
            }
            serverSocket = socket
            callbacks.onListening()
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    if (isActive) Log.d(TAG, "accept terminé: ${e.message}")
                    break
                }
                handle(client)
            }
        }
    }

    private suspend fun bind(port: Int): ServerSocket? {
        repeat(BIND_ATTEMPTS) { attempt ->
            val socket = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port), BACKLOG)
                }
            }.getOrNull()
            if (socket != null) return socket
            Log.d(TAG, "port $port occupé, nouvelle tentative (${attempt + 1})")
            delay(BIND_RETRY_MILLIS)
        }
        return null
    }

    private fun handle(socket: Socket) {
        val peer = PeerLink(socket)
        peer.startWriter(scope)
        scope.launch(Dispatchers.IO) {
            peer.readLoop { message ->
                if (message is Hello) {
                    // Un joueur qui revient après une coupure remplace sa connexion précédente.
                    peers.filter { it.playerId == message.playerId && it !== peer }
                        .forEach { stale ->
                            peers.remove(stale)
                            stale.close()
                        }
                    peer.playerId = message.playerId
                    if (!peers.contains(peer)) peers.add(peer)
                    callbacks.onHello(peer, message)
                } else if (peer.playerId != null) {
                    callbacks.onMessage(peer, message)
                }
            }
            peers.remove(peer)
            peer.close()
            callbacks.onGone(peer)
        }
    }

    fun broadcast(message: NetMessage) {
        peers.forEach { it.send(message) }
    }

    fun sendTo(playerId: String, message: NetMessage) {
        peers.firstOrNull { it.playerId == playerId }?.send(message)
    }

    fun addressOf(playerId: String): String? =
        peers.firstOrNull { it.playerId == playerId }?.remoteAddress?.takeIf { it.isNotBlank() }

    /** true si une connexion vivante existe pour ce joueur (une reconnexion en remplace une ancienne). */
    fun isConnected(playerId: String): Boolean = peers.any { it.playerId == playerId }

    fun disconnect(playerId: String) {
        peers.filter { it.playerId == playerId }.forEach {
            peers.remove(it)
            it.close()
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        peers.forEach { it.close() }
        peers.clear()
    }

    private companion object {
        const val TAG = "LanServer"
        const val BACKLOG = 24
        const val BIND_ATTEMPTS = 12
        const val BIND_RETRY_MILLIS = 250L
    }
}
