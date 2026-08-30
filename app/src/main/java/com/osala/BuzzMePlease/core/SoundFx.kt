package com.osala.BuzzMePlease.core

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Habillage sonore et haptique du plateau.
 *
 * Tout ce qui doit tomber à l'instant près — le décompte, le go — passe par [ToneGenerator] :
 * aucune latence de décodage, un bip demandé part immédiatement. Le buzz et les verdicts de
 * l'animateur jouent un vrai son : ils arrivent après coup, l'heure du buzz étant déjà relevée
 * sur l'appui lui-même, et méritent d'être entendus comme tels.
 */
class SoundFx(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    /**
     * Le son du buzzer choisi par le joueur : un chemin d'asset, une URI `content://` pour un
     * fichier importé, ou vide pour le bip d'origine.
     */
    @Volatile
    var buzzerSound: String = ""

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Le son en cours, gardé pour pouvoir le libérer. */
    private var clip: MediaPlayer? = null

    /** La minuterie qui borne la durée d'un son choisi par le joueur. */
    private val cutter = Handler(Looper.getMainLooper())

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()

    /** Bip sec du décompte 3 · 2 · 1. */
    fun tick() = play(ToneGenerator.TONE_PROP_BEEP, 90, vibrate = 12)

    /** Le go : buzzers verts. */
    fun go() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350, vibrate = 40)

    /** Mon buzz est parti — avec le son que le joueur s'est choisi, s'il en a choisi un. */
    fun buzz() {
        if (!enabled) return
        vibrate(60)
        val chosen = buzzerSound
        if (chosen.isNotBlank() && playClip(chosen)) return
        runCatching { tones?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 320) }
    }

    /** Quelqu'un d'autre a coiffé tout le monde au poteau. */
    fun locked() = play(ToneGenerator.TONE_PROP_NACK, 200, vibrate = 20)

    /** La parole vous revient : à vous de répondre. */
    fun yourTurn() = play(ToneGenerator.TONE_PROP_ACK, 300, vibrate = 45)

    /**
     * Mauvaise réponse : l'animateur passe la main au suivant. Celui qui perd la parole entend
     * le vrai son du jeu, pas un bip. Le bip reste en secours si le fichier est illisible.
     */
    fun wrong() {
        if (!enabled) return
        vibrate(120)
        val path = SoundLibrary.wrongPath(appContext)
        if (path != null && playClip(path)) return
        runCatching { tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 450) }
    }

    /**
     * Bonne réponse : l'animateur valide, le buzzer passe au vert. Le vrai son du jeu là aussi,
     * avec le bip d'acquittement en secours si le fichier est illisible.
     */
    fun correct() {
        if (!enabled) return
        vibrate(70)
        val path = SoundLibrary.correctPath(appContext)
        if (path != null && playClip(path)) return
        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 350) }
    }

    /**
     * La partie est finie : le feu d'artifice part sur tous les téléphones à la fois.
     *
     * Il a droit à plus que les quatre secondes d'un buzzer — c'est le dénouement, et le
     * carton reste dix secondes à l'écran. La borne est là pour qu'il ne lui survive pas.
     */
    fun celebrate() {
        if (!enabled) return
        vibrate(220)
        val path = SoundLibrary.fireworksPath(appContext)
        if (path != null && playClip(path, CELEBRATION_MILLIS)) return
        runCatching { tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500) }
    }

    /** Fait écouter un son sans passer par le jeu : l'aperçu des réglages. */
    fun preview(source: String) {
        if (source.isBlank()) {
            runCatching { tones?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 320) }
        } else {
            playClip(source)
        }
    }

    /**
     * @param limitMillis durée au-delà de laquelle le son est coupé. Un joueur peut choisir
     *   n'importe quel fichier de son téléphone : sans cette borne, un morceau de deux minutes
     *   couvrirait la manche entière, et la suivante.
     */
    private fun playClip(source: String, limitMillis: Long = MAX_CLIP_MILLIS): Boolean {
        val player = open(appContext, source) ?: return false
        cutter.removeCallbacks(cut)
        runCatching { clip?.release() }
        player.setOnCompletionListener {
            if (clip === it) clip = null
            cutter.removeCallbacks(cut)
            runCatching { it.release() }
        }
        clip = player
        val started = runCatching { player.start() }.isSuccess
        if (started && limitMillis > 0) cutter.postDelayed(cut, limitMillis)
        return started
    }

    /** Coupe le son en cours : le fondu n'apporterait rien sur un jingle de plateau. */
    private val cut = Runnable {
        val current = clip ?: return@Runnable
        clip = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun play(tone: Int, durationMillis: Int, vibrate: Long) {
        if (!enabled) return
        runCatching { tones?.startTone(tone, durationMillis) }
        vibrate(vibrate)
    }

    private fun vibrate(millis: Long) {
        if (millis <= 0) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun release() {
        cutter.removeCallbacks(cut)
        runCatching { tones?.release() }
        runCatching { clip?.release() }
        clip = null
    }

    private companion object {
        const val VOLUME = 90

        /**
         * Un son de buzzer ne dépasse pas quatre secondes. C'est assez pour un jingle, et
         * assez court pour que le morceau de deux minutes importé par un plaisantin ne couvre
         * pas la manche.
         */
        const val MAX_CLIP_MILLIS = 4_000L

        /** Le feu d'artifice de la fin de partie, borné à la durée du carton qu'il habille. */
        const val CELEBRATION_MILLIS = 10_000L
    }
}
