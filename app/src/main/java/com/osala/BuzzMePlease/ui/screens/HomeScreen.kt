package com.osala.BuzzMePlease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.Features
import com.osala.BuzzMePlease.core.Transport
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

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
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings), tint = Stage.TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Le titre en deux temps, comme un générique : le nom en blanc, la chute en or.
            Text(
                text = "BUZZ ME",
                style = MaterialTheme.typography.displayLarge,
                color = Stage.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = "PLEASE",
                style = MaterialTheme.typography.displayLarge,
                color = Stage.Gold,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Stage.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.home_nickname_label))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(18)
                        onNameChange(draft)
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.home_nickname_hint)) },
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
                SectionLabel(stringResource(R.string.home_connection_label))
                Spacer(Modifier.height(12.dp))
                if (Features.ONLINE_ROOMS) {
                    // height(IntrinsicSize.Min) + fillMaxHeight : les deux cartes gardent la même
                    // hauteur même quand un sous-titre passe sur deux lignes.
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TransportChoice(
                            title = stringResource(R.string.home_wifi_title),
                            subtitle = stringResource(R.string.home_wifi_subtitle),
                            selected = transport == Transport.LOCAL,
                            onClick = { onTransportChange(Transport.LOCAL) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = { tint -> Icon(Icons.Filled.Wifi, null, tint = tint) },
                        )
                        TransportChoice(
                            title = stringResource(R.string.home_online_title),
                            subtitle = stringResource(R.string.home_online_subtitle),
                            selected = transport == Transport.ONLINE,
                            onClick = { onTransportChange(Transport.ONLINE) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = { tint -> Icon(Icons.Filled.CloudQueue, null, tint = tint) },
                        )
                    }
                } else {
                    TransportChoice(
                        title = stringResource(R.string.home_wifi_title),
                        subtitle = stringResource(R.string.home_wifi_subtitle),
                        selected = true,
                        onClick = null,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { tint -> Icon(Icons.Filled.Wifi, null, tint = tint) },
                        wide = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.home_wifi_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Stage.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            PrimaryAction(
                text = stringResource(R.string.home_create_room),
                icon = Icons.Filled.Add,
                enabled = ready,
                onClick = onCreate,
                colors = listOf(Stage.Gold, Color(0xFFE0A21B)),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                text = stringResource(R.string.home_join_room),
                icon = Icons.AutoMirrored.Filled.Login,
                enabled = ready,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.home_need_nickname),
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
    onClick: (() -> Unit)?,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    /** Carte seule occupant toute la largeur : icône et texte côte à côte plutôt qu'empilés. */
    wide: Boolean = false,
) {
    val accent = if (selected) Stage.Violet else Stage.Line
    val tint = if (selected) Stage.VioletSoft else Stage.TextMuted
    val shape = RoundedCornerShape(16.dp)
    val frame = modifier
        .background(
            if (selected) {
                Brush.verticalGradient(listOf(Stage.Violet.copy(alpha = 0.20f), Stage.Night))
            } else {
                Brush.verticalGradient(listOf(Stage.Night, Stage.Night))
            },
            shape,
        )
        .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.5f), shape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(14.dp)

    val labels: @Composable ColumnScope.() -> Unit = {
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

    if (wide) {
        Row(modifier = frame, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp)) { icon(tint) }
            Spacer(Modifier.width(14.dp))
            Column(content = labels)
        }
    } else {
        Column(modifier = frame) {
            Box(modifier = Modifier.size(24.dp)) { icon(tint) }
            Spacer(Modifier.height(8.dp))
            labels()
        }
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
