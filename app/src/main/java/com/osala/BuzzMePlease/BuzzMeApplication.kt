package com.osala.BuzzMePlease

import android.app.Application
import android.os.Build
import com.osala.BuzzMePlease.core.CrashReporter
import com.osala.BuzzMePlease.core.runReported

/**
 * Point d'entrée du processus.
 *
 * Le jeu ne dépend toujours d'aucun service distant pour fonctionner — tout se
 * joue entre les téléphones du salon. La seule chose posée ici est le contexte de
 * diagnostic joint aux rapports d'erreur, et il l'est le plus tôt possible pour
 * qu'il accompagne y compris un crash survenant pendant l'ouverture d'un écran.
 *
 * Crashlytics lui-même n'a rien à initialiser : son gestionnaire d'exceptions non
 * interceptées est installé par son ContentProvider d'initialisation, donc AVANT
 * `onCreate`. Aucun crash de démarrage n'échappe à la collecte.
 */
class BuzzMeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        recordBuildContext()
    }

    /**
     * Clés stables présentes sur tous les rapports.
     *
     * `version_code` compte doublement ici : c'est le contrat du protocole. Deux
     * versions différentes autour d'une même table jouent à deux jeux de règles,
     * et un rapport sans elle est difficile à interpréter.
     */
    private fun recordBuildContext() {
        runReported("Application.recordBuildContext") {
            CrashReporter.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            CrashReporter.setCustomKey("version_name", BuildConfig.VERSION_NAME)
            CrashReporter.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            CrashReporter.setCustomKey("android_sdk", Build.VERSION.SDK_INT)
            CrashReporter.setCustomKey("device_manufacturer", Build.MANUFACTURER)
            CrashReporter.setCustomKey("device_model", Build.MODEL)
        }
    }
}
