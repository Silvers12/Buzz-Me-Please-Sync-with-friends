package fr.buzzme.net

import fr.buzzme.model.RoomState
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

@Serializable
@SerialName("bye")
data class Bye(val reason: String, val kicked: Boolean = false) : NetMessage
