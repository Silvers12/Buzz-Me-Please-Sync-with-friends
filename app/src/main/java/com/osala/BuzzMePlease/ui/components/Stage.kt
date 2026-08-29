package com.osala.BuzzMePlease.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osala.BuzzMePlease.ui.theme.Stage

/**
 * Le décor : dégradé de scène, projecteur violet en haut, rampe dorée au sol et vignettage.
 * Le halo respire lentement pour que l'écran ne soit jamais complètement figé.
 */
@Composable
fun StageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "stage")
    val glow by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Stage.Night, Stage.Deep))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spotCenter = Offset(size.width * 0.5f, -size.height * 0.04f)
            val spotRadius = size.width * 1.05f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Stage.Violet.copy(alpha = 0.30f * glow), Color.Transparent),
                    center = spotCenter,
                    radius = spotRadius,
                ),
                radius = spotRadius,
                center = spotCenter,
            )

            val floorCenter = Offset(size.width * 0.5f, size.height * 1.02f)
            val floorRadius = size.width * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Stage.Gold.copy(alpha = 0.14f * glow), Color.Transparent),
                    center = floorCenter,
                    radius = floorRadius,
                ),
                radius = floorRadius,
                center = floorCenter,
            )

            // Vignettage : les bords s'assombrissent, le regard reste au centre du plateau.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Stage.Deep.copy(alpha = 0.85f)),
                    center = center,
                    radius = size.maxDimension * 0.72f,
                ),
                radius = size.maxDimension * 0.72f,
            )
        }
        content()
    }
}

@Composable
fun StagePanel(
    modifier: Modifier = Modifier,
    accent: Color = Stage.Line,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(Stage.Panel, Stage.Night)), shape)
            .border(BorderStroke(1.dp, accent), shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Stage.TextMuted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Le code du salon, affiché comme un afficheur de plateau : une case par lettre, cerclée d'or.
 *
 * Les cases sont taillées à partir de la largeur réellement disponible, jamais d'une valeur
 * fixe : sur un écran étroit, une largeur imposée déborderait de la rangée, et c'est alors la
 * dernière case qui se ferait écraser — d'où des cases inégales et un badge collé au code.
 */
@Composable
fun CodeDisplay(
    code: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val gap = 6.dp
    BoxWithConstraints(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
    ) {
        val count = code.length.coerceAtLeast(1)
        val total = if (constraints.hasBoundedWidth) maxWidth else CODE_CELL_MAX * count
        val cell = ((total - gap * (count - 1)) / count).coerceIn(CODE_CELL_MIN, CODE_CELL_MAX)
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            code.forEach { letter ->
                Box(
                    modifier = Modifier
                        .width(cell)
                        .height(cell * 1.27f)
                        .background(
                            Brush.verticalGradient(listOf(Stage.PanelHigh, Stage.Night)),
                            RoundedCornerShape(12.dp),
                        )
                        .border(1.dp, Stage.Gold.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        color = Stage.GoldSoft,
                        fontWeight = FontWeight.Black,
                        fontSize = (cell.value * 0.62f).sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val CODE_CELL_MIN = 24.dp
private val CODE_CELL_MAX = 44.dp

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = listOf(Stage.Violet, Color(0xFF5B3BD8)),
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Stage.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier
            // heightIn plutôt que height : un libellé long passe à la ligne au lieu de déborder
            // du cadre, et le fond suit puisqu'il est peint sur la taille mesurée.
            .heightIn(min = 58.dp)
            .background(
                brush = if (enabled) {
                    Brush.horizontalGradient(colors)
                } else {
                    Brush.horizontalGradient(listOf(Stage.PanelHigh, Stage.Panel))
                },
                shape = shape,
            ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GhostAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = Stage.VioletSoft,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.55f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
            disabledContentColor = Stage.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Action secondaire réduite à son icône. Les libellés en toutes lettres feraient déborder la
 * rangée de commandes de l'animateur sur un écran étroit, au détriment du bouton « TOP ! ».
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Stage.VioletSoft,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .size(56.dp)
            .background(Stage.Night, shape)
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(22.dp))
    }
}

/** Petite pastille d'état : connecté, éliminé, hôte… */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
    )
}

@Composable
fun StageBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
