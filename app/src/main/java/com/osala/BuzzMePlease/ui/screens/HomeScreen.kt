package com.osala.BuzzMePlease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.isWideWindow
import com.osala.BuzzMePlease.ui.components.readableWidth
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

@Composable
fun HomeScreen(
    name: String,
    onNameChange: (String) -> Unit,
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
    val onDraft: (String) -> Unit = {
        draft = it.take(18)
        onNameChange(draft)
    }

    StageBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (isWideWindow(maxWidth, maxHeight)) {
                WideHome(draft, onDraft, ready, onCreate, onJoin, onSettings)
            } else {
                TallHome(draft, onDraft, ready, onCreate, onJoin, onSettings)
            }
        }
    }
}

/**
 * L'accueil du téléphone : une seule colonne, l'enseigne en haut et les commandes en dessous,
 * dans l'ordre où on les rencontre — son pseudo, la façon dont on se connecte, puis le départ.
 */
@Composable
private fun TallHome(
    draft: String,
    onDraft: (String) -> Unit,
    ready: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsRow(onSettings)

        Spacer(Modifier.height(12.dp))

        Marquee(
            horizontalAlignment = Alignment.CenterHorizontally,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        NicknamePanel(draft = draft, onDraft = onDraft)

        Spacer(Modifier.height(16.dp))

        ConnectionPanel()

        Spacer(Modifier.height(28.dp))

        StartActions(ready = ready, onCreate = onCreate, onJoin = onJoin)

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * L'accueil d'une tablette posée en paysage : l'enseigne et le rappel du Wi-Fi tiennent la
 * moitié gauche, ce sur quoi on agit — le pseudo, puis les deux boutons — la moitié droite.
 * Une seule colonne au milieu d'un écran de dix pouces laissait les deux tiers en vide.
 */
@Composable
private fun WideHome(
    draft: String,
    onDraft: (String) -> Unit,
    ready: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        SettingsRow(onSettings)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // L'enseigne, seule et centrée dans sa moitié : c'est l'affiche du jeu, elle occupe
            // son panneau comme un titre de générique, pas comme une colonne de texte.
            Marquee(
                horizontalAlignment = Alignment.CenterHorizontally,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            // Tout ce qu'on renseigne et sur quoi on appuie, dans l'ordre du téléphone.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                NicknamePanel(draft = draft, onDraft = onDraft)
                Spacer(Modifier.height(16.dp))
                ConnectionPanel()
                Spacer(Modifier.height(28.dp))
                StartActions(ready = ready, onCreate = onCreate, onJoin = onJoin)
            }
        }
    }
}

/** Les réglages, toujours au même coin de l'écran. */
@Composable
private fun SettingsRow(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = Stage.TextSecondary,
            )
        }
    }
}

/** L'enseigne : le titre en deux temps, comme un générique — le nom en blanc, la chute en or. */
@Composable
private fun Marquee(
    horizontalAlignment: Alignment.Horizontal,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
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
            textAlign = textAlign,
        )
    }
}

/** Le pseudo, celui qui s'affichera sur le plateau. */
@Composable
private fun NicknamePanel(draft: String, onDraft: (String) -> Unit) {
    StagePanel(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_nickname_label))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
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
}

/** Par où les téléphones se parlent : le Wi-Fi local, et rien d'autre. */
@Composable
private fun ConnectionPanel() {
    StagePanel(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_connection_label))
        Spacer(Modifier.height(12.dp))
        WifiCard(
            title = stringResource(R.string.home_wifi_title),
            subtitle = stringResource(R.string.home_wifi_subtitle),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.home_wifi_only),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
    }
}

/** Créer ou rejoindre — et, tant qu'il manque un pseudo, la raison de l'attente. */
@Composable
private fun ColumnScope.StartActions(ready: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    PrimaryAction(
        text = stringResource(R.string.home_create_room),
        icon = Icons.Filled.Add,
        enabled = ready,
        onClick = onCreate,
        colors = listOf(Stage.Gold, Stage.GoldDeep),
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
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Rappel de la manière dont les téléphones se parlent : le Wi-Fi local, et rien d'autre. */
@Composable
private fun WifiCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(Stage.Violet.copy(alpha = 0.20f), Stage.Night)),
                shape,
            )
            .border(1.dp, Stage.Violet.copy(alpha = 0.8f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Wifi,
            contentDescription = null,
            tint = Stage.VioletSoft,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Stage.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )
        }
    }
}
