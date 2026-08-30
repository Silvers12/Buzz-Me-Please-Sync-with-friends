package com.osala.BuzzMePlease.core

import android.os.SystemClock

/**
 * Horloge de l'application.
 *
 * Trois bases de temps, et le choix entre elles décide de l'équité du jeu :
 *  - [wallNow] : l'heure murale (epoch ms). Elle sert de référence **chez l'hôte seulement** —
 *    c'est elle qui donne l'heure du buzz affichée au pupitre. Réglable par l'utilisateur.
 *  - [elapsedNow] : temps écoulé depuis le démarrage de l'appareil, monotone. Ni un réglage
 *    d'heure ni un changement de fuseau ne le déplacent. C'est la base sur laquelle **le joueur**
 *    se synchronise avec l'hôte : reculer l'horloge de son téléphone n'y change rien.
 *  - [SystemClock.uptimeMillis] : la base des événements tactiles Android. On la convertit au
 *    moment du buzz pour garder la précision réelle de l'appui, et non celle du recompose.
 */
object AppClock {

    fun wallNow(): Long = System.currentTimeMillis()

    fun elapsedNow(): Long = SystemClock.elapsedRealtime()

    /**
     * L'horodatage d'un événement d'entrée ramené sur l'heure murale. Réservé à l'hôte, dont
     * l'horloge fait foi dans le salon.
     */
    fun wallFromUptime(uptimeMillis: Long): Long =
        System.currentTimeMillis() - SystemClock.uptimeMillis() + uptimeMillis

    /**
     * L'horodatage d'un événement d'entrée ramené sur la base monotone. Les deux horloges
     * avancent du même pas tant que l'appareil est éveillé — écran allumé, partie en cours —
     * et l'écart est relevé à l'instant du buzz.
     */
    fun elapsedFromUptime(uptimeMillis: Long): Long =
        SystemClock.elapsedRealtime() - SystemClock.uptimeMillis() + uptimeMillis

    fun elapsedNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
