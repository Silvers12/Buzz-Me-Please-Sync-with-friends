package com.osala.BuzzMePlease.core

import android.net.Uri
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remontée des erreurs vers Firebase Crashlytics.
 *
 * Un `object` plutôt qu'une dépendance injectée : le projet n'a pas de conteneur
 * d'injection, et la couche réseau est instanciée à la main depuis le ViewModel.
 * Cela suit l'idiome de ce paquet, où [Codes] et [SoundLibrary] sont déjà des
 * objets sans état visible.
 *
 * **Toutes les méthodes sont sans effet plutôt que fautives quand Firebase est
 * absent.** C'est indispensable : `GameEngine`, `Protocol` et `ClockSync` ont des
 * tests unitaires qui tournent sur une JVM nue, où `FirebaseCrashlytics.getInstance()`
 * lève. Un appel depuis du code testé ne doit jamais faire échouer un test.
 *
 * ## Ce qui ne doit JAMAIS sortir d'ici
 *
 * Ce jeu manipule trois données à ne pas transmettre :
 * - le **nom des joueurs**, saisi par eux et diffusé sur le réseau ;
 * - le **code du salon**, qui est le secret partagé permettant de le rejoindre ;
 * - les **adresses IP** des pairs, et les URI `content://` des sons importés,
 *   qui portent le nom de fichier choisi par l'utilisateur.
 *
 * Décrire un état par des booléens, des compteurs et des enums ; pour une URI,
 * passer par [redactUri].
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    /** Dernière opération tentée — présente sur tout rapport ultérieur. */
    private const val KEY_LAST_OPERATION = "last_operation"

    /** Écran affiché, renseigné par [setCurrentScreen]. */
    private const val KEY_SCREEN = "screen"

    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    /**
     * Anomalies déjà signalées durant ce processus, par clé.
     *
     * La couche réseau vit dans des boucles qui réessaient : le balayage mDNS se
     * relance toutes les six secondes, la liaison d'un pair se rétablit sans
     * cesse. Une panne durable produirait donc des centaines de rapports
     * identiques, rendant la console inutilisable au moment où elle compte.
     * [recordOnce] borne chaque cause à un rapport par lancement.
     */
    private val alreadyReported = ConcurrentHashMap.newKeySet<String>()

    /**
     * Exceptions qui signalent une annulation, jamais un défaut. Une coroutine
     * annulée parce que le joueur quitte le salon est le fonctionnement normal.
     */
    private val nonReportable = setOf(
        CancellationException::class.java.name,
        "kotlinx.coroutines.JobCancellationException",
        "kotlinx.coroutines.TimeoutCancellationException",
        InterruptedException::class.java.name,
    )

    /**
     * Remonte une exception non fatale.
     *
     * @param context identifiant de l'opération, au format `Classe.methode`.
     *   Également posé en clé `last_operation`, ce qui permet de savoir ce que
     *   faisait l'application juste avant un crash fatal ultérieur.
     */
    fun record(throwable: Throwable, context: String? = null) {
        if (!shouldReport(throwable)) {
            Log.d(TAG, "annulation ignorée : ${throwable::class.java.simpleName}")
            return
        }
        try {
            if (context != null) {
                crashlytics.setCustomKey(KEY_LAST_OPERATION, context)
                crashlytics.log("Context: $context")
            }
            crashlytics.recordException(throwable)
            Log.e(TAG, "non-fatale remontée : ${throwable.message}", throwable)
        } catch (e: Exception) {
            // Firebase indisponible (google-services.json absent, JVM de test) :
            // la télémétrie ne doit jamais faire tomber l'appelant.
            Log.e(TAG, "remontée impossible", e)
        }
    }

    /**
     * Comme [record], mais une seule fois par [key] et par lancement.
     *
     * À utiliser dans tout ce qui réessaie en boucle : échec d'annonce du salon,
     * balayage mDNS refusé, liaison qui se rétablit. Le premier incident porte
     * l'information ; les neuf cents suivants ne font que masquer le reste.
     */
    fun recordOnce(key: String, throwable: Throwable, context: String? = null) {
        if (!alreadyReported.add(key)) {
            Log.d(TAG, "non-fatale déjà remontée pour « $key », ignorée")
            return
        }
        record(throwable, context)
    }

    /**
     * Signale une anomalie applicative qui ne lève aucune exception d'elle-même,
     * une seule fois par [key].
     *
     * Les échecs de cette application arrivent surtout sous forme de **codes
     * d'erreur** : `NsdManager` répond `onRegistrationFailed(errorCode)`,
     * `MediaPlayer` `onError(what, extra)`. Il n'y a rien à attraper, et pourtant
     * le salon n'est pas annonçable ou le son ne sort pas.
     */
    fun recordAnomalyOnce(key: String, message: String, context: String? = null) {
        recordOnce(key, AppAnomalyException(message), context)
    }

    /** Ajoute une ligne au fil d'Ariane joint au prochain rapport. */
    fun log(message: String) {
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Log.d(TAG, "log impossible : $message")
        }
    }

    fun setCustomKey(key: String, value: String) = guarded { crashlytics.setCustomKey(key, value) }

    fun setCustomKey(key: String, value: Boolean) = guarded { crashlytics.setCustomKey(key, value) }

    fun setCustomKey(key: String, value: Int) = guarded { crashlytics.setCustomKey(key, value) }

    fun setCustomKey(key: String, value: Long) = guarded { crashlytics.setCustomKey(key, value) }

    /**
     * Déclare l'écran affiché. C'est la première information regardée pour
     * reproduire un incident : `Join`, `Room`, `Settings`…
     */
    fun setCurrentScreen(screen: String) {
        setCustomKey(KEY_SCREEN, screen)
        log("Screen: $screen")
    }

    /**
     * Active ou coupe la collecte. Le choix est persisté par le SDK et prend le
     * pas sur la meta-data du manifeste. Utilisé par la section Diagnostic des
     * réglages, en debug uniquement.
     */
    fun setCollectionEnabled(enabled: Boolean) = guarded {
        crashlytics.isCrashlyticsCollectionEnabled = enabled
        Log.i(TAG, "collecte Crashlytics = $enabled")
    }

    fun isCollectionEnabled(): Boolean =
        runCatching { crashlytics.isCrashlyticsCollectionEnabled }.getOrDefault(false)

    /**
     * Force l'envoi des rapports en attente. Crashlytics transmet normalement un
     * crash fatal au lancement suivant : utile pour ne pas attendre en phase de
     * validation.
     */
    fun sendUnsentReports() = guarded { crashlytics.sendUnsentReports() }

    /** Vrai si l'exécution précédente s'est terminée par un crash. */
    fun didCrashOnPreviousExecution(): Boolean =
        runCatching { crashlytics.didCrashOnPreviousExecution() }.getOrDefault(false)

    private fun shouldReport(throwable: Throwable): Boolean =
        throwable::class.java.name !in nonReportable

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.d(TAG, "appel Crashlytics ignoré : ${e.message}")
        }
    }
}

/**
 * Anomalie applicative détectée par le code, sans exception d'origine.
 *
 * Son nom est conservé par ProGuard : Crashlytics regroupe les non-fatales par
 * type d'exception, une classe obfusquée serait illisible dans la console.
 */
class AppAnomalyException(message: String) : Exception(message)

/**
 * Exécute [block] en remontant toute exception au lieu de laisser le processus
 * tomber, puis poursuit.
 *
 * À réserver aux étapes dont l'échec est acceptable. Pour une étape dont l'échec
 * invalide la suite, laisser l'exception remonter.
 */
inline fun runReported(operation: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        CrashReporter.record(e, operation)
    }
}

/**
 * Handler à installer sur les [kotlinx.coroutines.CoroutineScope] maison.
 *
 * `LanRoomSession` possède son propre périmètre (`SupervisorJob() + Dispatchers.Default`)
 * pour que la fermeture du salon aboutisse même écran détruit. Sans handler, une
 * exception levée hors des `try` internes de ce périmètre atteint le handler par
 * défaut du thread, donc un crash fatal — en pleine partie, chez tous les joueurs
 * connectés à cet hôte.
 */
fun crashReportingHandler(operation: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        CrashReporter.record(throwable, operation)
    }

/**
 * Réduit une URI à ce qui est diagnosticable sans exposer son contenu.
 *
 * `content://media/external/audio/media/1234` devient `content://media/...` : on
 * garde le schéma et l'autorité, qui suffisent à savoir *quel type* de source a
 * échoué, et on jette le chemin, qui porte le nom du fichier choisi par le joueur.
 * Un chemin d'asset livré avec le jeu ne contient rien de personnel et passe tel
 * quel — c'est justement l'information utile quand un son du jeu est illisible.
 */
fun redactUri(source: String?): String = when {
    source == null -> "null"
    source.isEmpty() -> "<vide>"
    source.startsWith("content://") || source.startsWith("file://") -> {
        val parsed = runCatching { Uri.parse(source) }.getOrNull()
        val scheme = parsed?.scheme ?: "?"
        val authority = parsed?.authority
        if (authority.isNullOrEmpty()) "$scheme://<importé>" else "$scheme://$authority/<importé>"
    }
    // Asset du jeu : « sounds/buzz.mp3 », aucune donnée utilisateur.
    else -> source
}
