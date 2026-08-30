package com.osala.BuzzMePlease.model

import kotlinx.serialization.Serializable
import com.osala.BuzzMePlease.core.AppLocale
import java.util.Locale
import java.util.concurrent.TimeUnit

/** État d'un joueur vis-à-vis de la partie. */
@Serializable
enum class PlayerStatus {
    /** Peut buzzer quand l'hôte lance le top. */
    ACTIVE,

    /** Éliminé : buzzer gris, aucun buzz accepté tant que l'hôte ne le réactive pas. */
    ELIMINATED,
}

/**
 * Ce que l'animateur peut annoncer à la cantonade : une sanction pour quelqu'un, ou la fin.
 *
 * Un motif, jamais une phrase toute faite — comme [com.osala.BuzzMePlease.net.ByeCause]. Chaque
 * appareil l'écrit dans sa propre langue, même si l'animateur joue dans une autre.
 */
@Serializable
enum class AlertKind { YELLOW_CARD, RED_CARD, GAME_OVER }

/**
 * Une alerte en route vers tous les téléphones. C'est un événement, pas un état : elle traverse
 * le salon une fois, s'affiche deux secondes, et ne laisse aucune trace dans [RoomState] — un
 * joueur qui arrive après coup n'a pas à recevoir le carton de la manche précédente.
 *
 * Le nom et le score voyagent avec elle plutôt que d'être relus à l'arrivée : l'annonce reste
 * juste même si le joueur quitte le salon dans la seconde.
 */
@Serializable
data class RoomAlert(
    val kind: AlertKind,
    val playerId: String = "",
    val playerName: String = "",
    val score: Int = 0,
    /** Vrai quand la partie s'achève sans vainqueur net : personne n'a marqué, ou égalité. */
    val tied: Boolean = false,
)

/** Où en est la manche en cours. */
@Serializable
enum class RoundState {
    /** Buzzers éteints, on attend le top de l'hôte. */
    IDLE,

    /**
     * Décompte « 3 · 2 · 1 · TOP ! » en cours. `armedAtMillis` porte l'instant exact de
     * l'armement : chaque appareil l'atteint au même moment sur l'horloge de l'hôte,
     * personne n'est donc servi avant les autres.
     */
    COUNTDOWN,

    /** Buzzers armés (verts). */
    ARMED,

    /** Quelqu'un a buzzé : buzzers verrouillés (mode duel uniquement). */
    LOCKED,
}

/** Mode de jeu de la manche. */
@Serializable
enum class GameMode {
    /** Le premier qui buzze verrouille tous les autres. */
    DUEL,

    /** Tout le monde peut buzzer, on obtient un classement complet. */
    COURSE,
}

@Serializable
data class RoomOptions(
    val mode: GameMode = GameMode.DUEL,
    /** Décompte « 3 · 2 · 1 · TOP ! » avant l'armement des buzzers. */
    val countdown: Boolean = true,
    /** Son + vibration à l'armement et au buzz. */
    val sound: Boolean = true,
    /**
     * Plateau masqué sur les téléphones des joueurs : chacun ne voit plus que sa propre ligne,
     * avec son score, et l'animateur garde le tableau complet. De quoi garder le suspense
     * jusqu'au dénouement — personne ne sait où en sont les autres.
     */
    val hideBoard: Boolean = false,
)

@Serializable
data class Player(
    val id: String,
    val name: String,
    val status: PlayerStatus = PlayerStatus.ACTIVE,
    val score: Int = 0,
    val connected: Boolean = true,
    /** Latence aller-retour mesurée avec l'hôte, en ms (0 pour l'hôte lui-même). */
    val pingMillis: Long = 0,
    val joinedAt: Long = 0,
) {
    val isEliminated: Boolean get() = status == PlayerStatus.ELIMINATED
}

/**
 * Un buzz enregistré par l'hôte.
 *
 * @param atHostMillis heure murale du buzz **ramenée sur l'horloge de l'hôte** : c'est la seule
 *   valeur comparable entre joueurs.
 * @param reactionMillis écart entre le top et le buzz.
 * @param precisionMillis incertitude de la synchronisation d'horloge (± ms) pour ce joueur.
 */
@Serializable
data class Buzz(
    val playerId: String,
    val round: Int,
    val atHostMillis: Long,
    val reactionMillis: Long,
    val precisionMillis: Long = 0,
) {
    /** « 21:04:37.482 » — l'heure exacte demandée : heure, minute, seconde, milliseconde. */
    fun wallClockText(): String = formatWallClock(atHostMillis)

    companion object {
        fun formatWallClock(epochMillis: Long): String {
            val zone = java.util.TimeZone.getDefault()
            val local = epochMillis + zone.getOffset(epochMillis)
            val ms = Math.floorMod(local, 1000L)
            val totalSeconds = Math.floorDiv(local, 1000L)
            val s = Math.floorMod(totalSeconds, 60L)
            val m = Math.floorMod(Math.floorDiv(totalSeconds, 60L), 60L)
            val h = Math.floorMod(Math.floorDiv(totalSeconds, 3600L), 24L)
            return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", h, m, s, ms)
        }

        /**
         * « 0,326 s » en français, « 0.326 s » en anglais : le séparateur décimal suit la
         * langue de l'application, pas celle du téléphone.
         */
        fun formatReaction(millis: Long): String = when {
            millis < 0 -> String.format(AppLocale.locale, "%.3f s", 0.0)
            millis < 10_000 -> String.format(AppLocale.locale, "%.3f s", millis / 1000.0)
            else -> String.format(AppLocale.locale, "%d s", TimeUnit.MILLISECONDS.toSeconds(millis))
        }

        /** « +0,014 s » : écart avec le meilleur temps. */
        fun formatGap(millis: Long): String =
            String.format(AppLocale.locale, "%+.3f s", millis / 1000.0)
    }
}

/**
 * État complet et faisant foi du salon. L'hôte le produit, tout le monde le consomme.
 * Il est assez petit pour être diffusé intégralement à chaque changement, ce qui rend
 * impossible toute divergence entre les appareils.
 */
@Serializable
data class RoomState(
    val code: String,
    val hostId: String,
    val round: Int = 0,
    val roundState: RoundState = RoundState.IDLE,
    /** Heure murale (horloge de l'hôte) du top, ou null si la manche n'est pas armée. */
    val armedAtMillis: Long? = null,
    val players: List<Player> = emptyList(),
    val buzzes: List<Buzz> = emptyList(),
    val winnerId: String? = null,
    /** true tant que la fenêtre d'arbitrage photo-finish n'est pas close. */
    val provisional: Boolean = false,
    /**
     * Joueurs à qui l'animateur a passé la main, dans l'ordre du classement : ils ont eu la
     * parole et l'ont rendue.
     */
    val passedIds: List<String> = emptyList(),
    /**
     * Celui que l'animateur vient de déclarer dans l'erreur. Son buzzer passe au rouge et la
     * sanction se fait entendre, mais la main ne bouge pas tant qu'il n'a pas appuyé sur
     * « suivant » : à lui de décider quand la partie repart.
     */
    val wrongId: String? = null,
    /**
     * Celui que l'animateur vient de déclarer dans le vrai. Son buzzer passe au vert et la
     * récompense se fait entendre, chez lui comme chez l'animateur, le temps de savourer :
     * les buzzers s'éteignent juste après, la manche étant jouée.
     */
    val rightId: String? = null,
    val options: RoomOptions = RoomOptions(),
) {
    fun player(id: String): Player? = players.firstOrNull { it.id == id }

    fun buzzOf(id: String): Buzz? = buzzes.firstOrNull { it.playerId == id }

    /** Buzz triés du plus rapide au plus lent. */
    val ranking: List<Buzz> get() = buzzes.sortedBy { it.atHostMillis }

    /**
     * Qui a la parole : le meilleur buzz que l'animateur n'a pas encore écarté. Null quand
     * personne n'a buzzé, ou quand tout le monde s'est trompé — la manche est alors à relancer.
     */
    val speakerId: String? get() = ranking.firstOrNull { it.playerId !in passedIds }?.playerId

    fun rankOf(id: String): Int {
        val index = ranking.indexOfFirst { it.playerId == id }
        return if (index < 0) 0 else index + 1
    }

    /** Écart avec le meilleur temps, ou null si le joueur est en tête / n'a pas buzzé. */
    fun gapOf(id: String): Long? {
        val best = ranking.firstOrNull() ?: return null
        val mine = buzzOf(id) ?: return null
        val gap = mine.atHostMillis - best.atHostMillis
        return if (gap <= 0) null else gap
    }

    val host: Player? get() = player(hostId)

    val activePlayers: List<Player> get() = players.filter { it.status == PlayerStatus.ACTIVE }

    /**
     * Y a-t-il encore quelqu'un pour appuyer ? Une manche lancée alors que tout le salon est
     * éliminé ne mènerait nulle part : le décompte s'égrènerait sur des buzzers éteints, et
     * l'animateur attendrait un buzz qui ne peut plus venir.
     */
    val canArm: Boolean get() = activePlayers.isNotEmpty()

    /**
     * État réellement visible à l'instant [nowHostMillis] : pendant le décompte, chaque appareil
     * bascule tout seul en ARMED à l'heure prévue, sans attendre un message de l'hôte.
     */
    fun effectiveRoundState(nowHostMillis: Long): RoundState =
        if (roundState == RoundState.COUNTDOWN && armedAtMillis != null && nowHostMillis >= armedAtMillis) {
            RoundState.ARMED
        } else {
            roundState
        }

    /** Millisecondes restantes avant le top, ou null si aucun décompte en cours. */
    fun countdownRemaining(nowHostMillis: Long): Long? {
        if (roundState != RoundState.COUNTDOWN) return null
        val armedAt = armedAtMillis ?: return null
        val remaining = armedAt - nowHostMillis
        return if (remaining > 0) remaining else null
    }

    /** Le joueur peut-il appuyer maintenant ? */
    fun canBuzz(id: String, nowHostMillis: Long): Boolean {
        val p = player(id) ?: return false
        if (p.isEliminated) return false
        if (effectiveRoundState(nowHostMillis) != RoundState.ARMED) return false
        return buzzOf(id) == null
    }

    /** Classement au score, pour le tableau des points. */
    val leaderboard: List<Player>
        get() = players.sortedWith(compareByDescending<Player> { it.score }.thenBy { it.name.lowercase(Locale.ROOT) })
}

/**
 * Couleur logique d'un buzzer, indépendante du thème.
 *
 * Cinq couleurs, cinq significations. **Blanc**, la parole : un seul joueur à la fois, une fois
 * la manche tranchée. **Vert**, les buzzers ouverts, quand chacun peut encore appuyer — et la
 * bonne réponse, qui referme la manche sur la même promesse tenue. **Rouge**, la mauvaise
 * réponse : l'animateur vient de la prononcer. **Gris**, le buzzer désactivé. **Bleu**, tout le
 * reste, à commencer par le cas le plus courant : quelqu'un d'autre a la main. L'ambre du
 * décompte reste à part, trois secondes de feu orange avant le go.
 */
enum class BuzzerVisual { OFF, COUNTDOWN, ARMED, BUZZED, SPEAKING, RIGHT, WRONG, LOST, ELIMINATED }

fun RoomState.visualFor(
    playerId: String,
    nowHostMillis: Long,
    localBuzzedRound: Int? = null,
): BuzzerVisual {
    val p = player(playerId) ?: return BuzzerVisual.OFF
    if (p.isEliminated) return BuzzerVisual.ELIMINATED
    val buzzed = buzzOf(playerId) != null || localBuzzedRound == round
    // Quand la parole est tranchée. En duel, il faut attendre la fin de la fenêtre d'arbitrage :
    // deux joueurs peuvent appuyer avant que le verrouillage ne les atteigne, et le classement
    // bouge encore. En course, personne ne verrouille personne — le premier buzz donne déjà la
    // main, les autres continuent de jouer pour leur place au classement.
    val decided = when (options.mode) {
        GameMode.DUEL -> roundState == RoundState.LOCKED && !provisional
        GameMode.COURSE -> buzzes.isNotEmpty()
    }
    return when {
        // Le verdict passe avant tout le reste : celui qui vient d'être jugé a encore la main,
        // mais ce n'est plus ce qu'il faut lui montrer.
        playerId == rightId -> BuzzerVisual.RIGHT
        playerId == wrongId -> BuzzerVisual.WRONG
        decided && speakerId == playerId -> BuzzerVisual.SPEAKING
        // Il a eu la parole et l'a rendue : la manche continue sans lui.
        playerId in passedIds -> BuzzerVisual.LOST
        // Duel : celui qui s'est fait coiffer au poteau s'éteint aussi, la manche est prise.
        decided && buzzed && options.mode == GameMode.DUEL -> BuzzerVisual.LOST
        buzzed -> BuzzerVisual.BUZZED
        effectiveRoundState(nowHostMillis) == RoundState.ARMED -> BuzzerVisual.ARMED
        roundState == RoundState.COUNTDOWN -> BuzzerVisual.COUNTDOWN
        roundState == RoundState.LOCKED -> BuzzerVisual.LOST
        else -> BuzzerVisual.OFF
    }
}
