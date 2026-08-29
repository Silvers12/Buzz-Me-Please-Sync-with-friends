package com.osala.BuzzMePlease.core

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Habillage sonore et haptique du plateau.
 *
 * Tout ce qui doit tomber à l'instant près — le décompte, le go — passe par [ToneGenerator] :
 * aucune latence de décodage, un bip demandé part immédiatement. Le buzz et la mauvaise réponse
 * jouent un vrai son : ils arrivent après coup, l'heure du buzz étant déjà relevée sur l'appui
 * lui-même, et méritent d'être entendus comme tels.
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

    /** Fait écouter un son sans passer par le jeu : l'aperçu des réglages. */
    fun preview(source: String) {
        if (source.isBlank()) {
            runCatching { tones?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 320) }
        } else {
            playClip(source)
        }
    }

    private fun playClip(source: String): Boolean {
        val player = open(appContext, source) ?: return false
        runCatching { clip?.release() }
        player.setOnCompletionListener {
            if (clip === it) clip = null
            runCatching { it.release() }
        }
        clip = player
        return runCatching { player.start() }.isSuccess
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
        runCatching { tones?.release() }
        runCatching { clip?.release() }
        clip = null
    }

    private companion object {
        const val VOLUME = 90
    }
}
