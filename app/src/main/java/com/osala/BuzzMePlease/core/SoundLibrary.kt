package com.osala.BuzzMePlease.core

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

    /**
     * Sons proposés, dans l'ordre de la liste de choix. Le libellé de chacun vit dans
     * `strings.xml` sous la clé `sound_<id>`, pour être traduit comme le reste.
     */
    private val CATALOG = listOf(
        // Verdicts : les deux touches qu'un animateur pose en premier.
        "correct", "wrong", "tadaa", "wow", "cash_register", "fireworks", "drum_joke",
        "boom", "gunshot", "glass_breaking", "whoosh", "pop", "doorbell", "horn",
        "car_honk", "police_siren", "car_engine", "clock", "slow_clock_ticking",
        "heartbeat", "winter_wind", "keyboard_typing", "violin", "witch_laugh",
        "evil_laugh", "monster_growl", "pathetic_screaming", "i_see_you", "stop_it",
        "shut_up", "baby_crying", "meow", "goat", "frog", "rooster_crowing", "wet_fart",
    )

    /** Les sons réellement présents dans l'application, dans l'ordre du catalogue. */
    fun clips(context: Context): List<SoundClip> {
        val res = context.resources
        val packageName = context.packageName
        return CATALOG.mapNotNull { id ->
            @Suppress("DiscouragedApi")
            val resId = res.getIdentifier(id, "raw", packageName)
            if (resId == 0) return@mapNotNull null
            @Suppress("DiscouragedApi")
            val labelId = res.getIdentifier("sound_$id", "string", packageName)
            SoundClip(id, if (labelId == 0) id else context.getString(labelId), resId)
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
