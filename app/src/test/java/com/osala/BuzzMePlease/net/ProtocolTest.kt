package com.osala.BuzzMePlease.net

import com.osala.BuzzMePlease.game.GameEngine
import com.osala.BuzzMePlease.model.RoomOptions
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le protocole local est du JSON, une ligne par message. Deux propriétés sont vitales :
 * un message ne doit jamais contenir de saut de ligne, et un champ inconnu — venant d'une
 * version plus récente installée sur le téléphone d'en face — ne doit pas faire tomber la liaison.
 */
class ProtocolTest {

    private fun roundTrip(message: NetMessage): NetMessage {
        val line = ProtocolJson.encodeToString(message)
        assertFalse("un message ne doit jamais contenir de saut de ligne", line.contains('\n'))
        return ProtocolJson.decodeFromString<NetMessage>(line)
    }

    @Test
    fun `les messages du protocole survivent a l aller-retour`() {
        val messages: List<NetMessage> = listOf(
            Hello("p1", "Alice"),
            Welcome("ABCDE", "host"),
            Ping(7, 1_700_000_000_000L),
            Pong(7, 1_700_000_000_000L, 1_700_000_005_000L),
            BuzzRequest(round = 4, clientWall = 1_700_000_000_123L, offset = -37, rtt = 6),
            RenameRequest("Chloé"),
            Bye(ByeCause.KICKED, kicked = true),
        )
        messages.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `l etat complet du salon tient dans un message compact`() {
        val engine = GameEngine("ABCDE", "host", RoomOptions())
        repeat(8) { index -> engine.join("p$index", "Joueur $index", index.toLong()) }
        engine.arm(armAtMillis = 1_000_000L, withCountdown = true)
        engine.markArmed(1)
        engine.registerBuzz("p3", 1, 1_000_240L, 4)

        val message = StateSync(engine.snapshot)
        val line = ProtocolJson.encodeToString<NetMessage>(message)

        assertEquals(message, roundTrip(message))
        // La diffusion intégrale de l'état à chaque changement n'est tenable que si elle reste
        // légère : à huit joueurs on veut rester très en dessous du kilo-octet et demi.
        assertTrue("état trop volumineux : ${line.length} octets", line.length < 1_500)
    }

    @Test
    fun `un champ inconnu venant d une version plus recente est ignore`() {
        val line = """{"t":"hello","playerId":"p9","name":"Zoé","protocol":1,"nouveauChamp":42}"""
        val message = ProtocolJson.decodeFromString<NetMessage>(line)
        assertEquals(Hello("p9", "Zoé"), message)
    }
}
