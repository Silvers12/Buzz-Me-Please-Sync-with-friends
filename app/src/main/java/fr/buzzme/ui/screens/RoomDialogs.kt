package fr.buzzme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.buzzme.model.GameMode
import fr.buzzme.model.Player
import fr.buzzme.model.RoomOptions
import fr.buzzme.model.RoomState
import fr.buzzme.ui.components.GhostAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.theme.Stage

/** Tout ce que l'animateur peut faire à un joueur, au même endroit. */
@Composable
fun PlayerActionsDialog(
    player: Player,
    isHost: Boolean,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onToggleStatus: () -> Unit,
    onPoints: (Int) -> Unit,
    onTransferHost: () -> Unit,
    onKick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        titleContentColor = Stage.TextPrimary,
        textContentColor = Stage.TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, style = MaterialTheme.typography.headlineMedium, color = Stage.TextPrimary)
                if (isHost) {
                    Spacer(Modifier.width(8.dp))
                    Text("★", color = Stage.Gold, style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        text = {
            // Un petit écran (ou une police système agrandie) ne doit pas rogner le bas du
            // pupitre : le contenu défile plutôt que de déborder.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("Score")
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScoreButton("−1", Stage.Red) { onPoints(-1) }
                    Text(
                        text = player.score.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = Stage.GoldSoft,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    ScoreButton("+1", Stage.Green) { onPoints(1) }
                    ScoreButton("+3", Stage.Green) { onPoints(3) }
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("Buzzer")
                Spacer(Modifier.height(8.dp))
                GhostAction(
                    text = if (player.isEliminated) "Réactiver le buzzer" else "Éliminer (buzzer noir)",
                    icon = if (player.isEliminated) Icons.Filled.CheckCircle else Icons.Filled.Block,
                    onClick = onToggleStatus,
                    accent = if (player.isEliminated) Stage.Green else Stage.Amber,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isMe) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Salon")
                    Spacer(Modifier.height(8.dp))
                    GhostAction(
                        text = "Passer l'animation",
                        icon = Icons.Filled.Star,
                        onClick = onTransferHost,
                        accent = Stage.Gold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostAction(
                        text = "Exclure du salon",
                        icon = Icons.Filled.PersonRemove,
                        onClick = onKick,
                        accent = Stage.Red,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = Stage.VioletSoft)
            }
        },
    )
}

@Composable
private fun ScoreButton(label: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black)
    }
}

/** Réglages de la manche, côté animateur. */
@Composable
fun RoomOptionsDialog(
    state: RoomState,
    amHost: Boolean,
    onDismiss: () -> Unit,
    onOptions: (RoomOptions) -> Unit,
    onResetScores: () -> Unit,
) {
    val options = state.options
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Règles de la partie", style = MaterialTheme.typography.headlineMedium, color = Stage.TextPrimary)
        },
        text = {
            // Un petit écran (ou une police système agrandie) ne doit pas rogner le bas du
            // pupitre : le contenu défile plutôt que de déborder.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OptionSwitch(
                    title = "Mode duel",
                    subtitle = "Le premier qui buzze verrouille tous les autres. " +
                        "Désactivé, tout le monde buzze et l'on obtient un classement complet.",
                    checked = options.mode == GameMode.DUEL,
                    enabled = amHost,
                    onCheckedChange = { duel ->
                        onOptions(options.copy(mode = if (duel) GameMode.DUEL else GameMode.COURSE))
                    },
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = "Décompte 3 · 2 · 1",
                    subtitle = "Tous les appareils s'arment au même instant, à la milliseconde près.",
                    checked = options.countdown,
                    enabled = amHost,
                    onCheckedChange = { onOptions(options.copy(countdown = it)) },
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = "Sons et vibrations",
                    subtitle = "Bips du décompte, top de départ et retour au buzz.",
                    checked = options.sound,
                    enabled = amHost,
                    onCheckedChange = { onOptions(options.copy(sound = it)) },
                )

                if (amHost) {
                    Spacer(Modifier.height(20.dp))
                    GhostAction(
                        text = "Remettre les scores à zéro",
                        onClick = onResetScores,
                        accent = Stage.Red,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = Stage.VioletSoft)
            }
        },
    )
}

@Composable
fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Stage.TextPrimary else Stage.TextMuted,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Stage.Violet,
                uncheckedThumbColor = Stage.TextMuted,
                uncheckedTrackColor = Stage.Night,
                uncheckedBorderColor = Stage.Line,
            ),
        )
    }
}
