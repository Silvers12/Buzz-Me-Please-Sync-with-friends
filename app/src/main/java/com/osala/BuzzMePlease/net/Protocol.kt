package com.osala.BuzzMePlease.net

import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.model.RoomAlert
import com.osala.BuzzMePlease.model.RoomState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version du dialogue entre appareils. **À incrémenter dès qu'un message ou l'état du salon
 * change de forme**, même quand le changement paraît anodin : deux versions qui se comprennent
 * à moitié donnent un salon incohérent, où l'un voit un buzzer rouge que l'autre voit bleu.
 * Mieux vaut refuser la liaison et le dire.
 *
 * 1 → 2 : les verdicts « faux » deviennent une liste, et l'animateur peut diffuser des annonces
 * (cartons, fin de partie) que les versions antérieures ignorent.
 *
 * 2 → 3 : l'état du salon porte la parole donnée à la main par l'animateur, et chaque joueur
 * ses cartons jaunes et rouges.
 *
 * 3 → 4 : chaque joueur porte la manche où il a été remis en jeu.
 */
const val PROTOCOL_VERSION = 4

/** Port TCP du salon. Fixe : il permet de reconstruire l'adresse de l'hôte après une passation. */
const val GAME_PORT = 47821

/** Type de service mDNS annoncé par l'hôte. */
const val NSD_SERVICE_TYPE = "_buzzme._tcp."
const val NSD_ATTR_CODE = "code"
const val NSD_ATTR_HOST = "host"
const val NSD_ATTR_PROTOCOL = "proto"

/** Version du jeu chez l'animateur, lisible dans la liste des salons avant de s'y connecter. */
const val NSD_ATTR_VERSION = "ver"

/**
 * Le même numéro sous sa forme comparable. « 1.10 » et « 1.9 » ne se rangent pas dans le bon
 * ordre par leurs noms : c'est ce nombre qui dit laquelle des deux versions est en avance.
 */
const val NSD_ATTR_VERSION_CODE = "vc"

/**
 * Les valeurs par défaut ne sont pas écrites : un champ absent reprend la valeur déclarée
 * dans le modèle, qui est la même des deux côtés puisque le salon impose la même version.
 * L'état entier étant rediffusé à chaque changement, tout ce qui n'est pas dit est autant
 * de moins à envoyer — un plateau de huit joueurs au repos ne coûte presque rien.
 */
val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
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
    /**
     * Version du jeu installée chez celui qui frappe à la porte. C'est elle qui fait foi :
     * même version, même protocole et mêmes règles. Zéro pour une version antérieure à ce
     * contrôle — qui n'a donc pas la bonne.
     */
    val appVersion: Long = 0,
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
data class Welcome(
    val code: String,
    val hostId: String,
    val protocol: Int = PROTOCOL_VERSION,
    /** Version du jeu chez l'animateur : le joueur vérifie de son côté aussi. */
    val appVersion: Long = 0,
) : NetMessage

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
