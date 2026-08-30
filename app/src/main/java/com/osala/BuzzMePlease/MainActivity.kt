package com.osala.BuzzMePlease

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
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
        requestedOrientation = if (
            resources.configuration.smallestScreenWidthDp >= TABLET_WIDTH_DP
        ) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent { BuzzMeApp() }
    }
}
