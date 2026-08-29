package com.osala.BuzzMePlease.game

import com.osala.BuzzMePlease.model.Buzz
import com.osala.BuzzMePlease.model.GameMode
import com.osala.BuzzMePlease.model.Player
import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
import com.osala.BuzzMePlease.model.RoundState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Moteur de jeu **faisant autorité**. Une seule instance existe dans le salon : celle de l'hôte.
 * Tous les autres appareils affichent l'état qu'elle publie.
 *
 * Toutes les méthodes sont synchronisées : elles sont appelées depuis les threads réseau
 * (réception d'un buzz) et depuis l'UI (commandes de l'hôte).
 */
class GameEngine(code: String, hostId: String, options: RoomOptions = RoomOptions()) {

    private val lock = Any()
    private val _state = MutableStateFlow(RoomState(code = code, hostId = hostId, options = options))
    val state: StateFlow<RoomState> = _state.asStateFlow()

    val snapshot: RoomState get() = _state.value

    /** Remplace intégralement l'état (reprise du salon après une passation de rôle). */
    fun restore(state: RoomState, newHostId: String) = synchronized(lock) {
        _state.value = state.copy(hostId = newHostId)
    }

    // ---------------------------------------------------------------- joueurs

    /** Ajoute le joueur ou met à jour sa fiche s'il revient après une coupure. */
    fun join(id: String, name: String, joinedAt: Long): Player = synchronized(lock) {
        val current = _state.value
        val existing = current.player(id)
        val player = existing?.copy(name = name, connected = true)
            ?: Player(id = id, name = name, joinedAt = joinedAt)
        _state.value = current.copy(players = current.players.upsert(player))
        player
    }

    fun setConnected(id: String, connected: Boolean) = mutate { current ->
        val p = current.player(id) ?: return@mutate current
        current.copy(players = current.players.upsert(p.copy(connected = connected)))
    }

    fun setPing(id: String, pingMillis: Long) = mutate { current ->
        val p = current.player(id) ?: return@mutate current
        if (p.pingMillis == pingMillis) current
        else current.copy(players = current.players.upsert(p.copy(pingMillis = pingMillis)))
    }

    fun rename(id: String, name: String) = mutate { current ->
        val p = current.player(id) ?: return@mutate current
        val clean = name.trim().take(MAX_NAME).ifBlank { p.name }
        current.copy(players = current.players.upsert(p.copy(name = clean)))
    }

    fun setStatus(id: String, status: PlayerStatus) = mutate { current ->
        val p = current.player(id) ?: return@mutate current
        var next = current.copy(players = current.players.upsert(p.copy(status = status)))
        if (status == PlayerStatus.ELIMINATED) {
            // Un joueur éliminé ne peut pas rester détenteur du buzz en cours.
            next = next.copy(
                buzzes = next.buzzes.filterNot { it.playerId == id },
                passedIds = next.passedIds.filterNot { it == id },
                wrongId = next.wrongId?.takeIf { it != id },
            )
            if (next.winnerId == id) next = next.recomputeWinner()
        }
        next
    }

    fun remove(id: String) = mutate { current ->
        if (id == current.hostId) return@mutate current // l'hôte ne peut pas s'exclure lui-même
        current.copy(
            players = current.players.filterNot { it.id == id },
            buzzes = current.buzzes.filterNot { it.playerId == id },
            passedIds = current.passedIds.filterNot { it == id },
            wrongId = current.wrongId?.takeIf { it != id },
        ).let { if (it.winnerId == id) it.recomputeWinner() else it }
    }

    fun addPoints(id: String, delta: Int) = mutate { current ->
        val p = current.player(id) ?: return@mutate current
        current.copy(players = current.players.upsert(p.copy(score = p.score + delta)))
    }

    fun resetScores() = mutate { current ->
        current.copy(players = current.players.map { it.copy(score = 0) })
    }

    fun transferHost(newHostId: String) = mutate { current ->
        if (current.player(newHostId) == null) current else current.copy(hostId = newHostId)
    }

    fun setOptions(options: RoomOptions) = mutate { it.copy(options = options) }

    // ----------------------------------------------------------------- manche

    /**
     * Le top : ouvre une nouvelle manche.
     *
     * @param armAtMillis instant exact (horloge de l'hôte) où les buzzers passent au vert.
     *   Le placer dans le futur permet le décompte « 3 · 2 · 1 · TOP ! » : tous les appareils
     *   connaissent l'échéance et s'arment d'eux-mêmes à la milliseconde près.
     */
    fun arm(armAtMillis: Long, withCountdown: Boolean) = mutate { current ->
        current.copy(
            round = current.round + 1,
            roundState = if (withCountdown) RoundState.COUNTDOWN else RoundState.ARMED,
            armedAtMillis = armAtMillis,
            buzzes = emptyList(),
            winnerId = null,
            provisional = false,
            passedIds = emptyList(),
            wrongId = null,
        )
    }

    /** Fin du décompte : l'état officiel rattrape ce que les appareils affichent déjà. */
    fun markArmed(round: Int) = mutate { current ->
        if (current.round == round && current.roundState == RoundState.COUNTDOWN) {
            current.copy(roundState = RoundState.ARMED)
        } else {
            current
        }
    }

    /** Réinitialise : buzzers éteints, résultats effacés, scores conservés. */
    fun reset() = mutate { current ->
        current.copy(
            roundState = RoundState.IDLE,
            armedAtMillis = null,
            buzzes = emptyList(),
            winnerId = null,
            provisional = false,
            passedIds = emptyList(),
            wrongId = null,
        )
    }

    /**
     * Enregistre un buzz.
     *
     * @param atHostMillis heure du buzz déjà ramenée sur l'horloge de l'hôte.
     * @return [BuzzOutcome.FIRST] pour le premier buzz de la manche — c'est lui qui déclenche
     *   le verrouillage des autres buzzers et la fenêtre d'arbitrage.
     */
    fun registerBuzz(playerId: String, round: Int, atHostMillis: Long, precisionMillis: Long): BuzzOutcome {
        synchronized(lock) {
            val current = _state.value
            val player = current.player(playerId) ?: return BuzzOutcome.REJECTED
            if (player.isEliminated) return BuzzOutcome.REJECTED
            if (round != current.round) return BuzzOutcome.REJECTED
            val armedAt = current.armedAtMillis ?: return BuzzOutcome.REJECTED
            if (current.buzzOf(playerId) != null) return BuzzOutcome.REJECTED

            // Un buzz parti pile à la fin du décompte peut arriver avant que l'hôte n'ait
            // basculé son propre état : on l'accepte s'il est postérieur au top.
            val open = when (current.roundState) {
                RoundState.ARMED -> true
                RoundState.COUNTDOWN -> atHostMillis >= armedAt
                RoundState.LOCKED -> current.provisional && current.options.mode == GameMode.DUEL
                RoundState.IDLE -> false
            }
            if (!open) return BuzzOutcome.REJECTED

            val duel = current.options.mode == GameMode.DUEL

            // Une horloge client légèrement en avance ne doit jamais produire un temps négatif.
            val at = maxOf(atHostMillis, armedAt)
            val buzz = Buzz(
                playerId = playerId,
                round = round,
                atHostMillis = at,
                reactionMillis = at - armedAt,
                precisionMillis = precisionMillis,
            )
            val first = current.buzzes.isEmpty()
            val withBuzz = current.copy(buzzes = current.buzzes + buzz)
            val next = if (duel) {
                withBuzz.copy(roundState = RoundState.LOCKED, provisional = true).recomputeWinner()
            } else {
                withBuzz.copy(roundState = RoundState.ARMED).recomputeWinner()
            }
            _state.value = next
            return if (first) BuzzOutcome.FIRST else BuzzOutcome.ACCEPTED
        }
    }

    /**
     * Clôture la fenêtre d'arbitrage : le vainqueur affiché devient définitif.
     * Les buzz arrivés pendant cette fenêtre — partis avant que le verrouillage ne les atteigne —
     * ont été pris en compte, et c'est bien le meilleur temps qui gagne, pas le premier paquet reçu.
     */
    fun closeAdjudication(round: Int) = mutate { current ->
        if (current.round != round || !current.provisional) current
        else current.copy(provisional = false).recomputeWinner()
    }

    /**
     * Mauvaise réponse : l'animateur retire la parole à celui qui l'a, et la main descend au
     * buzz suivant. Quand le dernier est écarté, plus personne n'a la main et la manche est à
     * relancer. Sans effet tant que l'arbitrage n'est pas clos — le classement peut encore bouger.
     */
    fun passSpeaker(round: Int) = mutate { current ->
        if (current.round != round || current.provisional) return@mutate current
        val speaker = current.speakerId ?: return@mutate current
        current.copy(passedIds = current.passedIds + speaker, wrongId = null)
    }

    /**
     * Mauvaise réponse : le buzzer de celui qui a la parole passe au rouge et la sanction se
     * fait entendre, chez lui comme chez l'animateur. La main ne bouge pas pour autant — c'est
     * « suivant » qui la déplace, quand l'animateur le décide.
     */
    fun markWrong(round: Int) = mutate { current ->
        if (current.round != round || current.provisional) return@mutate current
        val speaker = current.speakerId ?: return@mutate current
        if (current.wrongId == speaker) current else current.copy(wrongId = speaker)
    }

    // ------------------------------------------------------------------ utils

    private fun mutate(block: (RoomState) -> RoomState) = synchronized(lock) {
        val next = block(_state.value)
        if (next != _state.value) _state.value = next
    }

    private fun RoomState.recomputeWinner(): RoomState =
        copy(winnerId = buzzes.minByOrNull { it.atHostMillis }?.playerId)

    private fun List<Player>.upsert(player: Player): List<Player> {
        val index = indexOfFirst { it.id == player.id }
        return if (index < 0) this + player else toMutableList().also { it[index] = player }
    }

    companion object {
        const val MAX_NAME = 18

        /**
         * Fenêtre d'arbitrage « photo-finish ». Le verrouillage est diffusé immédiatement pour
         * l'effet visuel, mais l'hôte continue d'accepter les buzz partis avant qu'il ne les
         * atteigne. Doit couvrir un aller-retour Wi-Fi confortable.
         */
        const val ADJUDICATION_MILLIS = 350L
    }
}

enum class BuzzOutcome { FIRST, ACCEPTED, REJECTED }
