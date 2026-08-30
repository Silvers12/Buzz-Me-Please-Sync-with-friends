package com.osala.BuzzMePlease.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.game.RoomSession
import com.osala.BuzzMePlease.model.BuzzerVisual
import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.ui.components.BigBuzzer
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.SoundBoard
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.theme.Stage

/**
 * Le pupitre : la disposition des grands écrans tenus en paysage.
 *
 * Sur un téléphone, la place manque et les panneaux se relaient — le plateau s'efface derrière
 * la sonothèque, l'en-tête s'efface derrière le buzzer. Sur une tablette posée à côté de
 * l'animateur, il n'y a plus rien à cacher : les commandes, le verdict, le classement et les
 * sons tiennent côte à côte. L'animateur ne quitte plus le plateau des yeux pour lancer un son,
 * et n'attend plus un aller-retour pour voir qui vient de buzzer.
 *
 * La disposition du téléphone n'est pas touchée : elle reste celle de [RoomScreen] dès que
 * l'écran n'est pas assez large.
 */
@Composable
internal fun RoomConsole(
    session: RoomSession,
    view: RoomView,
    sounds: SoundDesk,
    onSelectPlayer: (String) -> Unit,
    onOptions: () -> Unit,
    onLeave: () -> Unit,
) {
    val state = view.state
    val amHost = view.amHost

    // Le joueur garde son plein écran de manche : quand le décompte part, tout s'efface au
    // profit du dôme, tablette ou pas. L'animateur, lui, ne perd jamais son pupitre : c'est
    // précisément pendant la manche qu'il en a besoin.
    val focused = !amHost &&
        (view.myVisual == BuzzerVisual.COUNTDOWN || view.myVisual == BuzzerVisual.ARMED)

    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            AnimatedVisibility(
                visible = !focused,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                Column {
                    RoomHeader(
                        state = state,
                        linkPhase = view.link.phase,
                        linkDetail = view.link.detail,
                        pingMillis = view.link.pingMillis,
                        precisionMillis = view.link.clockPrecisionMillis,
                        amHost = amHost,
                        onLeave = onLeave,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DeskColumn(
                    session = session,
                    view = view,
                    onSelectPlayer = onSelectPlayer,
                    onOptions = onOptions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                // Le plateau reste un panneau à part : c'est la colonne que l'animateur lit
                // pendant qu'il commande, et celle que le joueur regarde entre deux manches.
                if (!focused && view.showBoard) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(
                                when {
                                    state.options.hideBoard && amHost ->
                                        stringResource(R.string.room_board_hidden_host)

                                    amHost -> stringResource(R.string.room_board_host)
                                    else -> stringResource(R.string.room_board)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    R.string.room_online_count,
                                    view.board.count { it.connected },
                                    view.board.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = Stage.TextMuted,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        BoardList(
                            view = view,
                            onSelectPlayer = onSelectPlayer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // La sonothèque n'a plus à prendre la place du plateau : elle a la sienne.
                if (!focused && amHost) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        SectionLabel(stringResource(R.string.room_board_sounds))
                        Spacer(Modifier.height(8.dp))
                        SoundBoard(
                            slots = sounds.slots.map { id ->
                                sounds.library.firstOrNull { it.id == id }
                            },
                            library = sounds.library,
                            playingId = sounds.playingId,
                            onPlay = sounds.onPlay,
                            onEdit = sounds.onEdit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * La colonne de commande : ce sur quoi on appuie. Le GO et les réglages en haut, le verdict
 * juste en dessous — là où l'œil revient après chaque buzz —, le buzzer au milieu de ce qui
 * reste, et sa propre ligne au pied, comme sur le téléphone.
 */
@Composable
private fun DeskColumn(
    session: RoomSession,
    view: RoomView,
    onSelectPlayer: (String) -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = view.state
    Column(modifier = modifier) {
        if (view.amHost) {
            HostControls(
                state = state,
                onArm = session::arm,
                onReset = session::resetBoard,
                onOptions = onOptions,
            )
            Spacer(Modifier.height(12.dp))
        }

        ResultBanner(
            state = state,
            myId = view.myId,
            amHost = view.amHost,
            onRightAnswer = {
                state.speakerId?.let { session.addPoints(it, 1) }
                session.markRight()
            },
            onWrongAnswer = session::markWrong,
            onKnockOut = {
                state.wrongId?.let { session.setStatus(it, PlayerStatus.ELIMINATED) }
            },
            onNextPlayer = session::passSpeaker,
        )

        // Le dôme prend tout ce que les commandes laissent, sans jamais déborder de la colonne :
        // c'est le plus petit des deux côtés qui décide, sinon un écran très haut le ferait
        // sortir par la droite.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BigBuzzer(
                visual = view.myVisual,
                title = buzzerTitle(view.myVisual, view.remaining, state, view.myId),
                subtitle = buzzerSubtitle(view.myVisual, state, view.myId),
                onPress = { uptime -> session.buzz(uptime) },
                modifier = Modifier.size(minOf(maxWidth, maxHeight)),
            )
        }

        SelfRow(view = view, onSelectPlayer = onSelectPlayer)
    }
}
