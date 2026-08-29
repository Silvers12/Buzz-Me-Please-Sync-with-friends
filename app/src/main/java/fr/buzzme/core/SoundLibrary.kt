package fr.buzzme.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Un son de la sonothèque : le nom du fichier déposé dans `res/raw`, et le libellé montré à
 * l'animateur.
 */
data class SoundClip(val id: String, val label: String, val resId: Int)

/**
 * La sonothèque de l'animateur.
 *
 * **Ajouter un son** : déposer le fichier dans `app/src/main/res/raw` (nom en minuscules, sans
 * accent ni tiret : `correct.mp3`, `time_up.ogg`…) puis ajouter une ligne dans [CATALOG]. Un
 * son déclaré ici mais absent du dossier est simplement ignoré : le projet compile toujours,
 * et la sonothèque n'affiche que ce qui existe vraiment.
 */
object SoundLibrary {

    /** Nom de fichier attendu → libellé affiché. L'ordre est celui de la liste de choix. */
    private val CATALOG = listOf(
        // Verdicts : les deux touches qu'un animateur pose en premier.
        "correct" to "Bonne réponse",
        "wrong" to "Mauvaise réponse",
        "wow" to "Wow",
        "cash_register" to "Tiroir-caisse",
        "fireworks" to "Feu d'artifice",
        "boom" to "Explosion",
        "gunshot" to "Coup de feu",
        "glass_breaking" to "Verre brisé",
        "whoosh" to "Souffle",
        "pop" to "Pop",
        "doorbell" to "Sonnette",
        "horn" to "Corne de brume",
        "car_honk" to "Klaxon",
        "police_siren" to "Sirène de police",
        "car_engine" to "Moteur",
        "clock" to "Horloge",
        "slow_clock_ticking" to "Tic-tac lent",
        "heartbeat" to "Battements de cœur",
        "winter_wind" to "Vent d'hiver",
        "keyboard_typing" to "Clavier",
        "violin" to "Violon triste",
        "witch_laugh" to "Rire de sorcière",
        "evil_laugh" to "Rire diabolique",
        "monster_growl" to "Grognement",
        "pathetic_screaming" to "Cri pathétique",
        "i_see_you" to "Je te vois",
        "stop_it" to "Arrête ça",
        "baby_crying" to "Bébé qui pleure",
        "meow" to "Miaulement",
        "goat" to "Chèvre",
        "frog" to "Grenouille",
        "rooster_crowing" to "Coq",
        "wet_fart" to "Pet",
    )

    /** Les sons réellement présents dans l'application, dans l'ordre du catalogue. */
    fun clips(context: Context): List<SoundClip> {
        val res = context.resources
        val packageName = context.packageName
        return CATALOG.mapNotNull { (id, label) ->
            @Suppress("DiscouragedApi")
            val resId = res.getIdentifier(id, "raw", packageName)
            if (resId == 0) null else SoundClip(id, label, resId)
        }
    }

    /** Nombre de touches de la sonothèque : trois rangées de trois, à portée du pouce. */
    const val SLOTS = 9
}

/**
 * Lecteur de la sonothèque. Un seul son à la fois : réappuyer coupe le précédent, ce qui évite
 * l'empilement de jingles quand l'animateur enchaîne les manches.
 */
class ClipPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    /** Identifiant du son en cours : la touche correspondante s'allume tant qu'il joue. */
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    fun play(clip: SoundClip, onFinished: () -> Unit = {}) {
        stop()
        val created = runCatching { MediaPlayer.create(appContext, clip.resId) }.getOrNull() ?: return
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        created.setOnCompletionListener {
            _playing.value = null
            onFinished()
            it.release()
            if (player === it) player = null
        }
        player = created
        _playing.value = clip.id
        runCatching { created.start() }
    }

    fun stop() {
        val current = player ?: return
        player = null
        _playing.value = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    fun release() = stop()
}
