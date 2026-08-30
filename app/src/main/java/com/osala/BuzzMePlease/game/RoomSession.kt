package com.osala.BuzzMePlease.game

import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
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
    val ended: StateFlow<SessionEnded?>

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

    /** Convertit une heure locale (epoch ms) vers la même horloge de référence. */
    fun toHostMillis(localWallMillis: Long): Long

    // ------------------------------------------------------------ joueur
    /** @param atWallMillis heure exacte de l'appui, issue de l'événement tactile. */
    fun buzz(atWallMillis: Long)

    fun rename(name: String)

    // -------------------------------------------------------------- hôte
    fun arm()
    fun reset()

    /** Mauvaise réponse : buzzer rouge et son de sanction, sans que la main ne bouge. */
    fun markWrong()

    /**
     * Bonne réponse : buzzer vert et son de récompense chez le joueur comme chez l'animateur,
     * puis extinction des buzzers — la manche est jouée.
     */
    fun markRight()

    /** Au suivant : la main descend au buzz d'après, et le buzzer précédent s'éteint. */
    fun passSpeaker()

    fun setStatus(playerId: String, status: PlayerStatus)
    fun kick(playerId: String)
    fun addPoints(playerId: String, delta: Int)
    fun resetScores()
    fun transferHost(playerId: String)
    fun setOptions(options: RoomOptions)

    fun close()
}
