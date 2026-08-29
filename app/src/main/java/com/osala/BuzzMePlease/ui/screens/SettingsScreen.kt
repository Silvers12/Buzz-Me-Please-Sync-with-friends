package com.osala.BuzzMePlease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.core.Features
import com.osala.BuzzMePlease.net.online.FirebaseConfig
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StageBadge
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

@Composable
fun SettingsScreen(
    firebase: FirebaseConfig,
    sound: Boolean,
    keepScreenOn: Boolean,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onSound: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFirebase: (FirebaseConfig) -> Unit,
    onTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Stage.TextSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Stage.TextPrimary,
                )
            }

            Spacer(Modifier.height(20.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.settings_language_label))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { choice ->
                        LanguageChoice(
                            label = stringResource(
                                when (choice) {
                                    AppLanguage.SYSTEM -> R.string.settings_language_auto
                                    AppLanguage.FRENCH -> R.string.settings_language_fr
                                    AppLanguage.ENGLISH -> R.string.settings_language_en
                                },
                            ),
                            selected = choice == language,
                            onClick = { onLanguage(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.settings_comfort_label))
                Spacer(Modifier.height(12.dp))
                OptionSwitch(
                    title = stringResource(R.string.settings_sound_title),
                    subtitle = stringResource(R.string.settings_sound_subtitle),
                    checked = sound,
                    onCheckedChange = onSound,
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = stringResource(R.string.settings_screen_title),
                    subtitle = stringResource(R.string.settings_screen_subtitle),
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
            }

            Spacer(Modifier.height(16.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.settings_howto_label), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_howto_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                GhostAction(
                    text = stringResource(R.string.settings_open_tutorial),
                    icon = Icons.Filled.Info,
                    onClick = onTutorial,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (Features.ONLINE_ROOMS) {
                Spacer(Modifier.height(16.dp))
                FirebasePanel(firebase = firebase, onFirebase = onFirebase)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Configuration du projet Firebase, réservée au salon en ligne. */
@Composable
private fun FirebasePanel(firebase: FirebaseConfig, onFirebase: (FirebaseConfig) -> Unit) {
    var draft by remember { mutableStateOf(firebase) }
    var saved by remember { mutableStateOf(false) }

    StagePanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.settings_firebase_label), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StageBadge(
                text = stringResource(
                    if (draft.isComplete) R.string.settings_firebase_ready else R.string.settings_firebase_missing,
                ),
                color = if (draft.isComplete) Stage.Green else Stage.Amber,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_firebase_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
        Spacer(Modifier.height(14.dp))

        ConfigField(
            label = stringResource(R.string.settings_firebase_project),
            value = draft.projectId,
            placeholder = "buzzme-quiz",
        ) { draft = draft.copy(projectId = it); saved = false }

        ConfigField(
            label = stringResource(R.string.settings_firebase_app),
            value = draft.applicationId,
            placeholder = "1:1234567890:android:abcdef",
        ) { draft = draft.copy(applicationId = it); saved = false }

        ConfigField(
            label = stringResource(R.string.settings_firebase_key),
            value = draft.apiKey,
            placeholder = "AIza…",
        ) { draft = draft.copy(apiKey = it); saved = false }

        ConfigField(
            label = stringResource(R.string.settings_firebase_url),
            value = draft.databaseUrl,
            placeholder = "https://…firebasedatabase.app",
            keyboardType = KeyboardType.Uri,
        ) { draft = draft.copy(databaseUrl = it.trim()); saved = false }

        Spacer(Modifier.height(6.dp))
        PrimaryAction(
            text = stringResource(if (saved) R.string.settings_saved else R.string.settings_save),
            icon = Icons.Filled.Save,
            enabled = draft.isComplete && !saved,
            onClick = {
                onFirebase(draft)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Stage.TextSecondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = Stage.TextMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
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

/** Une des trois langues possibles : système, français, anglais. */
@Composable
private fun LanguageChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) Stage.Violet else Stage.Line
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .background(
                if (selected) Stage.Violet.copy(alpha = 0.18f) else Stage.Night,
                shape,
            )
            .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.5f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Stage.TextPrimary else Stage.TextSecondary,
            maxLines = 1,
        )
    }
}
