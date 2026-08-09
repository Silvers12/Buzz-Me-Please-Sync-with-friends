package fr.buzzme.net.lan

/**
 * Synchronisation d'horloge façon NTP, entre un joueur et l'hôte.
 *
 * Le client envoie `t0`, l'hôte répond avec son heure `tH`, le client note l'arrivée `t2`.
 * L'aller-retour vaut `t2 - t0` et, en supposant le trajet symétrique :
 *
 *     offset = tH + (t2 - t0) / 2 - t2
 *
 * On conserve plusieurs échantillons et on retient **celui dont l'aller-retour est le plus court** :
 * c'est celui qui a le moins souffert des files d'attente Wi-Fi, donc le plus fiable.
 * Sur un réseau local l'erreur résiduelle tombe à quelques millisecondes, ce qui suffit à
 * départager deux réflexes très proches.
 */
class ClockSync {

    private data class Sample(val rtt: Long, val offset: Long)

    private val samples = ArrayDeque<Sample>()

    @Volatile
    var offsetMillis: Long = 0
        private set

    /** Meilleur aller-retour observé récemment. */
    @Volatile
    var rttMillis: Long = 0
        private set

    @Volatile
    var lastRttMillis: Long = 0
        private set

    /** Incertitude à annoncer à l'utilisateur : la moitié de l'aller-retour. */
    val precisionMillis: Long get() = rttMillis / 2

    @Volatile
    var synced: Boolean = false
        private set

    @Synchronized
    fun record(clientSent: Long, hostWall: Long, clientReceived: Long) {
        val rtt = clientReceived - clientSent
        if (rtt < 0 || rtt > MAX_PLAUSIBLE_RTT) return
        val offset = hostWall + rtt / 2 - clientReceived
        samples.addLast(Sample(rtt, offset))
        while (samples.size > WINDOW) samples.removeFirst()
        val best = samples.minByOrNull { it.rtt } ?: return
        offsetMillis = best.offset
        rttMillis = best.rtt
        lastRttMillis = rtt
        synced = true
    }

    @Synchronized
    fun reset() {
        samples.clear()
        offsetMillis = 0
        rttMillis = 0
        lastRttMillis = 0
        synced = false
    }

    /** Convertit une heure locale en heure de l'hôte. */
    fun toHostTime(localWallMillis: Long): Long = localWallMillis + offsetMillis

    private companion object {
        const val WINDOW = 16
        const val MAX_PLAUSIBLE_RTT = 3_000L
    }
}
