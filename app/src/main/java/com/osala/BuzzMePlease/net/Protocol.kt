package com.osala.BuzzMePlease.net

import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.model.RoomAlert
import com.osala.BuzzMePlease.model.RoomState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1

/** Port TCP du salon. Fixe : il permet de reconstruire l'adresse de l'hôte après une passation. */
const val GAME_PORT = 47821

/** Type de service mDNS annoncé par l'hôte. */
const val NSD_SERVICE_TYPE = "_buzzme._tcp."
const val NSD_ATTR_CODE = "code"
const val NSD_ATTR_HOST = "host"
const val NSD_ATTR_PROTOCOL = "proto"

val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

/** Messages échangés en JSON, une ligne par message, sur une connexion TCP persistante. */
@Serializable
sealed interface NetMessage

// ------------------------------------------------------------------ client -> hôte

@Serializable
@SerialName("hello")
data class Hello(
    val playerId: String,
    val name: String,
    val protocol: Int = PROTOCOL_VERSION,
) : NetMessage

@Serializable
@SerialName("ping")
data class Ping(val seq: Int, val clientSent: Long) : NetMessage

@Serializable
@SerialName("buzz")
data class BuzzRequest(
    val round: Int,
    /** Heure murale locale de l'appui. */
    val clientWall: Long,
    /** Décalage mesuré à ajouter pour obtenir l'horloge de l'hôte. */
    val offset: Long,
    /** Aller-retour minimal observé : sert de marge d'incertitude. */
    val rtt: Long,
) : NetMessage

@Serializable
@SerialName("rename")
data class RenameRequest(val name: String) : NetMessage

// ------------------------------------------------------------------ hôte -> client

@Serializable
@SerialName("welcome")
data class Welcome(val code: String, val hostId: String, val protocol: Int = PROTOCOL_VERSION) : NetMessage

@Serializable
@SerialName("pong")
data class Pong(val seq: Int, val clientSent: Long, val hostWall: Long) : NetMessage

@Serializable
@SerialName("state")
data class StateSync(val state: RoomState) : NetMessage

/**
 * Passation du rôle d'hôte. Tout le monde se reconnecte à [address] ; le nouvel hôte
 * reprend [state] tel quel, scores et statuts compris.
 */
@Serializable
@SerialName("transfer")
data class HostTransfer(
    val newHostId: String,
    val address: String,
    val port: Int = GAME_PORT,
    val state: RoomState,
) : NetMessage

/** Une annonce de l'animateur pour tout le salon : carton, ou fin de partie. */
@Serializable
@SerialName("alert")
data class AlertBroadcast(val alert: RoomAlert) : NetMessage

@Serializable
@SerialName("bye")
data class Bye(val cause: ByeCause, val kicked: Boolean = false) : NetMessage

/**
 * Pourquoi la liaison se termine. Un motif plutôt qu'une phrase : chaque appareil l'affiche
 * dans sa propre langue, même si l'hôte joue dans une autre.
 */
@Serializable
enum class ByeCause { HOST_CLOSED, KICKED, LEFT, VERSION_MISMATCH }

/** Le texte à afficher pour ce motif, dans la langue de l'appareil qui le reçoit. */
fun ByeCause.messageRes(): Int = when (this) {
    ByeCause.HOST_CLOSED -> R.string.end_host_closed
    ByeCause.KICKED -> R.string.end_kicked
    ByeCause.LEFT -> R.string.end_left
    ByeCause.VERSION_MISMATCH -> R.string.end_version
}
