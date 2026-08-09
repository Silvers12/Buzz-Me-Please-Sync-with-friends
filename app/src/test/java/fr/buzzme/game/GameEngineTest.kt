package fr.buzzme.game

import fr.buzzme.model.BuzzerVisual
import fr.buzzme.model.GameMode
import fr.buzzme.model.PlayerStatus
import fr.buzzme.model.RoomOptions
import fr.buzzme.model.RoundState
import fr.buzzme.model.visualFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le moteur est la seule autorité du salon : c'est ici que se joue l'équité du buzz.
 * Ces tests figent les règles qui comptent, à commencer par le photo-finish.
 */
class GameEngineTest {

    private lateinit var engine: GameEngine
    private val armedAt = 1_003_000L

    @Before
    fun setUp() {
        engine = GameEngine("ABCDE", "host", RoomOptions(mode = GameMode.DUEL))
        engine.join("host", "Animateur", 0)
        engine.join("p1", "Alice", 1)
        engine.join("p2", "Bruno", 2)
        engine.join("p3", "Chloé", 3)
    }

    private fun armWithCountdown() = engine.arm(armAtMillis = armedAt, withCountdown = true)

    @Test
    fun `le decompte arme tous les appareils au meme instant`() {
        armWithCountdown()
        val state = engine.snapshot
        assertEquals(1, state.round)
        assertEquals(RoundState.COUNTDOWN, state.roundState)
        assertEquals(3000L, state.countdownRemaining(armedAt - 3000))
        assertEquals(RoundState.COUNTDOWN, state.effectiveRoundState(armedAt - 1))
        assertEquals(RoundState.ARMED, state.effectiveRoundState(armedAt))
    }

    @Test
    fun `un faux depart est refuse`() {
        armWithCountdown()
        assertFalse(engine.snapshot.canBuzz("p1", armedAt - 500))
        assertEquals(BuzzOutcome.REJECTED, engine.registerBuzz("p1", 1, armedAt - 50, 0))
        assertTrue(engine.snapshot.buzzes.isEmpty())
    }

    @Test
    fun `le premier buzz verrouille les autres buzzers`() {
        armWithCountdown()
        engine.markArmed(1)
        assertEquals(BuzzOutcome.FIRST, engine.registerBuzz("p1", 1, armedAt + 320, 3))

        val state = engine.snapshot
        assertEquals(RoundState.LOCKED, state.roundState)
        assertEquals("p1", state.winnerId)
        assertTrue(state.provisional)
        assertFalse(state.canBuzz("p3", armedAt + 330))
        assertEquals(BuzzerVisual.BUZZED, state.visualFor("p1", armedAt + 330))
        assertEquals(BuzzerVisual.LOST, state.visualFor("p3", armedAt + 330))
    }

    /**
     * Le cas qui justifie toute la mécanique : Bruno appuie 8 ms avant Alice, mais son message
     * arrive après. C'est l'instant de l'appui qui doit gagner, pas l'ordre d'arrivée.
     */
    @Test
    fun `un buzz plus rapide arrive en retard et reprend la tete`() {
        armWithCountdown()
        engine.markArmed(1)
        engine.registerBuzz("p1", 1, armedAt + 320, 3)

        assertEquals(BuzzOutcome.ACCEPTED, engine.registerBuzz("p2", 1, armedAt + 312, 4))

        val state = engine.snapshot
        assertEquals("p2", state.winnerId)
        assertEquals(listOf("p2", "p1"), state.ranking.map { it.playerId })
        assertEquals(8L, state.gapOf("p1"))
        assertNull(state.gapOf("p2"))
        assertEquals(312L, state.buzzOf("p2")?.reactionMillis)
    }

    @Test
    fun `une fois l arbitrage clos plus aucun buzz n est accepte`() {
        armWithCountdown()
        engine.markArmed(1)
        engine.registerBuzz("p1", 1, armedAt + 320, 3)
        engine.closeAdjudication(1)

        assertFalse(engine.snapshot.provisional)
        assertEquals(BuzzOutcome.REJECTED, engine.registerBuzz("p3", 1, armedAt + 400, 2))
    }

    @Test
    fun `on ne buzze pas deux fois ni pour une manche perimee`() {
        armWithCountdown()
        engine.markArmed(1)
        engine.registerBuzz("p1", 1, armedAt + 320, 0)
        assertEquals(BuzzOutcome.REJECTED, engine.registerBuzz("p1", 1, armedAt + 350, 0))
        assertEquals(BuzzOutcome.REJECTED, engine.registerBuzz("p2", 0, armedAt + 330, 0))
    }

    @Test
    fun `une horloge client en avance ne produit pas de temps negatif`() {
        engine.arm(armAtMillis = armedAt, withCountdown = false)
        engine.registerBuzz("p1", 1, armedAt - 25, 0)
        assertEquals(0L, engine.snapshot.buzzOf("p1")?.reactionMillis)
    }

    @Test
    fun `un joueur elimine a un buzzer noir et ne peut plus buzzer`() {
        engine.setStatus("p3", PlayerStatus.ELIMINATED)
        engine.arm(armAtMillis = armedAt, withCountdown = false)

        assertEquals(BuzzerVisual.ELIMINATED, engine.snapshot.visualFor("p3", armedAt + 10))
        assertFalse(engine.snapshot.canBuzz("p3", armedAt + 10))
        assertEquals(BuzzOutcome.REJECTED, engine.registerBuzz("p3", 1, armedAt + 100, 0))

        engine.setStatus("p3", PlayerStatus.ACTIVE)
        assertTrue(engine.snapshot.canBuzz("p3", armedAt + 200))
    }

    @Test
    fun `eliminer le vainqueur redonne la main au suivant`() {
        engine.arm(armAtMillis = armedAt, withCountdown = false)
        engine.registerBuzz("p1", 1, armedAt + 100, 0)
        engine.registerBuzz("p2", 1, armedAt + 140, 0)

        engine.setStatus("p1", PlayerStatus.ELIMINATED)

        assertEquals("p2", engine.snapshot.winnerId)
        assertNull(engine.snapshot.buzzOf("p1"))
    }

    @Test
    fun `le mode course classe tout le monde sans verrouiller`() {
        val race = GameEngine("ABCDE", "host", RoomOptions(mode = GameMode.COURSE))
        race.join("a", "A", 0)
        race.join("b", "B", 1)
        race.join("c", "C", 2)
        race.arm(armAtMillis = 500, withCountdown = false)

        race.registerBuzz("a", 1, 620, 0)
        race.registerBuzz("b", 1, 560, 0)
        race.registerBuzz("c", 1, 700, 0)

        assertEquals(RoundState.ARMED, race.snapshot.roundState)
        assertEquals(listOf("b", "a", "c"), race.snapshot.ranking.map { it.playerId })
    }

    @Test
    fun `relancer efface les resultats mais garde les scores`() {
        engine.arm(armAtMillis = armedAt, withCountdown = false)
        engine.registerBuzz("p1", 1, armedAt + 100, 0)
        engine.addPoints("p1", 3)

        engine.reset()

        val state = engine.snapshot
        assertEquals(RoundState.IDLE, state.roundState)
        assertTrue(state.buzzes.isEmpty())
        assertNull(state.winnerId)
        assertNull(state.armedAtMillis)
        assertEquals(3, state.player("p1")?.score)
    }

    @Test
    fun `les points se cumulent et le classement suit`() {
        engine.addPoints("p1", 3)
        engine.addPoints("p1", -1)
        engine.addPoints("p2", 1)

        assertEquals(2, engine.snapshot.player("p1")?.score)
        assertEquals("p1", engine.snapshot.leaderboard.first().id)

        engine.resetScores()
        assertTrue(engine.snapshot.players.all { it.score == 0 })
    }

    @Test
    fun `la passation d animation conserve joueurs et scores`() {
        engine.addPoints("p1", 5)
        engine.transferHost("p1")

        assertEquals("p1", engine.snapshot.hostId)
        assertEquals(5, engine.snapshot.player("p1")?.score)
        assertEquals(4, engine.snapshot.players.size)
    }

    @Test
    fun `l animateur ne peut pas s exclure lui meme`() {
        engine.remove("host")
        assertEquals(4, engine.snapshot.players.size)

        engine.remove("p2")
        assertNull(engine.snapshot.player("p2"))
    }

    @Test
    fun `revenir apres une coupure conserve score et statut`() {
        engine.addPoints("p1", 4)
        engine.setStatus("p1", PlayerStatus.ELIMINATED)
        engine.setConnected("p1", false)

        engine.join("p1", "Alice", 99)

        val player = engine.snapshot.player("p1")
        assertEquals(4, player?.score)
        assertEquals(PlayerStatus.ELIMINATED, player?.status)
        assertTrue(player?.connected == true)
        assertEquals(4, engine.snapshot.players.size)
    }
}
