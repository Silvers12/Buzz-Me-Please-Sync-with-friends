package fr.buzzme.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.buzzme.model.Buzz
import fr.buzzme.model.BuzzerVisual
import fr.buzzme.model.Player
import fr.buzzme.ui.theme.MonoDigits
import fr.buzzme.ui.theme.Stage

private fun accentFor(visual: BuzzerVisual): Color = when (visual) {
    BuzzerVisual.ARMED -> Stage.Green
    BuzzerVisual.BUZZED -> Stage.Red
    BuzzerVisual.COUNTDOWN -> Stage.Amber
    BuzzerVisual.LOST -> Color(0xFF454568)
    BuzzerVisual.ELIMINATED -> Color(0xFF2A2A38)
    BuzzerVisual.OFF -> Color(0xFF4A4A70)
}

/**
 * Une ligne de plateau : voyant du buzzer, pseudo, heure exacte du buzz et écart avec le meilleur.
 * C'est la vue de l'animateur — elle doit se lire d'un coup d'œil pendant la partie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerRow(
    player: Player,
    visual: BuzzerVisual,
    buzz: Buzz?,
    gapMillis: Long?,
    rank: Int,
    isWinner: Boolean,
    isHost: Boolean,
    isMe: Boolean,
    showControls: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Faux quand l'animateur a masqué les scores : la pastille reste, le chiffre disparaît. */
    showScore: Boolean = true,
) {
    val accent = accentFor(visual)
    val animatedAccent by animateColorAsState(accent, label = "accent")
    val shape = RoundedCornerShape(18.dp)
    val highlight = if (isWinner) Stage.Gold else animatedAccent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        highlight.copy(alpha = if (isWinner) 0.20f else 0.10f),
                        Stage.Panel.copy(alpha = 0.9f),
                    ),
                ),
                shape,
            )
            .border(1.dp, highlight.copy(alpha = if (isWinner) 0.75f else 0.30f), shape)
            .then(if (showControls) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Voyant du buzzer, reprise exacte des couleurs du gros bouton.
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    Brush.radialGradient(listOf(animatedAccent, animatedAccent.copy(alpha = 0.35f))),
                    CircleShape,
                )
                .border(2.dp, animatedAccent.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (rank in 1..3 && buzz != null) {
                Text(
                    text = rank.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (player.isEliminated) Stage.TextMuted else Stage.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHost) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Animateur",
                        tint = Stage.Gold,
                        modifier = Modifier.size(15.dp),
                    )
                }
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text("VOUS", style = MaterialTheme.typography.labelMedium, color = Stage.VioletSoft)
                }
            }

            Spacer(Modifier.height(3.dp))

            when {
                player.isEliminated -> Text(
                    "Éliminé",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )

                // FlowRow et non Row : quand la place manque (police système agrandie, écran
                // étroit), l'écart passe à la ligne entier au lieu d'être coupé en plein milieu.
                buzz != null -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buzz.wallClockText(),
                        style = MonoDigits,
                        color = if (isWinner) Stage.GoldSoft else Stage.TextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = Buzz.formatReaction(buzz.reactionMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Stage.TextSecondary,
                        maxLines = 1,
                    )
                    if (gapMillis != null) {
                        Text(
                            text = Buzz.formatGap(gapMillis),
                            style = MonoDigits,
                            color = Stage.Red.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }

                !player.connected -> Text(
                    "Hors ligne",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.Amber.copy(alpha = 0.8f),
                )

                else -> Text(
                    text = if (player.pingMillis > 0) "Prêt · ${player.pingMillis} ms" else "Prêt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        if (isWinner && buzz != null) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = "Vainqueur de la manche",
                tint = Stage.Gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }

        ScorePill(score = player.score, visible = showScore)
    }
}

@Composable
private fun ScorePill(score: Int, visible: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        !visible -> Stage.TextMuted
        score > 0 -> Stage.Gold
        score < 0 -> Stage.Red
        else -> Stage.TextMuted
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Text(
                text = score.toString(),
                style = MonoDigits,
                color = color,
                fontWeight = FontWeight.Black,
            )
        } else {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = "Score masqué par l'animateur",
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
