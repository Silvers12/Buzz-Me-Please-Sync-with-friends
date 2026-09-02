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
        var handlerFailures = 0
        try {
            while (true) {
                val line = reader.readLine() ?: break
                lastHeardAt = SystemClock.elapsedRealtime()
                if (line.isBlank()) continue
                val message = runCatching { ProtocolJson.decodeFromString<NetMessage>(line) }
                    .onFailure { failure ->
                        // Le log local garde l'exception complète : logcat ne quitte
                        // pas l'appareil.
                        Log.w(TAG, "message illisible: ${failure.message}", failure)
                        // Crashlytics, en revanche, ne reçoit QUE la longueur.
                        //
                        // Ne jamais transmettre `failure` ici : le message d'une
                        // `JsonDecodingException` contient la trame brute — en
                        // dessous de 200 caractères, `minify()` la renvoie telle
                        // quelle. Or une trame porte le nom des joueurs, le code du
                        // salon (le secret qui permet de le rejoindre) et parfois une
                        // adresse IP, et un `Hello` ou un `Welcome` tient largement
                        // sous cette limite : il partirait intégralement.
                        //
                        // Le chemin est loin d'être théorique : `readLine()` rend les
                        // caractères restants à la fermeture du flux, donc toute
                        // coupure en milieu de trame — Wi-Fi qui décroche, téléphone
                        // qui s'endort, chien de garde qui ferme — produit un JSON
                        // partiel qui échoue au décodage. C'est le mode de
                        // défaillance le plus courant de cette couche.
                        //
                        // `recordAnomalyOnce` : sur un décalage de protocole CHAQUE
                        // trame échoue, et le flux en produit plusieurs par seconde.
                        CrashReporter.recordAnomalyOnce(
                            key = "peer-decode",
                            message = "Trame illisible (${line.length} car.), message perdu",
                            context = "PeerLink.decode",
                        )
                    }
                    .getOrNull() ?: continue

                // Traitement isolé de la lecture.
                //
                // `onMessage` exécute la logique de jeu (GameEngine, mise à jour
                // d'état, diffusion). Sans ce try, une exception venue de là était
                // rattrapée par le catch de la boucle et journalisée « lecture
                // interrompue » : un bug de logique se déguisait en coupure réseau.
                // On remonte donc la vraie cause — mais sans garder la liaison
                // indéfiniment, voir la sortie bornée ci-dessous.
                try {
                    onMessage(message)
                    handlerFailures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // `recordOnce` et non `record` : c'est le chemin le plus chaud de
                    // l'application. L'hôte traite une sonde toutes les 2 s par
                    // joueur (~200 messages/minute à sept invités) et l'invité un
                    // `StateSync` à chaque changement d'état. Un défaut déterministe
                    // produirait un rapport par message, indéfiniment, et noierait
                    // au passage le tampon de fil d'Ariane — évinçant les miettes
                    // utiles au moment précis où elles serviraient.
                    CrashReporter.recordOnce("peer-onmessage", e, "PeerLink.onMessage")

                    // Sortie bornée. Garder la boucle ouverte est bon pour un échec
                    // passager, mais devient nuisible si l'état du pair est
                    // durablement incohérent : côté hôte un `Hello` en échec laisse
                    // un pair inscrit que le moteur n'a jamais admis, et un `Pong`
                    // en échec prive l'invité de sa synchro d'horloge sans que son
                    // chien de garde ne le sauve — `lastHeardAt` étant rafraîchi par
                    // n'importe quelle ligne reçue. Après plusieurs échecs
                    // consécutifs on rend la main, ce qui rétablit le comportement
                    // d'origine : nettoyage du pair puis reconnexion, laquelle
                    // repart d'un état propre.
                    if (++handlerFailures >= MAX_HANDLER_FAILURES) {
                        Log.w(TAG, "$handlerFailures échecs de traitement consécutifs, fermeture de la liaison")
                        break
                    }
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

        /**
         * Échecs consécutifs de traitement au-delà desquels la liaison est coupée.
         * Trois laisse passer un incident ponctuel sans jamais entretenir une
         * session durablement incohérente.
         */
        const val MAX_HANDLER_FAILURES = 3
    }
}
