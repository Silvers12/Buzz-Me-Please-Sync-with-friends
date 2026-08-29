package fr.buzzme.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.buzzme.core.Features
import fr.buzzme.ui.components.PrimaryAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.components.StageBackground
import fr.buzzme.ui.components.StagePanel
import fr.buzzme.ui.theme.Stage

private data class TutorialStep(
    val number: String,
    val title: String,
    val body: String,
    val accent: Color,
)

private val steps = listOf(
    TutorialStep(
        number = "1",
        title = "Choisir son pseudo",
        body = "Sur l'accueil, saisissez le pseudo qui s'affichera sur le plateau — il reste " +
            "modifiable à tout moment. La partie se joue en Wi-Fi local : tous les téléphones " +
            "sur le même réseau, sans Internet, et la mesure au plus juste." +
            if (Features.ONLINE_ROOMS) {
                " « En ligne » sert de repli pour jouer à distance et demande un projet Firebase."
            } else {
                " Le salon à distance arrivera dans une prochaine version."
            },
        accent = Stage.Violet,
    ),
    TutorialStep(
        number = "2",
        title = "Créer le salon ou le rejoindre",
        body = "L'animateur appuie sur « Créer un salon » : un code de 5 lettres apparaît en haut " +
            "de l'écran. Les autres appuient sur « Rejoindre » et saisissent ce code. En Wi-Fi " +
            "local, le salon apparaît tout seul dans la liste des salons détectés : un appui " +
            "suffit, sans rien taper.",
        accent = Stage.Gold,
    ),
    TutorialStep(
        number = "3",
        title = "Lancer le top",
        body = "L'animateur appuie sur « TOP ! ». Un décompte 3 · 2 · 1 se déclenche sur tous les " +
            "téléphones en même temps, puis chaque buzzer passe au vert à la milliseconde près. " +
            "Chez les joueurs, le reste de l'écran s'efface le temps de la manche : il ne reste " +
            "que le buzzer, en grand, au milieu. L'animateur garde son pupitre entier — c'est son " +
            "tableau de bord — et participe comme les autres.",
        accent = Stage.Green,
    ),
    TutorialStep(
        number = "4",
        title = "Buzzer",
        body = "Le premier qui appuie voit son buzzer virer au rouge, tous les autres se " +
            "verrouillent aussitôt. L'heure exacte du buzz est envoyée — heure, minute, seconde " +
            "et milliseconde — et l'animateur voit le classement, l'écart avec le meilleur temps " +
            "et la marge d'incertitude de la mesure. Une fois le classement arrêté, un seul " +
            "buzzer reste vert : celui qui a la parole.",
        accent = Stage.Red,
    ),
    TutorialStep(
        number = "5",
        title = "Enchaîner les manches",
        body = "Mauvaise réponse ? « Mauvaise réponse · au suivant », sous le bandeau, éteint le " +
            "buzzer du joueur qui avait la parole — il entend un signal d'erreur — et allume " +
            "celui du deuxième du classement, qui entend à son tour qu'on lui donne la main. " +
            "« Relancer » ouvre une nouvelle manche, la flèche circulaire éteint tout sans " +
            "toucher aux scores.",
        accent = Stage.Cyan,
    ),
    TutorialStep(
        number = "6",
        title = "Éliminer, réactiver, compter",
        body = "L'animateur appuie sur la ligne d'un joueur pour ouvrir son pupitre : buzzer noir " +
            "(éliminé) ou réactivé en un geste, points en +1, +3 ou −1, exclusion du salon. Un " +
            "joueur éliminé reste sur le plateau et peut revenir à tout moment.",
        accent = Stage.Amber,
    ),
    TutorialStep(
        number = "7",
        title = "Passer l'animation",
        body = "Depuis ce même pupitre, « Passer l'animation » confie le rôle d'animateur à un " +
            "autre joueur. Personne ne quitte le salon : scores, statuts et manche en cours " +
            "suivent, et l'ancien animateur redevient un joueur ordinaire.",
        accent = Stage.GoldSoft,
    ),
    TutorialStep(
        number = "8",
        title = "Adapter les règles",
        body = "Le bouton à curseurs, à droite du top, ouvre les règles de la partie. Le mode " +
            "duel peut être désactivé au profit du mode course, où tout le monde buzze et où " +
            "l'on obtient un classement complet — pratique pour un tie-break ou un jeu de " +
            "rapidité. « Scores masqués » garde le suspense : chaque joueur ne voit plus que " +
            "le sien, vous gardez le tableau complet. Le décompte et les sons se coupent au " +
            "même endroit.",
        accent = Stage.VioletSoft,
    ),
)

@Composable
fun TutorialScreen(onBack: () -> Unit) {
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
                Text(
                    "Comment ça marche",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Stage.TextPrimary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Huit étapes, de la création du salon au passage de l'animation.",
                style = MaterialTheme.typography.bodyLarge,
                color = Stage.TextSecondary,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEach { step -> StepCard(step) }
            }

            Spacer(Modifier.height(20.dp))

            StagePanel(modifier = Modifier.fillMaxWidth(), accent = Stage.Gold.copy(alpha = 0.4f)) {
                SectionLabel("Pour que le buzz reste juste", color = Stage.GoldSoft)
                Spacer(Modifier.height(10.dp))
                Bullet("Tout le monde sur le même Wi-Fi en mode local — pas un joueur en 4G.")
                Bullet("Activez « Garder l'écran allumé » dans les réglages avant de commencer.")
                Bullet(
                    "L'écart affiché tient compte de la latence réseau : c'est bien l'instant de " +
                        "l'appui qui est comparé, pas l'ordre d'arrivée des messages.",
                )
                Bullet(
                    "Sous la marge d'incertitude affichée (± quelques ms en local), deux réflexes " +
                        "sont à égalité : à l'animateur de trancher.",
                )
            }

            Spacer(Modifier.height(24.dp))

            PrimaryAction(
                text = "C'est parti",
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
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Stage.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Stage.TextSecondary,
                )
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
