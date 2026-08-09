package fr.buzzme.net.online

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Réglages Firebase saisis dans l'application.
 *
 * Ils sont volontairement fournis à l'exécution plutôt que via `google-services.json` :
 * le projet se compile et s'installe tel quel, et chaque groupe d'amis peut brancher
 * son propre projet Firebase sans toucher au code. Voir `docs/FIREBASE.md`.
 */
data class FirebaseConfig(
    val projectId: String = "",
    val applicationId: String = "",
    val apiKey: String = "",
    val databaseUrl: String = "",
) {
    val isComplete: Boolean
        get() = projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank() &&
            databaseUrl.startsWith("https://")
}

object FirebaseHolder {

    private const val TAG = "FirebaseHolder"
    private const val APP_NAME = "buzzme"

    @Volatile
    private var current: FirebaseConfig? = null

    @Volatile
    private var app: FirebaseApp? = null

    /** Ouvre (ou rouvre) la base correspondant à [config]. */
    @Synchronized
    fun database(context: Context, config: FirebaseConfig): FirebaseDatabase {
        require(config.isComplete) { "Configuration Firebase incomplète" }
        val existing = app
        if (existing != null && current == config) {
            return FirebaseDatabase.getInstance(existing, config.databaseUrl)
        }
        existing?.let { runCatching { it.delete() } }

        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.applicationId)
            .setApiKey(config.apiKey)
            .setDatabaseUrl(config.databaseUrl)
            .build()
        val created = FirebaseApp.initializeApp(context.applicationContext, options, APP_NAME)
        app = created
        current = config
        return FirebaseDatabase.getInstance(created, config.databaseUrl)
    }

    /**
     * Connexion anonyme, si elle est activée sur le projet. En cas d'échec on continue :
     * les règles fournies dans `firebase/database.rules.json` peuvent aussi être assouplies.
     */
    fun signIn(onDone: (Boolean) -> Unit) {
        val instance = app
        if (instance == null) {
            onDone(false)
            return
        }
        val auth = runCatching { FirebaseAuth.getInstance(instance) }.getOrNull()
        if (auth == null) {
            onDone(false)
            return
        }
        if (auth.currentUser != null) {
            onDone(true)
            return
        }
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "connexion anonyme refusée: ${task.exception?.message}")
                }
                onDone(task.isSuccessful)
            }
    }
}
