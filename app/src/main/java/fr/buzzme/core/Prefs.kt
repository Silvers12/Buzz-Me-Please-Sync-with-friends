package fr.buzzme.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.buzzme.net.online.FirebaseConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Transport { LOCAL, ONLINE }

data class Settings(
    val playerId: String,
    val name: String,
    val transport: Transport,
    val firebase: FirebaseConfig,
    val sound: Boolean,
    val keepScreenOn: Boolean,
    /** Le tutoriel s'ouvre tout seul au premier lancement, et une seule fois. */
    val tutorialSeen: Boolean,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buzzme")

/** Réglages persistants. L'identifiant du joueur est stable : il survit à une coupure réseau,
 * ce qui permet de retrouver son score et son statut en se reconnectant. */
class Prefs(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            playerId = prefs[KEY_PLAYER_ID].orEmpty(),
            name = prefs[KEY_NAME].orEmpty(),
            transport = prefs[KEY_TRANSPORT]?.let { runCatching { Transport.valueOf(it) }.getOrNull() }
                ?: Transport.LOCAL,
            firebase = FirebaseConfig(
                projectId = prefs[KEY_FB_PROJECT].orEmpty(),
                applicationId = prefs[KEY_FB_APP].orEmpty(),
                apiKey = prefs[KEY_FB_KEY].orEmpty(),
                databaseUrl = prefs[KEY_FB_URL].orEmpty(),
            ),
            sound = prefs[KEY_SOUND] ?: true,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true,
            tutorialSeen = prefs[KEY_TUTORIAL_SEEN] ?: false,
        )
    }

    /** Retourne l'identifiant existant, ou en crée un à la première ouverture. */
    suspend fun ensurePlayerId(): String {
        var id = ""
        store.edit { prefs ->
            val existing = prefs[KEY_PLAYER_ID]
            id = if (existing.isNullOrBlank()) {
                Codes.newPlayerId().also { prefs[KEY_PLAYER_ID] = it }
            } else {
                existing
            }
        }
        return id
    }

    suspend fun setName(name: String) = store.edit { it[KEY_NAME] = name }

    suspend fun setTransport(transport: Transport) = store.edit { it[KEY_TRANSPORT] = transport.name }

    suspend fun setSound(enabled: Boolean) = store.edit { it[KEY_SOUND] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = store.edit { it[KEY_KEEP_SCREEN_ON] = enabled }

    suspend fun setTutorialSeen() = store.edit { it[KEY_TUTORIAL_SEEN] = true }

    suspend fun setFirebase(config: FirebaseConfig) = store.edit {
        it[KEY_FB_PROJECT] = config.projectId
        it[KEY_FB_APP] = config.applicationId
        it[KEY_FB_KEY] = config.apiKey
        it[KEY_FB_URL] = config.databaseUrl
    }

    private companion object {
        val KEY_PLAYER_ID = stringPreferencesKey("player_id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_TRANSPORT = stringPreferencesKey("transport")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
        val KEY_FB_PROJECT = stringPreferencesKey("fb_project")
        val KEY_FB_APP = stringPreferencesKey("fb_app")
        val KEY_FB_KEY = stringPreferencesKey("fb_key")
        val KEY_FB_URL = stringPreferencesKey("fb_url")
    }
}
