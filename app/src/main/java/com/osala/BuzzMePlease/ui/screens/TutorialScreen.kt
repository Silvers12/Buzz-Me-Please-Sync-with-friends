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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.model.BuzzerVisual
import com.osala.BuzzMePlease.ui.components.BuzzerSample
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.readableWidth
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StagePanel
import com.osala.BuzzMePlease.ui.theme.Stage

private data class TutorialStep(
    val number: String,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val accent: Color,
    /** Ce qui ne se raconte pas avec des mots seuls — la légende des couleurs, par exemple. */
    val extra: (@Composable () -> Unit)? = null,
)

/** Une couleur de buzzer et ce qu'elle veut dire, le dôme à l'appui. */
@Composable
private fun ColourLegend() {
    val entries = listOf(
        BuzzerVisual.ARMED to R.string.tutorial_colour_armed,
        BuzzerVisual.SPEAKING to R.string.tutorial_colour_speaking,
        BuzzerVisual.RIGHT to R.string.tutorial_colour_right,
        BuzzerVisual.WRONG to R.string.tutorial_colour_wrong,
        BuzzerVisual.OFF to R.string.tutorial_colour_lost,
        BuzzerVisual.ELIMINATED to R.string.tutorial_colour_eliminated,
        BuzzerVisual.COUNTDOWN to R.string.tutorial_colour_countdown,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { (visual, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuzzerSample(visual = visual, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextSecondary,
                )
            }
        }
    }
}

private val steps = listOf(
    TutorialStep("1", R.string.tutorial_1_title, R.string.tutorial_1_body, Stage.Violet),
    TutorialStep("2", R.string.tutorial_2_title, R.string.tutorial_2_body, Stage.Gold),
    TutorialStep("3", R.string.tutorial_3_title, R.string.tutorial_3_body, Stage.Green),
    TutorialStep("4", R.string.tutorial_4_title, R.string.tutorial_4_body, Stage.Red),
    // Juste après le buzz : c'est là que la question « pourquoi mon buzzer est-il bleu ? » se pose.
    TutorialStep(
        number = "5",
        title = R.string.tutorial_colours_title,
        body = R.string.tutorial_colours_body,
        accent = Color(0xFFF1F3FF),
        extra = { ColourLegend() },
    ),
    // Les cartons se montrent avant les verdicts : c'est ce que le joueur voit en premier,
    // avant même de comprendre qui a appuyé sur quoi.
    TutorialStep("6", R.string.tutorial_cards_title, R.string.tutorial_cards_body, Stage.GoldSoft),
    TutorialStep("7", R.string.tutorial_5_title, R.string.tutorial_5_body, Stage.Cyan),
    TutorialStep("8", R.string.tutorial_6_title, R.string.tutorial_6_body, Stage.Amber),
    TutorialStep("9", R.string.tutorial_alerts_title, R.string.tutorial_alerts_body, Stage.Red),
    TutorialStep("10", R.string.tutorial_7_title, R.string.tutorial_7_body, Stage.GoldSoft),
    TutorialStep("11", R.string.tutorial_8_title, R.string.tutorial_8_body, Stage.VioletSoft),
    TutorialStep("12", R.string.tutorial_9_title, R.string.tutorial_9_body, Stage.Cyan),
)

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .readableWidth(720.dp)
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
                    stringResource(R.string.tutorial_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Stage.TextPrimary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tutorial_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = Stage.TextSecondary,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEach { step -> StepCard(step) }
            }

            Spacer(Modifier.height(20.dp))

            StagePanel(modifier = Modifier.fillMaxWidth(), accent = Stage.Gold.copy(alpha = 0.4f)) {
                SectionLabel(stringResource(R.string.tutorial_tips_title), color = Stage.GoldSoft)
                Spacer(Modifier.height(10.dp))
                Bullet(stringResource(R.string.tutorial_tip_1))
                Bullet(stringResource(R.string.tutorial_tip_2))
                Bullet(stringResource(R.string.tutorial_tip_3))
                Bullet(stringResource(R.string.tutorial_tip_4))
            }

            Spacer(Modifier.height(24.dp))

            PrimaryAction(
                text = stringResource(R.string.tutorial_start),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StepCard(step: TutorialStep) {
    StagePanel(modifier = Modifier.fillMaxWidth(), accent = step.accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(step.accent.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, step.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.number,
                    color = step.accent,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Stage.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(step.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Stage.TextSecondary,
                )
                step.extra?.let {
                    Spacer(Modifier.height(14.dp))
                    it()
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(Stage.Gold, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextSecondary,
        )
    }
}
