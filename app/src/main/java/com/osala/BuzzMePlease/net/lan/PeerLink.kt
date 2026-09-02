package com.osala.BuzzMePlease.net.lan

import android.os.SystemClock
import android.util.Log
import com.osala.BuzzMePlease.core.CrashReporter
import com.osala.BuzzMePlease.net.NetMessage
import com.osala.BuzzMePlease.net.ProtocolJson
import kotlinx.coroutines.CancellationException
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

    /**
     * Dernier signe de vie reçu. TCP ne dit rien d'une liaison à demi ouverte — un Wi-Fi qui
     * décroche, un téléphone qui s'endort — et la lecture attendrait alors indéfiniment. C'est
     * donc au protocole de trancher : au-delà d'un certain silence, la connexion est morte.
     */
    @Volatile
    private var lastHeardAt = SystemClock.elapsedRealtime()

    fun silentForMillis(): Long = SystemClock.elapsedRealtime() - lastHeardAt

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
                lastHeardAt = SystemClock.elapsedRealtime()
                if (line.isBlank()) continue
                val message = runCatching { ProtocolJson.decodeFromString<NetMessage>(line) }
                    .onFailure { failure ->
                        Log.w(TAG, "message illisible: ${failure.message}")
                        // Une trame illisible n'est jamais normale : les deux bouts
                        // parlent le même protocole, généré par le même code. Cela
                        // signe donc soit deux versions incompatibles autour de la
                        // table, soit un défaut d'encodage — et dans les deux cas le
                        // message est perdu en silence, ce qui se traduit par un
                        // plateau désynchronisé sans explication.
                        // `recordOnce` : sur un décalage de protocole, CHAQUE trame
                        // échoue, et le flux en produit plusieurs par seconde.
                        CrashReporter.recordOnce(
                            key = "peer-decode",
                            throwable = failure,
                            context = "PeerLink.decode",
                        )
                    }
                    .getOrNull() ?: continue

                // Traitement isolé de la lecture.
                //
                // `onMessage` exécute la logique de jeu (GameEngine, mise à jour
                // d'état, diffusion). Sans ce try, une exception venue de là était
                // rattrapée par le catch de la boucle et journalisée « lecture
                // interrompue » : un bug de logique se déguisait en coupure réseau,
                // la boucle rendait la main et le joueur était silencieusement
                // déconnecté. On remonte la vraie cause et on garde la connexion.
                try {
                    onMessage(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashReporter.record(e, "PeerLink.onMessage")
                }
            }
        } catch (e: CancellationException) {
            // Le salon se ferme ou l'écran est détruit : annulation normale, qu'il
            // faut relancer. Le `catch (Exception)` ci-dessous l'avalait, ce qui
            // rompait la coopération à l'annulation de tout le périmètre appelant.
            throw e
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
