package fr.buzzme.ui.screens

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.buzzme.core.Features
import fr.buzzme.net.online.FirebaseConfig
import fr.buzzme.ui.components.GhostAction
import fr.buzzme.ui.components.PrimaryAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.components.StageBackground
import fr.buzzme.ui.components.StageBadge
import fr.buzzme.ui.components.StagePanel
import fr.buzzme.ui.theme.Stage

@Composable
fun SettingsScreen(
    firebase: FirebaseConfig,
    sound: Boolean,
    keepScreenOn: Boolean,
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
                        contentDescription = "Retour",
                        tint = Stage.TextSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("Réglages", style = MaterialTheme.typography.headlineMedium, color = Stage.TextPrimary)
            }

            Spacer(Modifier.height(20.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Confort de jeu")
                Spacer(Modifier.height(12.dp))
                OptionSwitch(
                    title = "Sons et vibrations",
                    subtitle = "Bips du décompte, top de départ, retour au buzz.",
                    checked = sound,
                    onCheckedChange = onSound,
                )
                Spacer(Modifier.height(14.dp))
                OptionSwitch(
                    title = "Garder l'écran allumé",
                    subtitle = "Indispensable pendant une partie : un écran qui s'éteint, " +
                        "c'est un buzz manqué et une reconnexion à attendre.",
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
            }

            Spacer(Modifier.height(16.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Comment ça marche", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Le mode d'emploi complet : créer un salon, lancer le top, éliminer, " +
                        "compter les points, passer l'animation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                GhostAction(
                    text = "Ouvrir le tutoriel",
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
                "Buzz Me · buzzer de quiz synchronisé",
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
            SectionLabel("Mode en ligne (Firebase)", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            StageBadge(
                text = if (draft.isComplete) "Configuré" else "À remplir",
                color = if (draft.isComplete) Stage.Green else Stage.Amber,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Le mode Wi-Fi local ne demande aucune configuration. Ces quatre valeurs ne " +
                "servent qu'au repli en ligne : elles proviennent de votre propre projet " +
                "Firebase (voir docs/FIREBASE.md). Tous les joueurs doivent saisir les mêmes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
        Spacer(Modifier.height(14.dp))

        ConfigField(
            label = "ID du projet",
            value = draft.projectId,
            placeholder = "buzzme-quiz",
        ) { draft = draft.copy(projectId = it); saved = false }

        ConfigField(
            label = "ID de l'application",
            value = draft.applicationId,
            placeholder = "1:1234567890:android:abcdef",
        ) { draft = draft.copy(applicationId = it); saved = false }

        ConfigField(
            label = "Clé API",
            value = draft.apiKey,
            placeholder = "AIza…",
        ) { draft = draft.copy(apiKey = it); saved = false }

        ConfigField(
            label = "URL de la base",
            value = draft.databaseUrl,
            placeholder = "https://…firebasedatabase.app",
            keyboardType = KeyboardType.Uri,
        ) { draft = draft.copy(databaseUrl = it.trim()); saved = false }

        Spacer(Modifier.height(6.dp))
        PrimaryAction(
            text = if (saved) "Enregistré" else "Enregistrer",
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
