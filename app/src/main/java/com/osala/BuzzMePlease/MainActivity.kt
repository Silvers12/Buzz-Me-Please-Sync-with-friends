package com.osala.BuzzMePlease

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osala.BuzzMePlease.ui.BuzzMeApp

/** En dessous de cette largeur, on tient l'appareil d'une main : c'est un téléphone. */
private const val TABLET_WIDTH_DP = 600

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Le plateau est toujours sombre : les icônes système doivent rester claires, y compris
        // sur un téléphone réglé en thème clair.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Le téléphone reste bloqué en portrait : sa disposition est réglée pour une main, et
        // une manche ne se joue pas en tournant l'appareil. Une tablette, elle, se pose devant
        // l'animateur en paysage — c'est là que le pupitre a un sens, on lui rend la rotation.
        // Le manifeste demande le portrait au démarrage : un téléphone ne voit donc jamais
        // l'autre sens, même l'espace d'une image.
        requestedOrientation = if (smallestScreenSideDp() >= TABLET_WIDTH_DP) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent { BuzzMeApp() }
    }

    /**
     * Le petit côté de l'écran entier, en dp.
     *
     * On mesure l'écran, pas la fenêtre : le manifeste demandant le portrait, une tablette tenue
     * en paysage démarre dans une fenêtre mise en boîte, plus étroite que l'appareil. Lire la
     * configuration de l'activité reviendrait alors à mesurer la conséquence du verrou pour
     * décider s'il faut le lever — un Redmi Pad de 685 dp s'annonce ainsi en 562 dp et resterait
     * verrouillé pour toujours. `maximumWindowMetrics` donne les bornes que la fenêtre pourrait
     * occuper au mieux, que la mise en boîte ne rétrécit pas.
     */
    private fun smallestScreenSideDp(): Int {
        val density = resources.displayMetrics.density
        val side = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            minOf(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            minOf(metrics.widthPixels, metrics.heightPixels)
        }
        return (side / density).toInt()
    }
}
