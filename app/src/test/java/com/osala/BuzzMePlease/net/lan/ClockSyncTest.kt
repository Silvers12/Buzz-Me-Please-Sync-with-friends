package com.osala.BuzzMePlease.net.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Sans horloge commune, comparer deux buzz n'a aucun sens. Ces tests vérifient que l'estimation
 * reste juste même quand le Wi-Fi ralentit certaines sondes.
 */
class ClockSyncTest {

    @Test
    fun `estime le decalage a partir d un aller-retour symetrique`() {
        val sync = ClockSync()
        // L'hôte est 5 000 ms en avance ; aller-retour de 80 ms.
        sync.record(clientSent = 1000, hostWall = 6040, clientReceived = 1080)

        assertTrue(sync.synced)
        assertEquals(5000L, sync.offsetMillis)
        assertEquals(40L, sync.precisionMillis)
    }

    @Test
    fun `retient l echantillon le moins perturbe`() {
        val sync = ClockSync()
        sync.record(clientSent = 1000, hostWall = 6040, clientReceived = 1080) // 80 ms
        sync.record(clientSent = 2000, hostWall = 7003, clientReceived = 2006) // 6 ms
        sync.record(clientSent = 3000, hostWall = 8150, clientReceived = 3300) // 300 ms, asymétrique

        assertEquals(6L, sync.rttMillis)
        assertEquals(3L, sync.precisionMillis)
        assertTrue("décalage retenu : ${sync.offsetMillis}", abs(sync.offsetMillis - 5000) <= 2)
        // La dernière mesure sert d'indicateur de qualité de liaison, pas de référence.
        assertEquals(300L, sync.lastRttMillis)
    }

    @Test
    fun `ignore les sondes aberrantes`() {
        val sync = ClockSync()
        sync.record(clientSent = 5000, hostWall = 1000, clientReceived = 4000) // arrivée avant départ
        assertFalse(sync.synced)

        sync.record(clientSent = 1000, hostWall = 1000, clientReceived = 60_000) // 59 s
        assertFalse(sync.synced)
    }

    @Test
    fun `la conversion applique le decalage retenu`() {
        val sync = ClockSync()
        sync.record(clientSent = 2000, hostWall = 7003, clientReceived = 2006)
        assertEquals(4000 + sync.offsetMillis, sync.toHostTime(4000))

        sync.reset()
        assertFalse(sync.synced)
        assertEquals(4000L, sync.toHostTime(4000))
    }
}
