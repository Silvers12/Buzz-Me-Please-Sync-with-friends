package fr.buzzme.core

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Habillage sonore et haptique du plateau.
 *
 * Les sons sont générés par [ToneGenerator] : aucun fichier audio à embarquer, et surtout aucune
 * latence de décodage — un bip demandé part immédiatement, ce qui compte pour le « TOP ».
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

    private fun play(tone: Int, durationMillis: Int, vibrate: Long) {
        if (!enabled) return
        runCatching { tones?.startTone(tone, durationMillis) }
        if (vibrate <= 0) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(vibrate, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun release() {
        runCatching { tones?.release() }
    }

    private companion object {
        const val VOLUME = 90
    }
}
