package com.osala.BuzzMePlease.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppClock
import com.osala.BuzzMePlease.core.SoundClip
import com.osala.BuzzMePlease.core.SoundFx
import com.osala.BuzzMePlease.game.LinkPhase
import com.osala.BuzzMePlease.game.LinkStatus
import com.osala.BuzzMePlease.game.RoomSession
import com.osala.BuzzMePlease.model.AlertKind
import com.osala.BuzzMePlease.model.Buzz
import com.osala.BuzzMePlease.model.BuzzerVisual
import com.osala.BuzzMePlease.model.GameMode
import com.osala.BuzzMePlease.model.Player
import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.RoomAlert
import com.osala.BuzzMePlease.model.RoomState
import com.osala.BuzzMePlease.model.RoundState
import com.osala.BuzzMePlease.model.visualFor
import com.osala.BuzzMePlease.ui.components.Announcement
import com.osala.BuzzMePlease.ui.components.BigBuzzer
import com.osala.BuzzMePlease.ui.components.CodeDisplay
import com.osala.BuzzMePlease.ui.components.GhostAction
import com.osala.BuzzMePlease.ui.components.IconAction
import com.osala.BuzzMePlease.ui.components.isWideWindow
import com.osala.BuzzMePlease.ui.components.PlayerRow
import com.osala.BuzzMePlease.ui.components.PrimaryAction
import com.osala.BuzzMePlease.ui.components.SectionLabel
import com.osala.BuzzMePlease.ui.components.SoundBoard
import com.osala.BuzzMePlease.ui.components.SoundPickerDialog
import com.osala.BuzzMePlease.ui.components.StageBackground
import com.osala.BuzzMePlease.ui.components.StageBadge
import com.osala.BuzzMePlease.ui.components.StatusDot
import com.osala.BuzzMePlease.ui.theme.MonoDigits
import com.osala.BuzzMePlease.ui.theme.Stage
import kotlinx.coroutines.delay

@Composable
fun RoomScreen(
    session: RoomSession,
    soundFx: SoundFx,
    onLeave: () -> Unit,
    soundLibrary: List<SoundClip> = emptyList(),
    soundboard: List<String> = emptyList(),
    playingClipId: String? = null,
    onPlayClip: (SoundClip) -> Unit = {},
    onStopClip: () -> Unit = {},
    onPickClip: (index: Int, clipId: String?) -> Unit = { _, _ -> },
    onImportSound: (String) -> Unit = {},
) {
    val state by session.state.collectAsStateWithLifecycle()
    val link by session.link.collectAsStateWithLifecycle()
    val localBuzzRound by session.localBuzzRound.collectAsStateWithLifecycle()

    val amHost = state.hostId == session.myId
    var showOptions by remember { mutableStateOf(false) }
    var selectedPlayer by remember { mutableStateOf<String?>(null) }
    // Sonothèque : elle occupe la place du plateau, et seul l'animateur y a droit.
    var showSounds by remember { mutableStateOf(false) }
    var editedSlot by remember { mutableStateOf<Int?>(null) }

    // Horloge locale calée sur celle de l'hôte. Elle ne s'anime que pendant le décompte :
    // le reste du temps, rien ne bouge et l'écran reste au repos.
    var nowHost by remember { mutableStateOf(session.nowHostMillis()) }
    LaunchedEffect(state.roundState, state.round) {
        if (state.roundState == RoundState.COUNTDOWN) {
            while (true) {
                nowHost = session.nowHostMillis()
                delay(32)
            }
        } else {
            nowHost = session.nowHostMillis()
        }
    }

    val myVisual = state.visualFor(session.myId, nowHost, localBuzzRound)
    val remaining = state.countdownRemaining(nowHost)

    PlayFeedback(
        state = state,
        visual = myVisual,
        remaining = remaining,
        myId = session.myId,
        amHost = amHost,
        soundFx = soundFx,
    )

    val board = remember(state, amHost) {
        orderPlayers(state, keepEliminated = amHost, myId = session.myId)
    }
    // Le plateau du salon, dont on retire sa propre ligne : elle est épinglée au bas de
    // l'écran, sous les yeux en permanence, plateau ou pas.
    val others = remember(board) { board.filter { it.id != session.myId } }

    val view = RoomView(
        state = state,
        link = link,
        myId = session.myId,
        amHost = amHost,
        nowHost = nowHost,
        myVisual = myVisual,
        remaining = remaining,
        board = board,
        others = others,
        // L'animateur garde toujours son tableau. Le joueur le perd quand l'animateur a masqué
        // le plateau : il ne lui reste alors que sa ligne, et le buzzer prend le milieu.
        showBoard = amHost || !state.options.hideBoard,
    )
    val sounds = SoundDesk(
        library = soundLibrary,
        slots = soundboard,
        playingId = playingClipId,
        onPlay = onPlayClip,
        onStop = onStopClip,
        onEdit = { index -> editedSlot = index },
    )

    // Ce que le plateau annonce en grand, au milieu de l'écran. Une seule à la fois : la
    // dernière chasse la précédente, comme un carton d'émission.
    var announcement by remember { mutableStateOf<Announce?>(null) }
    val floorText = stringResource(R.string.announce_floor)
    val rightText = stringResource(R.string.announce_right)
    val wrongText = stringResource(R.string.announce_wrong)
    val lateText = stringResource(R.string.announce_late)
    val outText = stringResource(R.string.announce_out)

    // Ce qui arrive à son propre buzzer, et à lui seul : on n'annonce pas le sort des autres.
    // La clé porte la manche, sans quoi deux verdicts identiques d'affilée passeraient muets.
    LaunchedEffect(state.round, myVisual) {
        when (myVisual) {
            BuzzerVisual.SPEAKING -> announcement = Announce(floorText, Stage.GoldSoft)
            BuzzerVisual.RIGHT -> announcement = Announce(rightText, Stage.Green)
            BuzzerVisual.WRONG -> announcement = Announce(wrongText, Stage.Red)
            // Devancé, ou passé par « suivant » : dans les deux cas on avait appuyé.
            // Celui qui n'a pas touché son buzzer ne reçoit rien.
            BuzzerVisual.LOST -> announcement = Announce(lateText, Stage.Blue)
            else -> Unit
        }
    }

    // L'élimination ne se rattache à aucune manche : elle dure jusqu'à ce qu'on soit réactivé.
    // On l'annonce donc au basculement, une fois, et pas à chaque nouveau go.
    val amEliminated = state.player(session.myId)?.isEliminated == true
    LaunchedEffect(amEliminated) {
        if (amEliminated) announcement = Announce(outText, Stage.TextSecondary)
    }

    // Les annonces de l'animateur arrivent par le réseau : ce sont des événements, pas un état,
    // et elles s'écrivent dans la langue de l'appareil qui les reçoit — d'où le contexte
    // localisé plutôt qu'un texte préparé par l'expéditeur.
    val resources = LocalContext.current
    val yellowYou = stringResource(R.string.announce_yellow_you)
    val redYou = stringResource(R.string.announce_red_you)
    val tieText = stringResource(R.string.announce_game_over_tie)
    LaunchedEffect(session) {
        session.alerts.collect { alert ->
            announcement = when (alert.kind) {
                AlertKind.YELLOW_CARD -> Announce(
                    if (alert.playerId == session.myId) yellowYou
                    else resources.getString(R.string.announce_yellow_other, alert.playerName),
                    Stage.Gold,
                )

                AlertKind.RED_CARD -> Announce(
                    if (alert.playerId == session.myId) redYou
                    else resources.getString(R.string.announce_red_other, alert.playerName),
                    Stage.Red,
                )

                AlertKind.GAME_OVER -> Announce(
                    if (alert.tied) tieText
                    else resources.getString(R.string.announce_game_over, alert.playerName),
                    Stage.Gold,
                )
            }
        }
    }

    // Le passage de relais regarde tout le salon, pas seulement celui qui le reçoit : chacun
    // doit savoir à qui parler. On ne l'annonce qu'au changement — arriver dans un salon
    // qui a déjà son animateur n'est pas une passation.
    val hostYouText = stringResource(R.string.announce_host_you)
    val hostOtherText = stringResource(R.string.announce_host_other, state.host?.name.orEmpty())
    var knownHost by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.hostId) {
        val previous = knownHost
        knownHost = state.hostId
        if (previous == null || previous == state.hostId) return@LaunchedEffect
        announcement = if (state.hostId == session.myId) {
            Announce(hostYouText, Stage.Gold)
        } else {
            Announce(hostOtherText, Stage.Gold)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // La place réellement disponible décide de la disposition : on mesure la fenêtre plutôt que
        // d'interroger l'écran, ce qui reste juste en écran partagé comme après une rotation. Assez
        // large et plus large que haute, c'est une tablette posée en paysage : l'animateur a droit à
        // son pupitre entier plutôt qu'à des panneaux qui se relaient. En dessous, rien ne change —
        // le téléphone garde la disposition qui a été réglée écran par écran.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val console = isWideWindow(maxWidth, maxHeight)

            if (console) {
                RoomConsole(
                    session = session,
                    view = view,
                    sounds = sounds,
                    onSelectPlayer = { selectedPlayer = it },
                    onOptions = { showOptions = true },
                    onLeave = onLeave,
                )
            } else {
                PhoneRoom(
                    session = session,
                    view = view,
                    sounds = sounds,
                    showSounds = showSounds,
                    onToggleSounds = { showSounds = !showSounds },
                    onSelectPlayer = { selectedPlayer = it },
                    onOptions = { showOptions = true },
                    onLeave = onLeave,
                )
            }
        }

        // Par-dessus les deux dispositions, et sans capter d'appui : le buzzer reste utilisable
        // pendant que le carton passe.
        announcement?.let { current ->
            Announcement(
                text = current.text,
                accent = current.accent,
                onDone = { announcement = null },
            )
        }
    }

    val target = selectedPlayer?.let { id -> state.player(id) }
    if (amHost && target != null) {
        PlayerActionsDialog(
            player = target,
            isHost = state.hostId == target.id,
            isMe = target.id == session.myId,
            onDismiss = { selectedPlayer = null },
            onToggleStatus = {
                session.setStatus(
                    target.id,
                    if (target.isEliminated) PlayerStatus.ACTIVE else PlayerStatus.ELIMINATED,
                )
                selectedPlayer = null
            },
            onPoints = { delta -> session.addPoints(target.id, delta) },
            // Le carton part tout de suite et la fiche se referme : on ne reste pas devant un
            // dialogue pendant que le salon entier regarde l'annonce.
            onAlert = { kind ->
                session.sendAlert(RoomAlert(kind = kind, playerId = target.id))
                selectedPlayer = null
            },
            onTransferHost = {
                session.transferHost(target.id)
                selectedPlayer = null
            },
            onKick = {
                session.kick(target.id)
                selectedPlayer = null
            },
        )
    }

    val slot = editedSlot
    if (amHost && slot != null) {
        SoundPickerDialog(
            library = soundLibrary,
            current = soundboard.getOrNull(slot)?.let { id -> soundLibrary.firstOrNull { it.id == id } },
            onPick = { clip ->
                onPickClip(slot, clip?.id)
                editedSlot = null
            },
            onPreview = onPlayClip,
            onImport = onImportSound,
            onDismiss = { editedSlot = null },
        )
    }

    if (showOptions) {
        RoomOptionsDialog(
            state = state,
            amHost = amHost,
            onDismiss = { showOptions = false },
            onOptions = session::setOptions,
            onResetScores = session::resetScores,
            onEndGame = {
                session.sendAlert(RoomAlert(AlertKind.GAME_OVER))
                showOptions = false
            },
        )
    }
}

/**
 * La disposition du téléphone : une seule colonne, où les panneaux se relaient faute de place.
 * Le plateau cède la place à la sonothèque, l'en-tête et le tableau s'effacent pendant la
 * manche pour ne laisser que le dôme.
 */
@Composable
private fun PhoneRoom(
    session: RoomSession,
    view: RoomView,
    sounds: SoundDesk,
    showSounds: Boolean,
    onToggleSounds: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onOptions: () -> Unit,
    onLeave: () -> Unit,
) {
    val state = view.state
    val amHost = view.amHost
    val myVisual = view.myVisual
    val remaining = view.remaining
    val board = view.board
    val showBoard = view.showBoard

    // Le buzzer est taillé sur la hauteur de l'écran, pas sur ce qui reste une fois le bandeau
    // de résultat affiché : sur un petit téléphone il garde une taille jouable, sur un grand il
    // ne flotte pas au milieu du vide.
    val configuration = LocalConfiguration.current
    val buzzerHeight = (configuration.screenHeightDp.dp * 0.32f).coerceIn(150.dp, 300.dp)

    // Manche en cours, côté joueur : le plateau et l'en-tête s'effacent par le haut et par le
    // bas, le buzzer prend toute la place. Plus rien à lire, plus rien à surveiller — juste le
    // bouton sous le pouce. L'animateur, lui, garde son pupitre entier : c'est son tableau de bord.
    val focused = !amHost &&
        (myVisual == BuzzerVisual.COUNTDOWN || myVisual == BuzzerVisual.ARMED)

    val centreBuzzer = !showBoard
    val focusSide = minOf(
        configuration.screenWidthDp.dp - 36.dp,
        configuration.screenHeightDp.dp * 0.82f,
    )
    val stageHeight by animateDpAsState(
        targetValue = if (focused) focusSide else buzzerHeight,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "stage",
    )
    // Une fois l'écran vidé, le buzzer se recentre : la marge du haut se creuse à mesure que le
    // reste s'en va, pour que le bouton finisse au milieu de l'écran, sous le pouce.
    val focusGap = ((configuration.screenHeightDp.dp - focusSide) / 2 - 28.dp).coerceAtLeast(0.dp)
    val stageGap by animateDpAsState(
        // Quand le buzzer est déjà centré par les espaces souples, il n'y a rien à creuser.
        targetValue = if (focused && !centreBuzzer) focusGap else 0.dp,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "stageGap",
    )

    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp),
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
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(stageGap))

            // Sans tableau, le buzzer ne reste pas collé en haut : il se centre dans tout
            // l'espace que le plateau laisse libre.
            if (centreBuzzer) Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
                contentAlignment = Alignment.Center,
            ) {
                // fillMaxHeight + aspectRatio(1f) côté buzzer : le dôme reste rond quelle que
                // soit la largeur de l'écran.
                BigBuzzer(
                    visual = myVisual,
                    title = buzzerTitle(myVisual, remaining, state, session.myId),
                    subtitle = buzzerSubtitle(myVisual, state, session.myId),
                    onPress = { uptime -> session.buzz(uptime) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            if (centreBuzzer) Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = !focused,
                modifier = if (centreBuzzer) Modifier else Modifier.weight(1f, fill = false),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))

                    if (amHost) {
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
                        myId = session.myId,
                        amHost = amHost,
                        // Bonne réponse : le point est mis, le buzzer du joueur passe au vert et
                        // la récompense se fait entendre. Les buzzers s'éteignent juste après —
                        // la manche est jouée, il n'y a plus rien à arbitrer.
                        onRightAnswer = {
                            state.speakerId?.let { session.addPoints(it, 1) }
                            session.markRight()
                        },
                        onWrongAnswer = session::markWrong,
                        // Éliminer celui qui vient de se tromper : la manche peut alors
                        // repartir sans lui, aux seuls joueurs qui n'ont pas encore répondu.
                        onKnockOut = {
                            state.wrongId?.let { session.setStatus(it, PlayerStatus.ELIMINATED) }
                        },
                        onNextPlayer = session::passSpeaker,
                    )

                    Spacer(Modifier.height(8.dp))

                    if (showBoard) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(
                                when {
                                    showSounds -> stringResource(R.string.room_board_sounds)
                                    // Rappel utile à l'animateur : il sait ce qu'il a coupé.
                                    state.options.hideBoard ->
                                        stringResource(R.string.room_board_hidden_host)

                                    amHost -> stringResource(R.string.room_board_host)
                                    else -> stringResource(R.string.room_board)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (amHost) {
                                // Le panneau se rétracte sur le plateau : deux vues, une place.
                                SoundBoardToggle(open = showSounds, onToggle = onToggleSounds)
                            } else {
                                Text(
                                    stringResource(
                                        R.string.room_online_count,
                                        board.count { it.connected },
                                        board.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Stage.TextMuted,
                                    maxLines = 1,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        if (showSounds && amHost) {
                            SoundBoard(
                                slots = sounds.slots.map { id ->
                                    sounds.library.firstOrNull { it.id == id }
                                },
                                library = sounds.library,
                                playingId = sounds.playingId,
                                onPlay = sounds.onPlay,
                                onStop = sounds.onStop,
                                onEdit = sounds.onEdit,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            BoardList(
                                view = view,
                                onSelectPlayer = onSelectPlayer,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    SelfRow(view = view, onSelectPlayer = onSelectPlayer)

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Un carton d'annonce en attente d'être crié : son texte et sa couleur. */
internal data class Announce(val text: String, val accent: Color)

/**
 * Ce que les deux dispositions — le téléphone et le pupitre — lisent de la manche en cours.
 * Réunies ici, ces valeurs ne sont calculées qu'une fois, et les deux écrans montrent
 * forcément la même chose.
 */
internal data class RoomView(
    val state: RoomState,
    val link: LinkStatus,
    val myId: String,
    val amHost: Boolean,
    val nowHost: Long,
    val myVisual: BuzzerVisual,
    val remaining: Long?,
    /** Le salon entier, classement en tête. */
    val board: List<Player>,
    /** Le salon sans soi : sa propre ligne est affichée à part, toujours au même endroit. */
    val others: List<Player>,
    val showBoard: Boolean,
)

/** La sonothèque et ce qu'on peut en faire, pour ne pas traîner cinq paramètres de plus. */
internal data class SoundDesk(
    val library: List<SoundClip>,
    val slots: List<String>,
    val playingId: String?,
    val onPlay: (SoundClip) -> Unit,
    val onStop: () -> Unit,
    val onEdit: (index: Int) -> Unit,
)

/**
 * Le classement des autres joueurs. Les buzz le réordonnent : sans le retour en tête, la liste
 * resterait sur le joueur qui menait et montrerait une ligne coupée au lieu du haut du tableau.
 */
@Composable
internal fun BoardList(
    view: RoomView,
    onSelectPlayer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = view.state
    val boardState = rememberLazyListState()
    LaunchedEffect(state.round, state.winnerId) {
        if (boardState.firstVisibleItemIndex != 0 ||
            boardState.firstVisibleItemScrollOffset != 0
        ) {
            boardState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = boardState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(view.others, key = { it.id }) { player ->
            PlayerRow(
                player = player,
                visual = state.visualFor(player.id, view.nowHost),
                buzz = state.buzzOf(player.id),
                gapMillis = state.gapOf(player.id),
                rank = state.rankOf(player.id),
                isWinner = state.winnerId == player.id,
                isHost = state.hostId == player.id,
                isMe = false,
                showControls = view.amHost,
                onClick = { onSelectPlayer(player.id) },
            )
        }
    }
}

/**
 * Sa propre ligne, toujours sous les yeux : au pied du plateau quand il est là, seule au bas de
 * l'écran quand il ne l'est pas.
 */
@Composable
internal fun SelfRow(view: RoomView, onSelectPlayer: (String) -> Unit) {
    val state = view.state
    val me = state.player(view.myId) ?: return
    PlayerRow(
        player = me,
        visual = view.myVisual,
        buzz = state.buzzOf(me.id),
        gapMillis = state.gapOf(me.id),
        rank = state.rankOf(me.id),
        isWinner = state.winnerId == me.id,
        isHost = state.hostId == me.id,
        isMe = true,
        showControls = view.amHost,
        onClick = { onSelectPlayer(me.id) },
    )
}

/**
 * Trie le plateau : les buzz dans l'ordre chronologique d'abord, le reste par ordre d'arrivée.
 *
 * Un joueur éliminé ne peut plus buzzer : sur les téléphones des joueurs, sa ligne disparaît du
 * plateau plutôt que d'encombrer le tableau. L'animateur, lui, la garde — c'est de là qu'il le
 * réactive ou lui remet des points. Chacun continue de se voir soi-même, éliminé ou non.
 */
private fun orderPlayers(state: RoomState, keepEliminated: Boolean, myId: String): List<Player> {
    val ranked = state.ranking.mapNotNull { state.player(it.playerId) }
    val rest = state.players.filter { player -> ranked.none { it.id == player.id } }
        .sortedWith(compareBy({ it.isEliminated }, { it.joinedAt }))
    val board = ranked + rest
    return if (keepEliminated) board else board.filter { !it.isEliminated || it.id == myId }
}

@Composable
internal fun buzzerTitle(
    visual: BuzzerVisual,
    remaining: Long?,
    state: RoomState,
    myId: String,
): String = when (visual) {
    BuzzerVisual.COUNTDOWN -> remaining?.let { ((it / 1000) + 1).coerceAtMost(9L).toString() } ?: "!"
    BuzzerVisual.ARMED -> stringResource(R.string.buzzer_buzz)
    BuzzerVisual.BUZZED,
    BuzzerVisual.SPEAKING,
    -> state.buzzOf(myId)?.let { Buzz.formatReaction(it.reactionMillis) }
        ?: stringResource(R.string.buzzer_taken)
    BuzzerVisual.RIGHT -> stringResource(R.string.buzzer_correct)
    BuzzerVisual.WRONG -> stringResource(R.string.buzzer_missed)
    BuzzerVisual.LOST -> stringResource(R.string.buzzer_too_late)
    BuzzerVisual.ELIMINATED -> stringResource(R.string.buzzer_out)
    BuzzerVisual.OFF -> stringResource(R.string.buzzer_ready)
}

@Composable
internal fun buzzerSubtitle(visual: BuzzerVisual, state: RoomState, myId: String): String =
    when (visual) {
        BuzzerVisual.COUNTDOWN -> stringResource(R.string.buzzer_get_ready)
        BuzzerVisual.ARMED -> stringResource(R.string.buzzer_press)
        BuzzerVisual.BUZZED -> stringResource(R.string.buzzer_recorded)
        BuzzerVisual.SPEAKING -> stringResource(R.string.buzzer_your_turn)
        BuzzerVisual.RIGHT -> stringResource(R.string.buzzer_point)
        // Écarté par l'animateur, ou devancé : dans les deux cas on nomme qui a la parole.
        BuzzerVisual.WRONG,
        BuzzerVisual.LOST,
        -> state.speakerId?.let { state.player(it)?.name.orEmpty() } ?: ""
        BuzzerVisual.ELIMINATED -> stringResource(R.string.buzzer_can_return)
        BuzzerVisual.OFF -> stringResource(R.string.buzzer_waiting)
    }

// ------------------------------------------------------------------- en-tête

@Composable
internal fun RoomHeader(
    state: RoomState,
    linkPhase: LinkPhase,
    linkDetail: String,
    pingMillis: Long,
    precisionMillis: Long,
    amHost: Boolean,
    onLeave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLeave) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.room_leave),
                    tint = Stage.TextSecondary,
                )
            }
            Spacer(Modifier.width(6.dp))
            // Le code prend la place qui reste une fois les badges mesurés : c'est lui qui se
            // resserre sur un écran étroit, jamais le badge « Animateur » qui viendrait le coller.
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.room_code_label))
                Spacer(Modifier.height(4.dp))
                CodeDisplay(code = state.code)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                StageBadge(
                    text = stringResource(if (amHost) R.string.room_badge_host else R.string.room_badge_player),
                    color = if (amHost) Stage.Gold else Stage.VioletSoft,
                )
                Spacer(Modifier.height(6.dp))
                StageBadge(
                    text = stringResource(
                        if (state.options.mode == GameMode.DUEL) R.string.room_badge_duel
                        else R.string.room_badge_race,
                    ),
                    color = Stage.Cyan,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = when (linkPhase) {
                    LinkPhase.CONNECTED -> Stage.Green
                    LinkPhase.ERROR -> Stage.Red
                    LinkPhase.CLOSED -> Stage.TextMuted
                    else -> Stage.Amber
                },
                size = 8.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = linkDetail.ifBlank { stringResource(R.string.room_round, state.round) },
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (pingMillis > 0) {
                // Latence de la liaison, et incertitude de la synchronisation d'horloge :
                // en dessous de cet écart, deux réflexes ne sont pas départageables.
                Text(
                    text = stringResource(R.string.room_latency, pingMillis, precisionMillis),
                    style = MonoDigits,
                    color = Stage.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Bascule entre le plateau et la sonothèque : les deux se partagent la même place. */
@Composable
private fun SoundBoardToggle(open: Boolean, onToggle: () -> Unit) {
    val accent = if (open) Stage.Gold else Stage.VioletSoft
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.12f), shape)
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) Icons.Filled.Groups else Icons.Filled.LibraryMusic,
            contentDescription = stringResource(
                if (open) R.string.room_sounds_close_desc else R.string.room_sounds_open_desc,
            ),
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (open) R.string.room_sounds_close else R.string.room_sounds_open),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------ pupitre animateur

@Composable
internal fun HostControls(
    state: RoomState,
    onArm: () -> Unit,
    onReset: () -> Unit,
    onOptions: () -> Unit,
) {
    val armed = state.roundState == RoundState.ARMED || state.roundState == RoundState.COUNTDOWN
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryAction(
                text = stringResource(if (armed) R.string.room_restart else R.string.room_go),
                icon = Icons.Filled.Bolt,
                // Tout le salon éliminé : le go n'arme personne, le bouton s'éteint.
                enabled = state.canArm,
                onClick = onArm,
                colors = listOf(Stage.Gold, Color(0xFFDE9A12)),
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.room_reset),
                onClick = onReset,
                accent = Stage.TextSecondary,
            )
            IconAction(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.room_rules),
                onClick = onOptions,
            )
        }

        // Un bouton éteint sans un mot laisserait l'animateur chercher : on dit par où repartir.
        if (!state.canArm) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.room_all_out),
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.Amber,
            )
        }
    }
}

// ------------------------------------------------------------------- bandeau

@Composable
internal fun ResultBanner(
    state: RoomState,
    myId: String,
    amHost: Boolean,
    onRightAnswer: () -> Unit,
    onWrongAnswer: () -> Unit,
    onKnockOut: () -> Unit,
    onNextPlayer: () -> Unit,
) {
    // Le bandeau suit celui qui a la parole, pas le premier chronomètre : après une mauvaise
    // réponse, c'est le suivant du classement qui s'affiche.
    val speaker = state.speakerId?.let { state.player(it) }
    val buzz = state.speakerId?.let { state.buzzOf(it) }
    // Tout le monde s'est trompé : le bandeau le dit au lieu de disparaître sans un mot.
    val exhausted = speaker == null && state.buzzes.isNotEmpty()

    if (exhausted) {
        EmptyHandBanner()
        return
    }

    val visible = speaker != null && buzz != null

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        if (speaker == null || buzz == null) return@AnimatedVisibility
        val winner = speaker
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Stage.Gold.copy(alpha = 0.22f), Stage.Night.copy(alpha = 0.2f)),
                    ),
                    shape,
                )
                .border(1.dp, Stage.Gold.copy(alpha = 0.6f), shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (winner.id == myId) {
                        stringResource(R.string.banner_yours)
                    } else {
                        stringResource(R.string.banner_has_hand, winner.name)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Stage.GoldSoft,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = buzz.wallClockText(),
                    style = MonoDigits,
                    color = Stage.GoldSoft,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            // Le suivant dans la file : celui à qui la main reviendra en cas de mauvaise réponse.
            val next = state.ranking.firstOrNull {
                it.playerId != speaker.id && it.playerId !in state.passedIds
            }
            val detail = buildString {
                append(stringResource(R.string.banner_reaction, Buzz.formatReaction(buzz.reactionMillis)))
                if (buzz.precisionMillis > 0) {
                    append(stringResource(R.string.banner_precision, buzz.precisionMillis))
                }
                if (next != null) {
                    val gap = next.atHostMillis - buzz.atHostMillis
                    append(
                        stringResource(
                            R.string.banner_ahead,
                            state.player(next.playerId)?.name.orEmpty(),
                            Buzz.formatGap(gap),
                        ),
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextSecondary,
            )
            if (state.provisional) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.banner_photo_finish),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.Amber,
                    textAlign = TextAlign.Start,
                )
            } else if (amHost && state.rightId == null) {
                Spacer(Modifier.height(10.dp))
                // Le verdict de l'animateur, en deux gestes : le point est mis et la manche se
                // clôt, ou la main descend au suivant. Une fois « vrai » prononcé, les boutons
                // s'effacent : le point est déjà compté, il ne peut pas l'être deux fois.
                // Une fois la réponse déclarée fausse, « faux » n'a plus rien à dire : la place
                // revient à l'élimination. En mode rapide, c'est ce qui permet de relancer la
                // même question aux seuls joueurs qui n'ont pas encore répondu.
                val marked = state.wrongId == speaker.id
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostAction(
                        text = stringResource(R.string.banner_right),
                        onClick = onRightAnswer,
                        accent = Stage.Green,
                        modifier = Modifier.weight(1f),
                    )
                    GhostAction(
                        text = stringResource(
                            if (marked) R.string.banner_knock_out else R.string.banner_wrong,
                        ),
                        onClick = if (marked) onKnockOut else onWrongAnswer,
                        accent = Stage.Red,
                        modifier = Modifier.weight(if (marked) 1.3f else 1f),
                    )
                    GhostAction(
                        text = stringResource(R.string.banner_next),
                        onClick = onNextPlayer,
                        accent = Stage.VioletSoft,
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }
    }
}

/** Plus personne en lice sur cette manche : il ne reste qu'à relancer. */
@Composable
private fun EmptyHandBanner() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Stage.Panel.copy(alpha = 0.6f), shape)
            .border(1.dp, Stage.Line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.banner_nobody),
            style = MaterialTheme.typography.bodyLarge,
            color = Stage.TextMuted,
        )
    }
}

// ------------------------------------------------------------- son & vibration

/**
 * Habillage sonore. Chaque effet est déclenché par un changement d'état observé, jamais par
 * une minuterie : il tombe donc exactement en même temps que l'animation correspondante.
 */
@Composable
private fun PlayFeedback(
    state: RoomState,
    visual: BuzzerVisual,
    remaining: Long?,
    myId: String,
    amHost: Boolean,
    soundFx: SoundFx,
) {
    var lastTick by remember { mutableIntStateOf(-1) }
    // L'animateur coupe l'habillage pour tout le salon ; chacun peut aussi le couper pour lui
    // seul depuis les réglages, ce que porte déjà `soundFx.enabled`.
    val roomSound = state.options.sound

    LaunchedEffect(state.round, state.roundState) {
        if (state.roundState == RoundState.IDLE) lastTick = -1
    }

    val tick = remaining?.let { ((it / 1000) + 1).toInt() }
    LaunchedEffect(tick, roomSound) {
        if (roomSound && tick != null && tick != lastTick) {
            lastTick = tick
            soundFx.tick()
        }
    }

    // Chaque couleur a son son : le vert de la main, le rouge de la mauvaise réponse, le bleu
    // de la manche perdue.
    LaunchedEffect(visual, state.round) {
        if (!roomSound) return@LaunchedEffect
        when (visual) {
            BuzzerVisual.ARMED -> soundFx.go()
            BuzzerVisual.BUZZED -> soundFx.buzz()
            BuzzerVisual.SPEAKING -> soundFx.yourTurn()
            BuzzerVisual.LOST -> soundFx.locked()
            else -> Unit
        }
    }

    // La sanction s'entend des deux côtés : chez celui qui s'est trompé, et chez l'animateur
    // qui vient de la prononcer.
    LaunchedEffect(state.wrongId, state.round) {
        val wrong = state.wrongId ?: return@LaunchedEffect
        if (!roomSound) return@LaunchedEffect
        if (wrong == myId || amHost) soundFx.wrong()
    }

    // La récompense aussi : chez celui qui vient de marquer, et chez l'animateur qui l'a validé.
    LaunchedEffect(state.rightId, state.round) {
        val right = state.rightId ?: return@LaunchedEffect
        if (!roomSound) return@LaunchedEffect
        if (right == myId || amHost) soundFx.correct()
    }
}
