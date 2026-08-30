package com.osala.BuzzMePlease.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osala.BuzzMePlease.ui.theme.Stage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Durée totale d'une annonce, de l'éclair au silence. */
private const val HOLD_MILLIS = 1_250L

/**
 * Le carton d'annonce : ce que le plateau crie au joueur, en plein milieu de son écran.
 *
 * Un buzz se joue en une demi-seconde et se lit sur une couleur ; le reste — la parole qui
 * arrive, le point qui tombe, l'élimination — se ratait facilement, faute d'être annoncé. D'où
 * ce carton de jeu télévisé : lignes de vitesse convergentes, bandeau incliné, texte cerné de
 * noir, entrée en ressort et sortie qui s'échappe vers l'avant. Deux secondes, puis plus rien.
 *
 * Il ne capte aucun appui : le buzzer reste utilisable pendant qu'il passe.
 */
@Composable
fun Announcement(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val finish by rememberUpdatedState(onDone)

    val scale = remember { Animatable(0.45f) }
    val tilt = remember { Animatable(-14f) }
    val fade = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val rays = remember { Animatable(0f) }

    // Le texte est la clé : une annonce qui en remplace une autre rejoue toute la scène.
    LaunchedEffect(text) {
        scale.snapTo(0.45f)
        tilt.snapTo(-14f)
        fade.snapTo(0f)
        flash.snapTo(1f)
        rays.snapTo(0f)

        launch { flash.animateTo(0f, tween(360, easing = LinearEasing)) }
        launch { fade.animateTo(1f, tween(110, easing = LinearEasing)) }
        launch { rays.animateTo(1f, tween(2_000, easing = LinearEasing)) }
        launch { tilt.animateTo(-6f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
        // Le ressort dépasse la cible puis revient : c'est ce rebond qui fait le coup de poing.
        scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))

        delay(HOLD_MILLIS)

        launch { fade.animateTo(0f, tween(230, easing = LinearEasing)) }
        scale.animateTo(1.32f, tween(250, easing = FastOutLinearInEasing))
        finish()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val veil = fade.value
            if (veil > 0f) {
                // Le décor s'assombrit par les bords : le regard tombe au centre.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Stage.Night.copy(alpha = 0.82f)),
                        center = center,
                        radius = size.maxDimension * 0.62f,
                    ),
                    alpha = veil,
                )

                // Lignes de vitesse : des coins vers le centre, largeurs inégales comme au trait.
                val turn = rays.value
                rotate(degrees = turn * 10f) {
                    val outer = size.maxDimension * 0.85f
                    val inner = size.minDimension * (0.30f + 0.06f * turn)
                    val count = 46
                    repeat(count) { i ->
                        val angle = (i * 360f / count) * (Math.PI / 180f).toFloat()
                        // Largeur pseudo-aléatoire mais stable : sans cela les traits clignotent.
                        // Des traits fins et nombreux, comme au feutre — pas des faisceaux.
                        val spread = (0.0016f + 0.0052f * ((i * 37) % 11) / 10f)
                        val path = Path().apply {
                            moveTo(
                                center.x + cos(angle) * inner,
                                center.y + sin(angle) * inner,
                            )
                            lineTo(
                                center.x + cos(angle - spread) * outer,
                                center.y + sin(angle - spread) * outer,
                            )
                            lineTo(
                                center.x + cos(angle + spread) * outer,
                                center.y + sin(angle + spread) * outer,
                            )
                            close()
                        }
                        drawPath(path, color = accent, alpha = 0.34f * veil)
                    }
                }

                // Les traits s'effacent près du bandeau : le texte ne se lit pas dans un buisson.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Stage.Night.copy(alpha = 0.90f),
                            Stage.Night.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.42f,
                    ),
                    alpha = veil,
                )
            }

            if (flash.value > 0f) {
                drawRect(color = Color.White, alpha = flash.value * 0.30f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = tilt.value
                    alpha = fade.value
                },
            contentAlignment = Alignment.Center,
        ) {
            // Le halo de la couleur annoncée, posé avant le bandeau pour rester derrière lui.
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }

            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Stage.Panel, Stage.Deep),
                        ),
                        shape,
                    )
                    .border(3.dp, accent.copy(alpha = 0.85f), shape)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val base = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                )
                // Deux passes : le cerne d'abord, la couleur ensuite. Le texte tient alors sur
                // n'importe quel fond, y compris le dôme allumé juste derrière.
                Text(
                    text = text,
                    style = base.copy(
                        drawStyle = Stroke(width = 16f),
                        color = Stage.Deep,
                    ),
                )
                Text(text = text, style = base.copy(color = accent))
            }
        }
    }
}
