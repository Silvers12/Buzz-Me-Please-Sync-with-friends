package com.osala.BuzzMePlease.core

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.osala.BuzzMePlease.R

/**
 * Habillage sonore et haptique du plateau.
 *
 * Tout ce qui doit tomber à l'instant près — le décompte, le go, le buzz — passe par
 * [ToneGenerator] : aucune latence de décodage, un bip demandé part immédiatement. Seule la
 * mauvaise réponse joue un vrai clip, parce qu'elle arrive après coup et doit s'entendre.
 */
class SoundFx(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Le clip « mauvaise réponse » en cours, gardé pour pouvoir le libérer. */
    private var wrongClip: MediaPlayer? = null

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()

    /** Bip sec du décompte 3 · 2 · 1. */
    fun tick() = play(ToneGenerator.TONE_PROP_BEEP, 90, vibrate = 12)

    /** Le top : buzzers verts. */
    fun go() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350, vibrate = 40)

    /** Mon buzz est parti. */
    fun buzz() = play(ToneGenerator.TONE_CDMA_HIGH_L, 320, vibrate = 60)

    /** Quelqu'un d'autre a coiffé tout le monde au poteau. */
    fun locked() = play(ToneGenerator.TONE_PROP_NACK, 200, vibrate = 20)

    /** La parole vous revient : à vous de répondre. */
    fun yourTurn() = play(ToneGenerator.TONE_PROP_ACK, 300, vibrate = 45)

    /**
     * Mauvaise réponse : l'animateur passe la main au suivant. Celui qui perd la parole
     * entend le vrai son du jeu, pas un bip — c'est une sanction, elle doit s'entendre. Le bip
     * reste en secours si le décodage échoue.
     */
    fun wrong() {
        if (!enabled) return
        buzzVibration(120)
        val started = runCatching {
            wrongClip?.release()
            wrongClip = MediaPlayer.create(appContext, R.raw.wrong)?.apply {
                setOnCompletionListener { player ->
                    if (wrongClip === player) wrongClip = null
                    runCatching { player.release() }
                }
                start()
            }
            wrongClip != null
        }.getOrDefault(false)
        if (!started) runCatching { tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 450) }
    }

    private fun play(tone: Int, durationMillis: Int, vibrate: Long) {
        if (!enabled) return
        runCatching { tones?.startTone(tone, durationMillis) }
        buzzVibration(vibrate)
    }

    private fun buzzVibration(millis: Long) {
        if (millis <= 0) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun release() {
        runCatching { tones?.release() }
        runCatching { wrongClip?.release() }
        wrongClip = null
    }

    private companion object {
        const val VOLUME = 90
    }
}
