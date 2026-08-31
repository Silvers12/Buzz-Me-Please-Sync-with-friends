package com.osala.BuzzMePlease.ui.components

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.osala.BuzzMePlease.model.BuzzerVisual
import com.osala.BuzzMePlease.ui.theme.Stage
import kotlinx.coroutines.launch

/**
 * Le buzzer en miniature, sans texte ni geste : de quoi montrer une couleur pour ce qu'elle est.
 *
 * Le tutoriel s'en sert pour sa légende — expliquer « le vert veut dire ceci » sans le vert sous
 * les yeux demanderait au joueur de croire sur parole. Mêmes teintes, même dôme, même socle que
 * le vrai bouton : ce sont les mêmes [BuzzerSkin] qui servent aux deux.
 */
@Composable
fun BuzzerSample(visual: BuzzerVisual, modifier: Modifier = Modifier) {
    val skin = skinFor(visual)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val radius = size.minDimension / 2f
        val c = center

        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3A3A55), Color(0xFF15151F)),
                startY = c.y - radius,
                endY = c.y + radius,
            ),
            radius = radius,
            center = c,
        )
        drawCircle(
            color = skin.ring.copy(alpha = 0.6f),
            radius = radius,
            center = c,
            style = Stroke(width = radius * 0.07f),
        )

        val domeRadius = radius * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(skin.highlight, skin.body, skin.shadow),
                center = Offset(c.x - domeRadius * 0.32f, c.y - domeRadius * 0.36f),
                radius = domeRadius * 1.45f,
            ),
            radius = domeRadius,
            center = c,
        )
    }
}

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

    // Le buzz est parti, le verdict se joue : bleu franc, ni promesse ni sanction.
    BuzzerVisual.BUZZED -> BuzzerSkin(
        highlight = Color(0xFFA8D4FF),
        body = Stage.Blue,
        shadow = Stage.BlueDeep,
        ring = Color(0xFF6BB3FF),
        glow = Stage.Blue,
        text = Color(0xFF031F42),
    )

    BuzzerVisual.COUNTDOWN -> BuzzerSkin(
        highlight = Color(0xFFFFD79A),
        body = Stage.Amber,
        shadow = Color(0xFF7A4300),
        ring = Color(0xFFFFC061),
        glow = Stage.Amber,
        text = Color(0xFF3A2000),
    )

    // La parole : le blanc, seule couleur qui ne serve à rien d'autre. Sur un fond de nuit, un
    // dôme blanc ne se confond avec aucun autre état et se voit à l'autre bout de la table.
    BuzzerVisual.SPEAKING -> BuzzerSkin(
        highlight = Color(0xFFFFFFFF),
        body = Color(0xFFF1F3FF),
        shadow = Color(0xFF8E93AE),
        ring = Color(0xFFFFFFFF),
        glow = Color(0xFFE4E9FF),
        text = Color(0xFF10101C),
    )

    // Bonne réponse : le vert du jeu revient, plus clair encore que celui des buzzers ouverts.
    // La manche se referme sur la couleur qui l'a ouverte.
    BuzzerVisual.RIGHT -> BuzzerSkin(
        highlight = Color(0xFFD6FFE8),
        body = Stage.Green,
        shadow = Stage.GreenDeep,
        ring = Color(0xFF7BFFC0),
        glow = Stage.Green,
        text = Color(0xFF05310F),
    )

    // Mauvaise réponse : l'animateur retire la parole. Le seul rouge du jeu, il ne doit
    // signifier que cela.
    BuzzerVisual.WRONG -> BuzzerSkin(
        highlight = Color(0xFFFF9AAC),
        body = Stage.Red,
        shadow = Stage.RedDeep,
        ring = Color(0xFFFF5E78),
        glow = Stage.Red,
        text = Color(0xFF3B0009),
    )

    // La manche est prise par quelqu'un d'autre : bleu éteint, on regarde sans jouer.
    BuzzerVisual.LOST -> BuzzerSkin(
        highlight = Color(0xFF3F5F88),
        body = Color(0xFF27405E),
        shadow = Color(0xFF0E1B2A),
        ring = Color(0xFF375A83),
        glow = Color(0xFF1B3350),
        text = Color(0xFF9FB6D2),
    )

    // Buzzer désactivé : gris, sans la moindre teinte, il ne participe plus.
    BuzzerVisual.ELIMINATED -> BuzzerSkin(
        highlight = Color(0xFF52525C),
        body = Color(0xFF3A3A42),
        shadow = Color(0xFF17171B),
        ring = Color(0xFF4A4A54),
        glow = Color(0xFF26262C),
        text = Color(0xFF9A9AA6),
    )

    // Au repos, entre deux manches.
    BuzzerVisual.OFF -> BuzzerSkin(
        highlight = Color(0xFF4E74A6),
        body = Color(0xFF2F4C74),
        shadow = Color(0xFF122135),
        ring = Color(0xFF456C9E),
        glow = Color(0xFF23405F),
        text = Color(0xFFD3E4FA),
    )
}

/**
 * Le buzzer.
 *
 * Point clé : l'horodatage n'est pas pris au moment où l'on réagit à l'événement, mais lu sur
 * l'événement tactile lui-même ([androidx.compose.ui.input.pointer.PointerInputChange.uptimeMillis]).
 * Le temps de recomposition, de rendu et d'ordonnancement ne se retrouve donc pas dans le
 * chrono du joueur : on mesure l'appui, pas la charge du téléphone.
 *
 * @param onPress vrai si l'appui a été pris, faux s'il tombe sur un buzzer fermé. C'est ce qui
 *   décide de l'onde de choc : elle appartient au geste, comme le son.
 */
@Composable
fun BigBuzzer(
    visual: BuzzerVisual,
    title: String,
    subtitle: String,
    onPress: (uptimeMillis: Long) -> Boolean,
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
    // Il respire aussi sur la bonne réponse : le vert doit se voir de loin.
    val alive = visual == BuzzerVisual.ARMED ||
        visual == BuzzerVisual.SPEAKING ||
        visual == BuzzerVisual.RIGHT
    val haloBoost = if (alive) 0.55f + breathing * 0.45f else 0.35f

    // Onde de choc. Comme le son, elle part de l'appui lui-même et non du bleu qui s'ensuit :
    // cette couleur n'est pas toujours traversée, et quand elle l'est, pas toujours assez
    // longtemps. En course, le premier qui appuie prend la parole dans la foulée — le buzzer
    // passe du vert au blanc sans s'arrêter au bleu, sur-le-champ chez l'animateur, dont
    // l'appui met à jour l'état dans la même image. L'onde ne partait pas, ou se faisait couper
    // net au changement de couleur : elle n'allait au bout que pour qui n'était pas le premier,
    // exactement l'inverse de ce qu'elle souligne.
    val shockwave = remember { Animatable(0f) }
    val waves = rememberCoroutineScope()
    // Le détecteur de geste ne relance jamais son bloc : le départ de l'onde doit donc rester
    // le même d'une image à l'autre. L'onde s'éteint d'elle-même en fin de course — elle finit
    // à 1, où elle est transparente — et n'a rien à faire effacer derrière elle.
    val strike: () -> Unit = remember(waves, shockwave) {
        {
            waves.launch {
                shockwave.snapTo(0f)
                shockwave.animateTo(1f, tween(520, easing = LinearEasing))
            }
        }
    }

    // La bonne réponse garde la sienne : ce vert-là tient le temps qu'on le savoure, il peut
    // donc encore se lire sur la couleur. Elle emprunte le même départ, et non la coroutine de
    // l'effet : celle-ci serait annulée à la couleur suivante, laissant l'anneau figé en route.
    LaunchedEffect(visual) {
        if (visual == BuzzerVisual.RIGHT) strike()
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    if (currentOnPress(down.uptimeMillis)) strike()
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
