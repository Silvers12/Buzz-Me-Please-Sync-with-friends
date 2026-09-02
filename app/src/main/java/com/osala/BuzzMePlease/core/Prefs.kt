package com.osala.BuzzMePlease.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osala.BuzzMePlease.model.RoomOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
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
     * Les fichiers apportés par l'utilisateur, en URI. Ils rejoignent la bibliothèque du jeu et
     * servent aussi bien au buzzer qu'aux touches de la sonothèque — on ne les importe qu'une
     * fois pour les deux. Gardés même quand un autre son est choisi : on y revient d'une touche.
     */
    val imports: List<String>,
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

/**
 * Un fichier de préférences corrompu (coupure pendant une écriture, restauration
 * partielle) fait lever `CorruptionException` à la PREMIÈRE lecture. Sans
 * gestionnaire, l'exception traverse le flux [Prefs.settings], que le ViewModel
 * collecte au démarrage : l'application crasherait alors à chaque ouverture, de
 * façon définitive.
 *
 * On repart d'un jeu vide : le joueur retrouve les réglages d'origine et un
 * nouvel identifiant, ce qui est récupérable, au lieu d'un jeu mort.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "buzzme",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Réglages persistants. L'identifiant du joueur est stable : il survit à une coupure réseau,
 * ce qui permet de retrouver son score et son statut en se reconnectant. */
class Prefs(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data
        // `retryWhen` avant tout repli : les erreurs de lecture DataStore sont le
        // plus souvent passagères (fichier verrouillé, contention d'écriture). Un
        // `catch` seul aurait TERMINÉ le flux — le collecteur unique de
        // `AppViewModel` aurait rendu la main, et plus aucun changement de réglage
        // n'aurait été vu pour le reste du processus.
        .retryWhen { cause, attempt ->
            CrashReporter.recordOnce("prefs-read", cause, "Prefs.settings")
            if (attempt < MAX_READ_RETRIES) {
                delay(READ_RETRY_MILLIS * (attempt + 1))
                true
            } else {
                false
            }
        }
        .map { prefs ->
        Settings(
            playerId = prefs[KEY_PLAYER_ID].orEmpty(),
            name = prefs[KEY_NAME].orEmpty(),
            sound = prefs[KEY_SOUND] ?: true,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true,
            tutorialSeen = prefs[KEY_TUTORIAL_SEEN] ?: false,
            soundboard = decodeSoundboard(prefs[KEY_SOUNDBOARD]),
            language = AppLanguage.of(prefs[KEY_LANGUAGE]),
            roomOptions = decodeOptions(prefs[KEY_ROOM_OPTIONS]),
            // Les sons ne vivent plus en deux dossiers : un choix enregistré avant la fusion
            // pointerait dans le vide.
            buzzerSound = SoundLibrary.migratePath(prefs[KEY_BUZZER_SOUND].orEmpty()),
            imports = decodeImports(prefs[KEY_IMPORTS], prefs[KEY_BUZZER_IMPORT]),
        )
    }
        // Placé APRÈS le `map` : couvre aussi l'analyse des chaînes persistées
        // (`decodeSoundboard`, `AppLanguage.of`, `migratePath`, `decodeImports`),
        // qui était la seule partie non protégée du flux.
        //
        // On ne réinjecte volontairement AUCUNE valeur : émettre un jeu vide
        // produirait un `playerId` vide, et `AppViewModel.startSession` ouvrirait un
        // salon avec cet identifiant — côté hôte, chaque invité à identifiant vide
        // évincerait le précédent. Le flux se termine, l'application reste sur les
        // réglages qu'elle a déjà, et `AppViewModel` garantit un identifiant valide.
        .catch { failure ->
            CrashReporter.recordOnce("prefs-read-final", failure, "Prefs.settings.final")
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
    }

    /**
     * Range un fichier apporté par l'utilisateur dans la bibliothèque commune. Le même fichier
     * importé deux fois ne s'y ajoute pas deux fois, et la liste est bornée : ce sont des
     * autorisations d'accès que le système garde ouvertes, pas seulement des chaînes.
     */
    suspend fun addImport(uri: String) = store.edit { prefs ->
        val current = decodeImports(prefs[KEY_IMPORTS], prefs[KEY_BUZZER_IMPORT])
        if (uri.isBlank() || uri in current) return@edit
        prefs[KEY_IMPORTS] = (current + uri).takeLast(MAX_IMPORTS).joinToString(SEPARATOR)
    }

    /** Mémorise la sonothèque telle qu'elle est posée : elle sera rechargée au salon suivant. */
    suspend fun setSoundboard(slots: List<String>) = store.edit {
        it[KEY_SOUNDBOARD] = slots.take(SoundLibrary.SLOTS).joinToString(SEPARATOR)
    }

    /**
     * Un réglage inconnu ou illisible ne bloque rien : on repart des valeurs d'origine.
     *
     * Le repli reste silencieux pour l'utilisateur, mais il est signalé : le JSON
     * est écrit par cette même application, avec `ignoreUnknownKeys` déjà actif
     * pour absorber les champs ajoutés plus tard. Un échec de lecture signe donc un
     * changement de format incompatible entre deux versions, et l'animateur
     * retrouve tous ses réglages de partie remis à zéro sans comprendre pourquoi.
     */
    private fun decodeOptions(raw: String?): RoomOptions =
        raw?.let { stored ->
            runCatching { optionsJson.decodeFromString(RoomOptions.serializer(), stored) }
                .onFailure {
                    // On ne transmet PAS l'exception : le message d'une
                    // `JsonDecodingException` contient l'entrée brute. La charge
                    // utile est ici bénigne (`RoomOptions` n'est qu'un enum et
                    // quelques booléens), mais la règle vaut mieux que l'exception
                    // à la règle — le jour où une option accueille un texte libre,
                    // personne ne repassera par ici.
                    CrashReporter.recordAnomalyOnce(
                        key = "prefs-room-options",
                        message = "Réglages de partie illisibles (${stored.length} car.), retour aux valeurs par défaut",
                        context = "Prefs.decodeOptions",
                    )
                }
                .getOrNull()
        } ?: RoomOptions()

    /**
     * Les sons importés. Une version antérieure n'en gardait qu'un, celui du buzzer : il ouvre
     * la liste plutôt que d'être perdu à la mise à jour.
     */
    private fun decodeImports(raw: String?, legacy: String?): List<String> {
        val saved = raw?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        if (saved.isNotEmpty()) return saved
        return listOfNotNull(legacy?.takeIf { it.isNotBlank() })
    }

    /** Toujours neuf entrées en sortie, quel que soit ce qui a été enregistré auparavant. */
    private fun decodeSoundboard(raw: String?): List<String> {
        val saved = raw?.split(SEPARATOR).orEmpty()
        return List(SoundLibrary.SLOTS) { index -> saved.getOrNull(index).orEmpty() }
    }

    private companion object {
        const val SEPARATOR = "|"

        /** Tentatives de relecture avant de renoncer, les erreurs DataStore étant surtout passagères. */
        const val MAX_READ_RETRIES = 3
        const val READ_RETRY_MILLIS = 150L

        /** Au-delà, ce sont des autorisations d'accès accumulées pour rien. */
        const val MAX_IMPORTS = 12
        val KEY_SOUNDBOARD = stringPreferencesKey("soundboard")
        val KEY_BUZZER_SOUND = stringPreferencesKey("buzzer_sound")
        val KEY_IMPORTS = stringPreferencesKey("sound_imports")

        /** Le son importé unique d'avant : relu une fois, pour ne pas le perdre. */
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
