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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.BuildConfig
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.core.AppAnomalyException
import com.osala.BuzzMePlease.core.CrashReporter
import com.osala.BuzzMePlease.core.Links
import com.osala.BuzzMePlease.core.openLink
import com.osala.BuzzMePlease.core.startSupportMail
import com.osala.BuzzMePlease.core.SoundClip
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.isWideWindow
import com.osala.BuzzMePlease.ui.components.readableWidth
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

@Composable
fun SettingsScreen(
    sound: Boolean,
    keepScreenOn: Boolean,
    language: AppLanguage,
    buzzerSound: String,
    library: List<SoundClip>,
    onLanguage: (AppLanguage) -> Unit,
    onSound: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onBuzzerSound: (String) -> Unit,
    onImportSound: (String) -> Unit,
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
        // Le fichier rejoint la bibliothèque commune : il servira aussi sur les touches de la
        // sonothèque, sans avoir à l'importer une seconde fois.
        onImportSound(uri.toString())
        onBuzzerSound(uri.toString())
        onPreviewSound(uri.toString())
    }

    // Les panneaux sont les mêmes des deux côtés : seule leur mise en page change.
    val languagePanel: @Composable () -> Unit = { LanguagePanel(language, onLanguage) }
    val comfortPanel: @Composable () -> Unit = {
        ComfortPanel(sound, keepScreenOn, onSound, onKeepScreenOn)
    }
    val buzzerPanel: @Composable () -> Unit = {
        BuzzerPanel(
            buzzerSound = buzzerSound,
            library = library,
            onBuzzerSound = onBuzzerSound,
            onPreviewSound = onPreviewSound,
            onImport = { importer.launch(arrayOf("audio/*")) },
        )
    }
    val howToPanel: @Composable () -> Unit = { HowToPanel(onTutorial) }
    val supportPanel: @Composable () -> Unit = {
        SupportPanel(
            onContact = { context.startSupportMail() },
            onDonate = { context.openLink(Links.DONATE) },
        )
    }

    StageBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (isWideWindow(maxWidth, maxHeight)) {
                WideSettings(onBack, languagePanel, comfortPanel, buzzerPanel, howToPanel, supportPanel)
            } else {
                TallSettings(onBack, languagePanel, comfortPanel, buzzerPanel, howToPanel, supportPanel)
            }
        }
    }
}

/** Les réglages du téléphone : les panneaux les uns sous les autres, dans une colonne qui défile. */
@Composable
private fun TallSettings(
    onBack: () -> Unit,
    languagePanel: @Composable () -> Unit,
    comfortPanel: @Composable () -> Unit,
    buzzerPanel: @Composable () -> Unit,
    howToPanel: @Composable () -> Unit,
    supportPanel: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 24.dp),
    ) {
        SettingsHeader(onBack)

        Spacer(Modifier.height(20.dp))
        languagePanel()
        Spacer(Modifier.height(16.dp))
        comfortPanel()
        Spacer(Modifier.height(16.dp))
        buzzerPanel()
        Spacer(Modifier.height(16.dp))
        howToPanel()
        Spacer(Modifier.height(16.dp))
        supportPanel()

        // `BuildConfig.DEBUG` est une constante de compilation : en release la
        // condition vaut `false`, le bloc est éliminé par le compilateur et
        // DiagnosticsPanel devient du code mort que R8 retire du dex. Le bouton de
        // crash volontaire est donc absent du binaire publié, pas seulement caché.
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            DiagnosticsPanel()
        }

        Spacer(Modifier.height(24.dp))
        Signature()

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Les réglages d'une tablette en paysage, en deux colonnes. À gauche ce qu'on règle une fois
 * pour toutes ; à droite le choix du son de buzzer, qui est une liste et prend la hauteur.
 * Chaque colonne défile pour elle-même : aucune ne fait descendre l'autre.
 */
@Composable
private fun WideSettings(
    onBack: () -> Unit,
    languagePanel: @Composable () -> Unit,
    comfortPanel: @Composable () -> Unit,
    buzzerPanel: @Composable () -> Unit,
    howToPanel: @Composable () -> Unit,
    supportPanel: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        SettingsHeader(onBack)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                languagePanel()
                Spacer(Modifier.height(16.dp))
                comfortPanel()
                Spacer(Modifier.height(16.dp))
                howToPanel()
                Spacer(Modifier.height(24.dp))
                Signature()
                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                buzzerPanel()
                Spacer(Modifier.height(16.dp))
                supportPanel()
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(16.dp))
                    DiagnosticsPanel()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Le retour et le titre, identiques d'un format à l'autre. */
@Composable
private fun SettingsHeader(onBack: () -> Unit) {
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
}

/** La langue de l'application : celle du téléphone, ou l'une des deux imposées. */
@Composable
private fun LanguagePanel(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
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
}

/** Le confort de jeu : ce que le téléphone fait entendre, et l'écran qui ne s'éteint pas. */
@Composable
private fun ComfortPanel(
    sound: Boolean,
    keepScreenOn: Boolean,
    onSound: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
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
}

/** Le son que joue son propre téléphone quand on appuie — au choix, ou apporté de chez soi. */
@Composable
private fun BuzzerPanel(
    buzzerSound: String,
    library: List<SoundClip>,
    onBuzzerSound: (String) -> Unit,
    onPreviewSound: (String) -> Unit,
    onImport: () -> Unit,
) {
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
            // La bibliothèque entière : les sons du jeu, puis ceux qu'on a apportés. Un fichier
            // importé reste dans la liste même si on lui préfère ensuite un son du jeu.
            library.forEach { clip ->
                SoundChoice(
                    label = clip.label,
                    selected = buzzerSound == clip.path,
                    onClick = {
                        onBuzzerSound(clip.path)
                        onPreviewSound(clip.path)
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GhostAction(
            text = stringResource(R.string.settings_buzzer_import),
            icon = Icons.Filled.LibraryMusic,
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * De quoi joindre l'auteur, et de quoi le remercier.
 *
 * Le courriel part déjà rempli : modèle, version d'Android, version de l'application. Un rapport
 * de bug sans ces trois lignes oblige à un aller-retour, et la personne qui l'écrit n'a en
 * général aucune idée d'où les trouver.
 */
@Composable
private fun SupportPanel(onContact: () -> Unit, onDonate: () -> Unit) {
    StagePanel(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.settings_support_label))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.settings_contact_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        GhostAction(
            text = stringResource(R.string.settings_contact),
            icon = Icons.Filled.MailOutline,
            onClick = onContact,
            accent = Stage.Cyan,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_donate_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        GhostAction(
            text = stringResource(R.string.settings_donate),
            icon = Icons.Filled.Favorite,
            onClick = onDonate,
            accent = Stage.Gold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Le rappel des règles, pour qui arrive en cours de soirée. */
@Composable
private fun HowToPanel(onTutorial: () -> Unit) {
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
}

/**
 * Panneau de validation de l'intégration Crashlytics, réservé aux builds debug.
 *
 * Il couvre les deux chemins de remontée, qui n'ont rien en commun techniquement :
 * la non-fatale part tout de suite par `recordException` et le jeu continue ; le
 * crash fatal passe par le gestionnaire d'exceptions non interceptées et n'est
 * transmis qu'au lancement suivant. Tester l'un ne prouve rien sur l'autre.
 *
 * L'interrupteur vient en premier parce qu'il conditionne les deux : la collecte
 * est coupée par défaut en debug, sinon les parties de développement fausseraient
 * la métrique « utilisateurs sans crash » du build publié.
 *
 * Le panneau ne prend aucun paramètre : il ne lit ni ne modifie l'état du jeu, il
 * parle directement à [CrashReporter]. Rien à câbler dans [SettingsScreen], donc,
 * et rien à retirer de sa signature quand ce panneau disparaîtra.
 */
@Composable
private fun DiagnosticsPanel() {
    var collectionEnabled by remember { mutableStateOf(CrashReporter.isCollectionEnabled()) }
    // Lu une seule fois : la réponse porte sur le lancement précédent et ne peut
    // pas changer pendant que l'écran est ouvert.
    val crashedBefore = remember { CrashReporter.didCrashOnPreviousExecution() }

    StagePanel(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.diagnostics_label))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.diagnostics_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )

        Spacer(Modifier.height(12.dp))
        OptionSwitch(
            title = stringResource(R.string.diagnostics_collection_title),
            subtitle = stringResource(R.string.diagnostics_collection_subtitle),
            checked = collectionEnabled,
            onCheckedChange = { wanted ->
                CrashReporter.setCollectionEnabled(wanted)
                // On relit l'état réel plutôt que d'assumer : si le SDK refuse le
                // changement, l'interrupteur doit revenir en arrière et non mentir.
                collectionEnabled = CrashReporter.isCollectionEnabled()
            },
        )

        if (!collectionEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.diagnostics_collection_off),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.Amber,
            )
        }

        Spacer(Modifier.height(14.dp))
        GhostAction(
            text = stringResource(R.string.diagnostics_send_non_fatal),
            icon = Icons.Filled.BugReport,
            accent = Stage.Amber,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                CrashReporter.setCustomKey("diagnostic_test", true)
                CrashReporter.log("Diagnostic : envoi d'une non-fatale volontaire")
                // `record` et non `recordOnce` : un test doit pouvoir être rejoué
                // autant de fois que nécessaire dans la même session.
                CrashReporter.record(
                    AppAnomalyException("Test Crashlytics — non-fatale volontaire"),
                    "DiagnosticsPanel.nonFatal",
                )
            },
        )

        Spacer(Modifier.height(10.dp))
        GhostAction(
            text = stringResource(R.string.diagnostics_force_crash),
            icon = Icons.Filled.Warning,
            accent = Stage.Red,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                CrashReporter.setCustomKey("diagnostic_test", true)
                CrashReporter.log("Diagnostic : crash fatal volontaire")
                // L'exception traverse le gestionnaire de clic Compose puis le
                // thread principal : c'est exactement le trajet d'un vrai crash,
                // donc le test valide bien le handler installé par Crashlytics.
                throw RuntimeException("Test Crashlytics — crash fatal volontaire")
            },
        )

        Spacer(Modifier.height(10.dp))
        GhostAction(
            text = stringResource(R.string.diagnostics_flush),
            icon = Icons.Filled.CloudUpload,
            accent = Stage.Green,
            modifier = Modifier.fillMaxWidth(),
            onClick = { CrashReporter.sendUnsentReports() },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                if (crashedBefore) R.string.diagnostics_previous_crash_yes
                else R.string.diagnostics_previous_crash_no
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
    }
}

/** La mention, la version installée et le copyright : le bas de page de l'application. */
@Composable
private fun ColumnScope.Signature() {
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
