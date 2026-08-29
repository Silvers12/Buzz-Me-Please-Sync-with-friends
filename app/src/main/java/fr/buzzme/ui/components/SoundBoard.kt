package fr.buzzme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.buzzme.core.SoundClip
import fr.buzzme.ui.theme.Stage

/**
 * La sonothèque de l'animateur : neuf touches préparées avant la partie, à portée du pouce
 * pendant. Un appui joue le son, un appui long ouvre le choix — c'est là qu'on remplit une
 * touche vide, qu'on en change ou qu'on la libère.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundBoard(
    slots: List<SoundClip?>,
    library: List<SoundClip>,
    playingId: String?,
    onPlay: (SoundClip) -> Unit,
    onEdit: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (library.isEmpty()) {
        EmptyLibraryNotice(modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(slots.size) { index ->
            val clip = slots[index]
            SoundPad(
                clip = clip,
                playing = clip != null && clip.id == playingId,
                onClick = { if (clip == null) onEdit(index) else onPlay(clip) },
                onLongClick = { onEdit(index) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundPad(
    clip: SoundClip?,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = when {
        playing -> Stage.Green
        clip != null -> Stage.VioletSoft
        else -> Stage.Line
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .background(
                Brush.verticalGradient(
                    if (playing) {
                        listOf(Stage.Green.copy(alpha = 0.25f), Stage.Night)
                    } else {
                        listOf(Stage.PanelHigh.copy(alpha = 0.75f), Stage.Night)
                    },
                ),
                shape,
            )
            .border(1.dp, accent.copy(alpha = if (clip != null) 0.6f else 0.35f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (clip == null) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Choisir un son pour cette touche",
                tint = Stage.TextMuted,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text = clip.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (playing) Stage.Green else Stage.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyLibraryNotice(modifier: Modifier = Modifier) {
    StagePanel(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Sonothèque vide")
        Spacer(Modifier.height(8.dp))
        Text(
            "Déposez vos fichiers audio dans app/src/main/res/raw (noms en minuscules, sans " +
                "accent : correct.mp3, wrong.mp3…), puis déclarez-les dans SoundLibrary.",
            style = MaterialTheme.typography.bodyMedium,
            color = Stage.TextMuted,
        )
    }
}

/** Choix du son posé sur une touche : écouter, remplacer, ou libérer la touche. */
@Composable
fun SoundPickerDialog(
    library: List<SoundClip>,
    current: SoundClip?,
    onPick: (SoundClip?) -> Unit,
    onPreview: (SoundClip) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Stage.Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Son de la touche",
                style = MaterialTheme.typography.headlineMedium,
                color = Stage.TextPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Appuyez sur ▶ pour écouter, sur le nom pour le poser sur la touche.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                library.forEach { clip ->
                    SoundRow(
                        clip = clip,
                        selected = clip.id == current?.id,
                        onPick = { onPick(clip) },
                        onPreview = { onPreview(clip) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = Stage.VioletSoft)
            }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = { onPick(null) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Stage.Red,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Libérer", color = Stage.Red)
                    }
                }
            }
        },
    )
}

@Composable
private fun SoundRow(
    clip: SoundClip,
    selected: Boolean,
    onPick: () -> Unit,
    onPreview: () -> Unit,
) {
    val accent = if (selected) Stage.Green else Stage.Line
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Stage.Night, shape)
            .border(1.dp, accent.copy(alpha = if (selected) 0.7f else 0.4f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Écouter ${clip.label}",
                tint = Stage.VioletSoft,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = clip.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Stage.Green else Stage.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPick)
                .padding(vertical = 14.dp),
        )
        Spacer(Modifier.size(12.dp))
    }
}

