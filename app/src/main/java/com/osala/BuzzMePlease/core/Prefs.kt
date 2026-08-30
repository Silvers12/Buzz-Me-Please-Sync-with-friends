package com.osala.BuzzMePlease.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osala.BuzzMePlease.model.RoomOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class Settings(
    val playerId: String,
    val name: String,
    val sound: Boolean,
    val keepScreenOn: Boolean,
    /** Le tutoriel s'ouvre tout seul au premier lancement, et une seule fois. */
    val tutorialSeen: Boolean,
    /**
     * Sonothèque de l'animateur : un identifiant de son par touche, chaîne vide pour une touche
     * libre. Toujours [SoundLibrary.SLOTS] entrées, pour que la grille garde sa forme.
     */
    val soundboard: List<String>,
    /** Langue choisie à la main, ou [AppLanguage.SYSTEM] pour suivre le téléphone. */
    val language: AppLanguage,
    /**
     * Les réglages de partie du dernier salon animé. Un groupe qui rejoue joue le plus souvent
     * de la même façon : inutile de les reposer à chaque salon.
     */
    val roomOptions: RoomOptions,
    /**
     * Le son du buzzer : un chemin d'asset pour un son livré avec le jeu, une URI
     * `content://` pour un fichier importé, vide pour le bip d'origine.
     */
    val buzzerSound: String,
    /**
     * Le dernier fichier importé, gardé même quand un autre son est choisi : il reste dans la
     * liste, on n'a pas à le rechercher pour y revenir.
     */
    val buzzerImport: String,
)

/**
 * Langue de l'application. Par défaut elle suit le système : un téléphone en français ouvre le
 * jeu en français, tous les autres en anglais. Le choix manuel prime, et se conserve.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
    ;

    companion object {
        fun of(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buzzme")

/** Réglages persistants. L'identifiant du joueur est stable : il survit à une coupure réseau,
 * ce qui permet de retrouver son score et son statut en se reconnectant. */
class Prefs(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            playerId = prefs[KEY_PLAYER_ID].orEmpty(),
            name = prefs[KEY_NAME].orEmpty(),
            sound = prefs[KEY_SOUND] ?: true,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true,
            tutorialSeen = prefs[KEY_TUTORIAL_SEEN] ?: false,
            soundboard = decodeSoundboard(prefs[KEY_SOUNDBOARD]),
            language = AppLanguage.of(prefs[KEY_LANGUAGE]),
            roomOptions = decodeOptions(prefs[KEY_ROOM_OPTIONS]),
            buzzerSound = prefs[KEY_BUZZER_SOUND].orEmpty(),
            buzzerImport = prefs[KEY_BUZZER_IMPORT].orEmpty(),
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

    suspend fun setSound(enabled: Boolean) = store.edit { it[KEY_SOUND] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = store.edit { it[KEY_KEEP_SCREEN_ON] = enabled }

    suspend fun setTutorialSeen() = store.edit { it[KEY_TUTORIAL_SEEN] = true }

    suspend fun setLanguage(language: AppLanguage) = store.edit { it[KEY_LANGUAGE] = language.name }

    /** Les réglages de partie, tels que l'animateur vient de les poser. */
    suspend fun setRoomOptions(options: RoomOptions) = store.edit {
        it[KEY_ROOM_OPTIONS] = optionsJson.encodeToString(RoomOptions.serializer(), options)
    }

    /** Le son du buzzer, vide pour revenir au bip. */
    suspend fun setBuzzerSound(source: String) = store.edit {
        it[KEY_BUZZER_SOUND] = source
        if (source.startsWith("content://")) it[KEY_BUZZER_IMPORT] = source
    }

    /** Mémorise la sonothèque telle qu'elle est posée : elle sera rechargée au salon suivant. */
    suspend fun setSoundboard(slots: List<String>) = store.edit {
        it[KEY_SOUNDBOARD] = slots.take(SoundLibrary.SLOTS).joinToString(SEPARATOR)
    }

    /** Un réglage inconnu ou illisible ne bloque rien : on repart des valeurs d'origine. */
    private fun decodeOptions(raw: String?): RoomOptions =
        raw?.let { runCatching { optionsJson.decodeFromString(RoomOptions.serializer(), it) }.getOrNull() }
            ?: RoomOptions()

    /** Toujours neuf entrées en sortie, quel que soit ce qui a été enregistré auparavant. */
    private fun decodeSoundboard(raw: String?): List<String> {
        val saved = raw?.split(SEPARATOR).orEmpty()
        return List(SoundLibrary.SLOTS) { index -> saved.getOrNull(index).orEmpty() }
    }

    private companion object {
        const val SEPARATOR = "|"
        val KEY_SOUNDBOARD = stringPreferencesKey("soundboard")
        val KEY_BUZZER_SOUND = stringPreferencesKey("buzzer_sound")
        val KEY_BUZZER_IMPORT = stringPreferencesKey("buzzer_import")
        val KEY_ROOM_OPTIONS = stringPreferencesKey("room_options")

        /** Tolérant aux champs ajoutés plus tard : une option inconnue est ignorée. */
        val optionsJson = Json { ignoreUnknownKeys = true }
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_PLAYER_ID = stringPreferencesKey("player_id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }
}
