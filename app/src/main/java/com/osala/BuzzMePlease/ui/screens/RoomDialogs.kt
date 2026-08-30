package com.osala.BuzzMePlease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.model.AlertKind
import com.osala.BuzzMePlease.model.GameMode
import com.osala.BuzzMePlease.model.Player
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.theme.Stage

/** Tout ce que l'animateur peut faire à un joueur, au même endroit. */
@Composable
fun PlayerActionsDialog(
    player: Player,
    isHost: Boolean,
    isMe: Boolean,
    /** Faux quand il a déjà la parole, ou qu'il est éliminé : l'action n'aurait rien à faire. */
    canGiveFloor: Boolean,
    onDismiss: () -> Unit,
    onGiveFloor: () -> Unit,
    onToggleStatus: () -> Unit,
    onPoints: (Int) -> Unit,
    onAlert: (AlertKind) -> Unit,
    onClearCards: () -> Unit,
    onTransferHost: () -> Unit,
    onKick: () -> Unit,
) {
    // Passer l'animation change de main le pupitre entier et ne se rattrape que si le nouvel
    // animateur veut bien vous le rendre : jamais sur un appui isolé, au milieu d'une partie.
    var confirmTransfer by remember(player.id) { mutableStateOf(false) }
    var pickAlert by remember(player.id) { mutableStateOf(false) }

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
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.player_host_desc),
                        tint = Stage.Gold,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        text = {
            // Un petit écran (ou une police système agrandie) ne doit pas rogner le bas du
            // pupitre : le contenu défile plutôt que de déborder.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // En tête, parce que c'est le geste qui presse : la question est posée, il
                // faut désigner qui répond. Le classement propose, l'animateur dispose.
                if (canGiveFloor) {
                    SectionLabel(stringResource(R.string.dialog_floor))
                    Spacer(Modifier.height(8.dp))
                    GhostAction(
                        text = stringResource(R.string.dialog_give_floor),
                        icon = Icons.Filled.RecordVoiceOver,
                        onClick = onGiveFloor,
                        accent = Stage.Gold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                SectionLabel(stringResource(R.string.dialog_score))
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
                SectionLabel(stringResource(R.string.dialog_buzzer))
                Spacer(Modifier.height(8.dp))
                GhostAction(
                    text = stringResource(
                        if (player.isEliminated) R.string.dialog_revive else R.string.dialog_eliminate,
                    ),
                    icon = if (player.isEliminated) Icons.Filled.CheckCircle else Icons.Filled.Block,
                    onClick = onToggleStatus,
                    accent = if (player.isEliminated) Stage.Green else Stage.Amber,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Le carton s'adresse à quelqu'un d'autre : on ne s'avertit pas soi-même.
                // L'ardoise, elle, s'efface même sur sa propre ligne : un joueur qui a pris
                // des cartons puis reçu l'animation les garderait sinon jusqu'à la fin.
                val marked = player.yellowCards > 0 || player.redCards > 0
                if (!isMe || marked) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(stringResource(R.string.dialog_alert))
                    Spacer(Modifier.height(8.dp))
                    if (!isMe) {
                        GhostAction(
                            text = stringResource(R.string.alert_send),
                            icon = Icons.Filled.Campaign,
                            onClick = { pickAlert = true },
                            accent = Stage.Amber,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (marked) {
                        if (!isMe) Spacer(Modifier.height(8.dp))
                        val tally = listOfNotNull(
                            player.yellowCards.takeIf { it > 0 }?.let {
                                pluralStringResource(R.plurals.alert_clear_yellow, it, it)
                            },
                            player.redCards.takeIf { it > 0 }?.let {
                                pluralStringResource(R.plurals.alert_clear_red, it, it)
                            },
                        ).joinToString(", ")
                        GhostAction(
                            text = stringResource(R.string.alert_clear, tally),
                            icon = Icons.Filled.Backspace,
                            onClick = onClearCards,
                            accent = Stage.VioletSoft,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (!isMe) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(stringResource(R.string.dialog_room))
                    Spacer(Modifier.height(8.dp))
                    GhostAction(
                        text = stringResource(R.string.dialog_transfer),
                        icon = Icons.Filled.Mic,
                        onClick = { confirmTransfer = true },
                        accent = Stage.Gold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostAction(
                        text = stringResource(R.string.dialog_kick),
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
Text(stringResource(R.string.action_close), color = Stage.VioletSoft)
            }
        },
    )

    if (pickAlert) {
        AlertPickerDialog(
            onPick = { kind ->
                pickAlert = false
                onAlert(kind)
            },
            onDismiss = { pickAlert = false },
        )
    }

    if (confirmTransfer) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_transfer_title),
            message = stringResource(R.string.dialog_transfer_body, player.name),
            confirmLabel = stringResource(R.string.dialog_transfer),
            accent = Stage.Gold,
            onConfirm = {
                confirmTransfer = false
                onTransferHost()
            },
            onDismiss = { confirmTransfer = false },
        )
    }
}

/** Garde-fou devant une action qui ne se rattrape pas d'un simple appui. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    accent: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Stage.TextPrimary)
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Stage.TextSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Stage.TextSecondary)
            }
        },
    )
}

/**
 * Le choix de l'alerte à envoyer. Une liste plutôt que deux boutons posés dans la fiche du
 * joueur : d'autres cartons viendront, et ils s'ajouteront ici sans la surcharger.
 */
@Composable
private fun AlertPickerDialog(onPick: (AlertKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                stringResource(R.string.alert_pick_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Stage.TextPrimary,
            )
        },
        text = {
            Column {
                GhostAction(
                    text = stringResource(R.string.alert_yellow),
                    icon = Icons.Filled.Warning,
                    onClick = { onPick(AlertKind.YELLOW_CARD) },
                    accent = Stage.Amber,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GhostAction(
                    text = stringResource(R.string.alert_red),
                    icon = Icons.Filled.Dangerous,
                    onClick = { onPick(AlertKind.RED_CARD) },
                    accent = Stage.Red,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Stage.TextSecondary)
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
    onEndGame: () -> Unit,
) {
    val options = state.options
    // Le résultat part sur tous les téléphones d'un coup : on demande avant, comme pour la
    // passation. Un appui de trop ne doit pas proclamer un vainqueur au milieu d'une manche.
    var confirmEnd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                stringResource(R.string.rules_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Stage.TextPrimary,
            )
        },
        text = {
            // Un petit écran (ou une police système agrandie) ne doit pas rogner le bas du
            // pupitre : le contenu défile plutôt que de déborder.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OptionSwitch(
                    title = stringResource(R.string.rules_duel_title),
                    subtitle = stringResource(R.string.rules_duel_subtitle),
                    checked = options.mode == GameMode.DUEL,
                    enabled = amHost,
                    onCheckedChange = { duel ->
                        onOptions(options.copy(mode = if (duel) GameMode.DUEL else GameMode.COURSE))
                    },
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = stringResource(R.string.rules_countdown_title),
                    subtitle = stringResource(R.string.rules_countdown_subtitle),
                    checked = options.countdown,
                    enabled = amHost,
                    onCheckedChange = { onOptions(options.copy(countdown = it)) },
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = stringResource(R.string.rules_hide_board_title),
                    subtitle = stringResource(R.string.rules_hide_board_subtitle),
                    checked = options.hideBoard,
                    enabled = amHost,
                    onCheckedChange = { onOptions(options.copy(hideBoard = it)) },
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = stringResource(R.string.rules_sound_title),
                    subtitle = stringResource(R.string.rules_sound_subtitle),
                    checked = options.sound,
                    enabled = amHost,
                    onCheckedChange = { onOptions(options.copy(sound = it)) },
                )

                if (amHost) {
                    Spacer(Modifier.height(20.dp))
                    GhostAction(
                        text = stringResource(R.string.rules_end_game),
                        icon = Icons.Filled.EmojiEvents,
                        onClick = { confirmEnd = true },
                        accent = Stage.Gold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostAction(
                        text = stringResource(R.string.rules_reset_scores),
                        onClick = onResetScores,
                        accent = Stage.Red,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
Text(stringResource(R.string.action_close), color = Stage.VioletSoft)
            }
        },
    )

    if (confirmEnd) {
        ConfirmDialog(
            title = stringResource(R.string.rules_end_game_title),
            message = stringResource(R.string.rules_end_game_body),
            confirmLabel = stringResource(R.string.rules_end_game),
            accent = Stage.Gold,
            onConfirm = {
                confirmEnd = false
                onEndGame()
            },
            onDismiss = { confirmEnd = false },
        )
    }
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
            // Toute la ligne bascule l'option : viser la pastille de l'interrupteur, sur un
            // téléphone tenu à bout de bras pendant une partie, c'est une cible bien trop fine.
            // Au passage, le titre devient le libellé annoncé par les lecteurs d'écran.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 6.dp),
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
            // La ligne entière porte déjà le geste : l'interrupteur ne le capte pas une seconde fois.
            onCheckedChange = null,
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
