package com.osala.BuzzMePlease.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.model.Buzz
import com.osala.BuzzMePlease.model.BuzzerVisual
import com.osala.BuzzMePlease.model.Player
import com.osala.BuzzMePlease.ui.theme.MonoDigits
import com.osala.BuzzMePlease.ui.theme.Stage

// Le voyant de la ligne reprend exactement les couleurs du gros bouton : blanc la parole, vert
// les buzzers ouverts, rouge la mauvaise réponse, gris le buzzer désactivé, bleu tout le reste.
private fun accentFor(visual: BuzzerVisual): Color = when (visual) {
    BuzzerVisual.ARMED -> Stage.Green
    BuzzerVisual.SPEAKING -> Color(0xFFF1F3FF)
    BuzzerVisual.BUZZED -> Stage.Blue
    BuzzerVisual.COUNTDOWN -> Stage.Amber
    BuzzerVisual.RIGHT -> Stage.Green
    BuzzerVisual.WRONG -> Stage.Red
    BuzzerVisual.LOST -> Color(0xFF375A83)
    BuzzerVisual.ELIMINATED -> Color(0xFF4A4A54)
    BuzzerVisual.OFF -> Color(0xFF456C9E)
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
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.player_host_desc),
                        tint = Stage.Gold,
                        modifier = Modifier.size(15.dp),
                    )
                }
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.player_you),
                        style = MaterialTheme.typography.labelMedium,
                        color = Stage.VioletSoft,
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            when {
                player.isEliminated -> Text(
                    stringResource(R.string.player_out),
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
                    stringResource(R.string.player_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.Amber.copy(alpha = 0.8f),
                )

                else -> Text(
                    text = if (player.pingMillis > 0) {
                        stringResource(R.string.player_ready_ping, player.pingMillis)
                    } else {
                        stringResource(R.string.player_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Les cartons reçus, juste avant le score. Rien ne s'affiche tant qu'il n'y en a pas :
        // un plateau de zéros ne dit rien que l'absence ne dise déjà.
        if (player.yellowCards > 0) {
            CardTally(player.yellowCards, Stage.Amber, stringResource(R.string.player_yellow_cards, player.yellowCards))
            Spacer(Modifier.width(6.dp))
        }
        if (player.redCards > 0) {
            CardTally(player.redCards, Stage.Red, stringResource(R.string.player_red_cards, player.redCards))
            Spacer(Modifier.width(6.dp))
        }

        if (isWinner && buzz != null) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = stringResource(R.string.player_winner_desc),
                tint = Stage.Gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }

        ScorePill(score = player.score)
    }
}

/** Un carton d'arbitre, avec son compte dedans : la forme se reconnaît sans lire. */
@Composable
private fun CardTally(count: Int, color: Color, description: String) {
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            // La boîte suit son contenu : à deux chiffres, ou avec la police système
            // agrandie, une taille figée rognerait le compte.
            .defaultMinSize(minWidth = 16.dp, minHeight = 21.dp)
            .background(color.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Stage.Night,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ScorePill(score: Int, modifier: Modifier = Modifier) {
    val color = when {
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
        Text(
            text = score.toString(),
            style = MonoDigits,
            color = color,
            fontWeight = FontWeight.Black,
        )
    }
}
