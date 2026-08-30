package com.osala.BuzzMePlease.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.game.LinkPhase
import com.osala.BuzzMePlease.ui.components.CodeDisplay
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.readableWidth
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

/**
 * L'attente entre le code saisi et le salon trouvé.
 *
 * Sans cet écran, un code tapé au hasard ouvrait un salon d'apparence normale — buzzer compris —
 * qui n'existait nulle part. Tant que l'hôte n'a pas répondu, on ne montre donc rien du jeu :
 * seulement le code cherché, ce qui se passe, et de quoi faire demi-tour.
 */
@Composable
fun JoiningScreen(
    code: String,
    phase: LinkPhase,
    detail: String,
    onCancel: () -> Unit,
) {
    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(stringResource(R.string.joining_title))

            Spacer(Modifier.height(16.dp))

            // Sans largeur imposée, les cases se rangent d'elles-mêmes au centre de l'écran :
            // c'est le code qu'on regarde ici, il n'a pas à être poussé dans un coin.
            CodeDisplay(code = code)

            Spacer(Modifier.height(28.dp))

            StagePanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // La recherche se poursuit même quand le salon reste introuvable : l'hôte
                    // ne l'a peut-être pas encore ouvert.
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = if (phase == LinkPhase.ERROR) Stage.Red else Stage.Violet,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = detail.ifBlank { stringResource(R.string.joining_searching) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Stage.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.joining_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            GhostAction(
                text = stringResource(R.string.joining_cancel),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
