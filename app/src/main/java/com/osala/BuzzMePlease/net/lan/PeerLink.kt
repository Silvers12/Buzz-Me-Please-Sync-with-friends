package com.osala.BuzzMePlease.net.lan

import android.util.Log
import com.osala.BuzzMePlease.net.NetMessage
import com.osala.BuzzMePlease.net.ProtocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * Une connexion TCP persistante avec un pair, en JSON délimité par des sauts de ligne.
 *
 * L'écriture passe par une file dédiée : appuyer sur le buzzer ne bloque jamais le thread UI,
 * et l'envoi part immédiatement (Nagle désactivé) pour ne pas ajouter de latence au buzz.
 */
class PeerLink(private val socket: Socket) {

    /** Renseigné dès réception du Hello (côté hôte) ou connu d'avance (côté client). */
    @Volatile
    var playerId: String? = null

    @Volatile
    private var closed = false

    private val outbox = Channel<String>(Channel.UNLIMITED)
    private var writerJob: Job? = null

    val remoteAddress: String
        get() = runCatching { socket.inetAddress?.hostAddress }.getOrNull().orEmpty()

    init {
        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 0
        }
    }

    fun startWriter(scope: CoroutineScope) {
        if (writerJob != null) return
        writerJob = scope.launch(Dispatchers.IO) {
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            try {
                for (line in outbox) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "écriture interrompue: ${e.message}")
            } finally {
                close()
            }
        }
    }

    fun send(message: NetMessage) {
        if (closed) return
        val line = runCatching { ProtocolJson.encodeToString<NetMessage>(message) }.getOrNull() ?: return
        outbox.trySend(line)
    }

    /**
     * Boucle de lecture bloquante. À lancer sur [Dispatchers.IO].
     * Se termine quand le pair ferme la connexion ou qu'une erreur survient.
     */
    suspend fun readLoop(onMessage: suspend (NetMessage) -> Unit) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val message = runCatching { ProtocolJson.decodeFromString<NetMessage>(line) }
                    .onFailure { Log.w(TAG, "message illisible: ${it.message}") }
                    .getOrNull() ?: continue
                onMessage(message)
            }
        } catch (e: Exception) {
            Log.d(TAG, "lecture interrompue: ${e.message}")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        outbox.close()
        runCatching { socket.close() }
    }

    private companion object {
        const val TAG = "PeerLink"
    }
}
