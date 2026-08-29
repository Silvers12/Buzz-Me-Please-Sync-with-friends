package com.osala.BuzzMePlease.core

import android.os.SystemClock

/**
 * Horloge de l'application.
 *
 * Deux bases de temps sont utilisées :
 *  - [wallNow] : l'heure murale (epoch ms) — la seule qui peut être comparée entre appareils
 *    une fois corrigée par l'offset mesuré avec l'hôte.
 *  - [SystemClock.uptimeMillis] : horloge monotone utilisée par le système d'entrée Android.
 *    Les événements tactiles sont horodatés dans cette base ; on la convertit en heure murale
 *    au moment du buzz pour garder la précision réelle de l'appui (et non celle du recompose).
 */
object AppClock {

    fun wallNow(): Long = System.currentTimeMillis()

    /**
     * Convertit l'horodatage d'un événement d'entrée (base uptime) en heure murale.
     * L'offset est recalculé à chaque appel : sur la durée d'une manche il est stable au
     * millimètre près, et cela évite toute dérive liée à un ajustement d'horloge.
     */
    fun wallFromUptime(uptimeMillis: Long): Long =
        System.currentTimeMillis() - SystemClock.uptimeMillis() + uptimeMillis

    fun elapsedNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
