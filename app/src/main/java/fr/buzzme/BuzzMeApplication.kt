package fr.buzzme

import android.app.Application

/**
 * Point d'entrée du processus.
 *
 * Aucune initialisation Firebase ici : le mode en ligne n'est branché qu'à la demande, à partir
 * des réglages saisis par l'utilisateur. L'application démarre donc sans dépendre du réseau.
 */
class BuzzMeApplication : Application()
