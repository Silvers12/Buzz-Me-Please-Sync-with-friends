package com.osala.BuzzMePlease.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.core.SoundClip
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
    buzzerSound: String,
    buzzerImport: String,
    buzzerLibrary: List<SoundClip>,
    onLanguage: (AppLanguage) -> Unit,
    onSound: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onBuzzerSound: (String) -> Unit,
    onPreviewSound: (String) -> Unit,
    onTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Un son à soi : n'importe quel fichier audio du téléphone fait l'affaire. On garde
    // l'autorisation de lecture au-delà du redémarrage, sinon le son choisi serait muet demain.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onBuzzerSound(uri.toString())
        onPreviewSound(uri.toString())
    }
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
                SectionLabel(stringResource(R.string.settings_buzzer_label))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_buzzer_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundChoice(
                        label = stringResource(R.string.settings_buzzer_default),
                        selected = buzzerSound.isBlank(),
                        onClick = {
                            onBuzzerSound("")
                            onPreviewSound("")
                        },
                    )
                    buzzerLibrary.forEach { clip ->
                        SoundChoice(
                            label = clip.label,
                            selected = buzzerSound == clip.path,
                            onClick = {
                                onBuzzerSound(clip.path)
                                onPreviewSound(clip.path)
                            },
                        )
                    }
                    // Le son importé reste dans la liste une fois choisi, même si on lui
                    // préfère ensuite un son du jeu : on y revient d'une touche.
                    if (buzzerImport.isNotBlank()) {
                        SoundChoice(
                            label = rememberImportedName(buzzerImport),
                            selected = buzzerSound == buzzerImport,
                            onClick = {
                                onBuzzerSound(buzzerImport)
                                onPreviewSound(buzzerImport)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                GhostAction(
                    text = stringResource(R.string.settings_buzzer_import),
                    icon = Icons.Filled.LibraryMusic,
                    onClick = { importer.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
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


/** Un son de buzzer au choix : on l'entend en le touchant, ce qui vaut mieux qu'un nom. */
@Composable
private fun SoundChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (selected) Stage.Violet else Stage.Line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) Stage.Violet.copy(alpha = 0.18f) else Stage.Night, shape)
            .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.5f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (selected) Stage.VioletSoft else Stage.TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Stage.TextPrimary else Stage.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Le nom du fichier importé, tel que le téléphone l'affiche. Faute de pouvoir l'interroger —
 * fichier déplacé, autorisation perdue — on annonce simplement « son importé ».
 */
@Composable
private fun rememberImportedName(source: String): String {
    val context = LocalContext.current
    val fallback = stringResource(R.string.settings_buzzer_imported)
    return remember(source, fallback) {
        runCatching {
            context.contentResolver.query(
                Uri.parse(source),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: fallback
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
