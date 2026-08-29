package fr.buzzme.core

/**
 * Interrupteurs de fonctionnalités.
 *
 * Le salon en ligne (Firebase) reste entièrement dans le dépôt, mais il n'est pas proposé tant
 * qu'il n'a pas été éprouvé sur le terrain : une seule constante à repasser à `true` le remet
 * partout — choix du transport sur l'accueil, panneau de configuration des réglages, tutoriel.
 */
object Features {
    /** Salons hébergés sur Firebase, pour jouer à distance. Désactivé pour le moment. */
    const val ONLINE_ROOMS = false
}
