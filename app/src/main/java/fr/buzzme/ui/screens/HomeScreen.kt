package fr.buzzme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.buzzme.core.Transport
import fr.buzzme.ui.components.PrimaryAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.components.StageBackground
import fr.buzzme.ui.components.StagePanel
import fr.buzzme.ui.theme.Stage

@Composable
fun HomeScreen(
    name: String,
    transport: Transport,
    onNameChange: (String) -> Unit,
    onTransportChange: (Transport) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSettings: () -> Unit,
) {
    var draft by remember { mutableStateOf(name) }
    // Les réglages arrivent de façon asynchrone : on ne rattrape la valeur enregistrée que
    // tant que l'utilisateur n'a rien saisi, sinon la frappe se ferait écraser en cours de route.
    LaunchedEffect(name) {
        if (draft.isBlank() && name.isNotBlank()) draft = name
    }
    val ready = draft.trim().length >= 2

    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = Stage.TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "BUZZ",
                style = MaterialTheme.typography.displayLarge,
                color = Stage.TextPrimary,
            )
            Text(
                text = "ME",
                style = MaterialTheme.typography.displayLarge,
                color = Stage.Gold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Le buzzer de quiz, à la milliseconde",
                style = MaterialTheme.typography.bodyLarge,
                color = Stage.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Votre pseudo")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(18)
                        onNameChange(draft)
                    },
                    singleLine = true,
                    placeholder = { Text("Ex. Camille") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = MaterialTheme.typography.titleLarge,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Stage.Violet,
                        unfocusedBorderColor = Stage.Line,
                        focusedTextColor = Stage.TextPrimary,
                        unfocusedTextColor = Stage.TextPrimary,
                        cursorColor = Stage.Gold,
                        focusedContainerColor = Stage.Night,
                        unfocusedContainerColor = Stage.Night,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Connexion entre joueurs")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TransportChoice(
                        title = "Wi-Fi local",
                        subtitle = "Sans Internet · le plus précis",
                        selected = transport == Transport.LOCAL,
                        onClick = { onTransportChange(Transport.LOCAL) },
                        modifier = Modifier.weight(1f),
                        icon = { tint -> Icon(Icons.Filled.Wifi, null, tint = tint) },
                    )
                    TransportChoice(
                        title = "En ligne",
                        subtitle = "Firebase · à distance",
                        selected = transport == Transport.ONLINE,
                        onClick = { onTransportChange(Transport.ONLINE) },
                        modifier = Modifier.weight(1f),
                        icon = { tint -> Icon(Icons.Filled.CloudQueue, null, tint = tint) },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            PrimaryAction(
                text = "Créer un salon",
                icon = Icons.Filled.Add,
                enabled = ready,
                onClick = onCreate,
                colors = listOf(Stage.Gold, Color(0xFFE0A21B)),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                text = "Rejoindre avec un code",
                icon = Icons.Filled.Login,
                enabled = ready,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Choisissez d'abord un pseudo (2 caractères minimum).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TransportChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) Stage.Violet else Stage.Line
    val tint = if (selected) Stage.VioletSoft else Stage.TextMuted
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(Stage.Violet.copy(alpha = 0.20f), Stage.Night))
                } else {
                    Brush.verticalGradient(listOf(Stage.Night, Stage.Night))
                },
                shape,
            )
            .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.5f), shape)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Box(modifier = Modifier.size(24.dp)) { icon(tint) }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Stage.TextPrimary else Stage.TextSecondary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
    }
}

/** Petit rappel visuel utilisé sur plusieurs écrans. */
@Composable
fun HintLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Stage.TextMuted,
        modifier = modifier,
    )
}
