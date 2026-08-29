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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StageBadge
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

@Composable
fun SettingsScreen(
    sound: Boolean,
    keepScreenOn: Boolean,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onSound: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
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

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_version, rememberVersionName()),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )
            Text(
                stringResource(R.string.settings_copyright),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}


/**
 * Le numéro de version tel qu'il est installé, lu sur le paquet : rien à tenir à jour à la
 * main ici, c'est `versionName` du module qui fait foi.
 */
@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
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
