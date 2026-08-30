package com.osala.BuzzMePlease.game

import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.RoomAlert
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LinkPhase { STARTING, SEARCHING, CONNECTING, CONNECTED, RECONNECTING, CLOSED, ERROR }

data class LinkStatus(
    val phase: LinkPhase = LinkPhase.STARTING,
    val detail: String = "",
    /** Latence aller-retour mesurée avec l'hôte (0 quand on est l'hôte). */
    val pingMillis: Long = 0,
    /** Incertitude de la synchronisation d'horloge, en ms. */
    val clockPrecisionMillis: Long = 0,
) {
    val isConnected: Boolean get() = phase == LinkPhase.CONNECTED
}

/** Raison pour laquelle la session s'est terminée, à afficher à l'utilisateur. */
data class SessionEnded(val message: String, val kicked: Boolean = false)

/**
 * Vue d'un salon, indépendamment de la couche de transport qui le porte.
 * Les commandes réservées à l'hôte sont ignorées silencieusement si on ne l'est pas.
 */
interface RoomSession {

    val state: StateFlow<RoomState>
    val link: StateFlow<LinkStatus>

    /**
     * Vrai dès que le salon a répondu. Avant cela il n'y a rien à montrer du jeu : un code tapé
     * au hasard ne doit pas ouvrir un salon d'apparence normale. Reste vrai ensuite, y compris
     * pendant une reconnexion — on ne renvoie pas un joueur à l'écran d'attente en pleine partie.
     */
    val joined: StateFlow<Boolean>
    val ended: StateFlow<SessionEnded?>

    /**
     * Les annonces de l'animateur — cartons, fin de partie. Un flux d'événements et non un état :
     * chaque alerte passe une fois, sur tous les appareils, et ne se rejoue pas pour qui arrive
     * après coup.
     */
    val alerts: Flow<RoomAlert>

    /** Identifiant local, stable d'une partie à l'autre. */
    val myId: String

    /** Manche pour laquelle on a appuyé localement, avant même l'accusé de réception de l'hôte. */
    val localBuzzRound: StateFlow<Int?>

    val isHost: Boolean get() = state.value.hostId == myId

    /**
     * Heure courante exprimée sur l'horloge de référence du salon, celle de l'hôte. C'est la
     * base commune qui permet à chaque appareil de déclencher son décompte au même instant.
     */
    fun nowHostMillis(): Long

    // ------------------------------------------------------------ joueur
    /**
     * @param atUptimeMillis horodatage brut de l'événement tactile. La session le ramène
     *   elle-même sur l'horloge du salon : chez l'hôte par son heure murale, chez le joueur par
     *   sa base monotone, qu'aucun réglage d'heure ne déplace.
     */
    fun buzz(atUptimeMillis: Long)

    fun rename(name: String)

    // -------------------------------------------------------------- hôte
    fun arm()
    fun reset()

    /** Mauvaise réponse : buzzer rouge et son de sanction, sans que la main ne bouge. */
    fun markWrong()

    /**
     * Bonne réponse : buzzer vert et son de récompense chez le joueur, puis extinction des
     * buzzers — la manche est jouée.
     */
    fun markRight()

    /** Au suivant : la main descend au buzz d'après, et le buzzer précédent s'éteint. */
    fun passSpeaker()

    /**
     * La parole donnée de la main de l'animateur, depuis la liste des joueurs. Elle passe
     * devant le classement, et le désigné n'a pas eu besoin de buzzer.
     */
    fun giveFloor(playerId: String)

    fun setStatus(playerId: String, status: PlayerStatus)

    /** Le plateau remis à neuf : résultats effacés et tous les buzzers rallumés. */
    fun resetBoard()

    fun kick(playerId: String)
    fun addPoints(playerId: String, delta: Int)
    fun resetScores()
    fun transferHost(playerId: String)
    fun setOptions(options: RoomOptions)

    /**
     * Annonce à tout le salon. Le nom et le score du joueur visé sont complétés par l'hôte,
     * qui compte au passage le carton reçu.
     */
    fun sendAlert(alert: RoomAlert)

    /** L'ardoise effacée : les cartons de ce joueur retombent à zéro. */
    fun clearCards(playerId: String)

    fun close()
}
