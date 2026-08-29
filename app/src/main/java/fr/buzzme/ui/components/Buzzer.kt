package fr.buzzme.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import fr.buzzme.model.BuzzerVisual
import fr.buzzme.ui.theme.Stage

private data class BuzzerSkin(
    val highlight: Color,
    val body: Color,
    val shadow: Color,
    val ring: Color,
    val glow: Color,
    val text: Color,
)

private fun skinFor(visual: BuzzerVisual): BuzzerSkin = when (visual) {
    BuzzerVisual.ARMED -> BuzzerSkin(
        highlight = Color(0xFF9BFFC8),
        body = Stage.Green,
        shadow = Stage.GreenDeep,
        ring = Color(0xFF37FF9B),
        glow = Stage.Green,
        text = Color(0xFF05310F),
    )

    BuzzerVisual.BUZZED -> BuzzerSkin(
        highlight = Color(0xFFFF9AAC),
        body = Stage.Red,
        shadow = Stage.RedDeep,
        ring = Color(0xFFFF5E78),
        glow = Stage.Red,
        text = Color(0xFF3B0009),
    )

    BuzzerVisual.COUNTDOWN -> BuzzerSkin(
        highlight = Color(0xFFFFD79A),
        body = Stage.Amber,
        shadow = Color(0xFF7A4300),
        ring = Color(0xFFFFC061),
        glow = Stage.Amber,
        text = Color(0xFF3A2000),
    )

    // Le vert de la parole : plus profond que celui du buzzer armé, pour qu'un coup d'œil
    // distingue « appuyez » de « c'est à vous de répondre ».
    BuzzerVisual.SPEAKING -> BuzzerSkin(
        highlight = Color(0xFFB6FFD8),
        body = Color(0xFF1CC96C),
        shadow = Color(0xFF07572D),
        ring = Color(0xFF5CFFB0),
        glow = Stage.Green,
        text = Color(0xFF03270F),
    )

    BuzzerVisual.LOST -> BuzzerSkin(
        highlight = Color(0xFF4A4A6E),
        body = Color(0xFF34344F),
        shadow = Color(0xFF1A1A2B),
        ring = Color(0xFF454568),
        glow = Color(0xFF2A2A44),
        text = Stage.TextMuted,
    )

    BuzzerVisual.ELIMINATED -> BuzzerSkin(
        highlight = Color(0xFF262636),
        body = Color(0xFF14141C),
        shadow = Color(0xFF07070B),
        ring = Color(0xFF31313F),
        glow = Color(0xFF101018),
        text = Color(0xFF6A6A80),
    )

    BuzzerVisual.OFF -> BuzzerSkin(
        highlight = Color(0xFF5A5A85),
        body = Color(0xFF3E3E60),
        shadow = Color(0xFF1D1D30),
        ring = Color(0xFF56567F),
        glow = Color(0xFF3A3A5C),
        text = Color(0xFFCFCEE8),
    )
}

/**
 * Le buzzer.
 *
 * Point clé : l'horodatage n'est pas pris au moment où l'on réagit à l'événement, mais lu sur
 * l'événement tactile lui-même ([androidx.compose.ui.input.pointer.PointerInputChange.uptimeMillis]).
 * Le temps de recomposition, de rendu et d'ordonnancement ne se retrouve donc pas dans le
 * chrono du joueur : on mesure l'appui, pas la charge du téléphone.
 */
@Composable
fun BigBuzzer(
    visual: BuzzerVisual,
    title: String,
    subtitle: String,
    onPress: (uptimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = skinFor(visual)
    var pressed by remember { mutableStateOf(false) }
    // Le détecteur de geste n'est jamais relancé : il doit donc lire la dernière lambda connue.
    val currentOnPress by rememberUpdatedState(onPress)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 900f),
        label = "press",
    )

    val pulse = rememberInfiniteTransition(label = "buzzerPulse")
    val breathing by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathing",
    )
    // Le halo respire quand le buzzer attend un geste : armé, ou en attente de la réponse.
    val alive = visual == BuzzerVisual.ARMED || visual == BuzzerVisual.SPEAKING
    val haloBoost = if (alive) 0.55f + breathing * 0.45f else 0.35f

    // Onde de choc au moment du buzz.
    val shockwave = remember { Animatable(0f) }
    LaunchedEffect(visual) {
        if (visual == BuzzerVisual.BUZZED) {
            shockwave.snapTo(0f)
            shockwave.animateTo(1f, tween(520, easing = LinearEasing))
        } else {
            shockwave.snapTo(0f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    currentOnPress(down.uptimeMillis)
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current

        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = center

            // Halo lumineux
            val haloRadius = radius * (0.98f + 0.02f * breathing)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        skin.glow.copy(alpha = 0.42f * haloBoost),
                        skin.glow.copy(alpha = 0.10f * haloBoost),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = c,
            )

            // Onde de choc
            val wave = shockwave.value
            if (wave > 0f) {
                drawCircle(
                    color = skin.ring.copy(alpha = (1f - wave) * 0.55f),
                    radius = radius * (0.62f + wave * 0.36f),
                    center = c,
                    style = Stroke(width = radius * 0.06f * (1f - wave) + 1f),
                )
            }

            // Socle métallique
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3A3A55), Color(0xFF15151F)),
                    startY = c.y - radius * 0.82f,
                    endY = c.y + radius * 0.82f,
                ),
                radius = radius * 0.82f,
                center = c,
            )
            drawCircle(
                color = skin.ring.copy(alpha = 0.6f),
                radius = radius * 0.82f,
                center = c,
                style = Stroke(width = radius * 0.022f),
            )

            // Dôme
            val domeRadius = radius * 0.70f * pressScale
            val lightCenter = Offset(c.x - domeRadius * 0.32f, c.y - domeRadius * 0.36f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(skin.highlight, skin.body, skin.shadow),
                    center = lightCenter,
                    radius = domeRadius * 1.45f,
                ),
                radius = domeRadius,
                center = c,
            )

            // Reflet spéculaire
            val glossWidth = domeRadius * 0.86f
            val glossHeight = domeRadius * 0.44f
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                ),
                topLeft = Offset(c.x - glossWidth / 2f, c.y - domeRadius * 0.82f),
                size = Size(glossWidth, glossHeight),
            )
        }

        Column(
            // Le texte reste à l'intérieur du dôme (0,70 du rayon) : un pseudo long ou une police
            // système agrandie doivent se replier, pas déborder sur le socle.
            modifier = Modifier.fillMaxWidth(0.64f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // L'inscription est gravée sur le dôme : elle se mesure au diamètre du buzzer, pas
            // à l'échelle de police du système, sans quoi elle déborderait du bouton sur un petit
            // écran ou avec une police agrandie. Le décompte occupe tout le dôme, les libellés
            // longs (« TROP TARD », un chrono) rapetissent d'un cran.
            val titleSize = with(density) {
                when {
                    title.length <= 2 -> diameter * 0.24f
                    title.length <= 9 -> diameter * 0.11f
                    else -> diameter * 0.09f
                }.toSp()
            }
            val subtitleSize = with(density) {
                (diameter * if (subtitle.length > 14) 0.038f else 0.046f).coerceAtLeast(8.dp).toSp()
            }
            Text(
                text = title,
                color = skin.text,
                fontWeight = FontWeight.Black,
                fontSize = titleSize,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle.uppercase(),
                    color = skin.text.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    fontSize = subtitleSize,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
