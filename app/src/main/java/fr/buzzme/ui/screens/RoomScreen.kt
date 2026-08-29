package fr.buzzme.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.buzzme.core.AppClock
import fr.buzzme.core.SoundFx
import fr.buzzme.game.LinkPhase
import fr.buzzme.game.RoomSession
import fr.buzzme.model.Buzz
import fr.buzzme.model.BuzzerVisual
import fr.buzzme.model.GameMode
import fr.buzzme.model.Player
import fr.buzzme.model.PlayerStatus
import fr.buzzme.model.RoomState
import fr.buzzme.model.RoundState
import fr.buzzme.model.visualFor
import fr.buzzme.ui.components.BigBuzzer
import fr.buzzme.ui.components.CodeDisplay
import fr.buzzme.ui.components.GhostAction
import fr.buzzme.ui.components.IconAction
import fr.buzzme.ui.components.PlayerRow
import fr.buzzme.ui.components.PrimaryAction
import fr.buzzme.ui.components.SectionLabel
import fr.buzzme.ui.components.StageBackground
import fr.buzzme.ui.components.StageBadge
import fr.buzzme.ui.components.StatusDot
import fr.buzzme.ui.theme.MonoDigits
import fr.buzzme.ui.theme.Stage
import kotlinx.coroutines.delay

@Composable
fun RoomScreen(
    session: RoomSession,
    soundFx: SoundFx,
    onLeave: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val link by session.link.collectAsStateWithLifecycle()
    val localBuzzRound by session.localBuzzRound.collectAsStateWithLifecycle()

    val amHost = state.hostId == session.myId
    var showOptions by remember { mutableStateOf(false) }
    var selectedPlayer by remember { mutableStateOf<String?>(null) }

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

    PlayFeedback(state = state, visual = myVisual, remaining = remaining, soundFx = soundFx)

    // Le buzzer est taillé sur la hauteur de l'écran, pas sur ce qui reste une fois le bandeau
    // de résultat affiché : sur un petit téléphone il garde une taille jouable, sur un grand il
    // ne flotte pas au milieu du vide.
    val buzzerHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.32f)
        .coerceIn(150.dp, 300.dp)

    StageBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            RoomHeader(
                state = state,
                linkPhase = link.phase,
                linkDetail = link.detail,
                pingMillis = link.pingMillis,
                precisionMillis = link.clockPrecisionMillis,
                amHost = amHost,
                onLeave = onLeave,
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buzzerHeight),
                contentAlignment = Alignment.Center,
            ) {
                // fillMaxHeight + aspectRatio(1f) côté buzzer : le dôme reste rond quelle que
                // soit la largeur de l'écran.
                BigBuzzer(
                    visual = myVisual,
                    title = buzzerTitle(myVisual, remaining, state, session.myId),
                    subtitle = buzzerSubtitle(myVisual, state, session.myId),
                    onPress = { uptime -> session.buzz(AppClock.wallFromUptime(uptime)) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            Spacer(Modifier.height(10.dp))

            if (amHost) {
                HostControls(
                    state = state,
                    onArm = session::arm,
                    onReset = session::reset,
                    onOptions = { showOptions = true },
                )
                Spacer(Modifier.height(12.dp))
            }

            ResultBanner(
                state = state,
                myId = session.myId,
                amHost = amHost,
                onWrongAnswer = session::passSpeaker,
            )

            Spacer(Modifier.height(8.dp))

            // Le plateau tel qu'il s'affiche ici : c'est aussi lui que compte le « x/y en ligne ».
            val ordered = remember(state, amHost) {
                orderPlayers(state, keepEliminated = amHost, myId = session.myId)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    when {
                        // Rappel utile des deux côtés : l'animateur sait ce qu'il a coupé, le
                        // joueur comprend pourquoi les pastilles des autres sont barrées.
                        state.options.hideScores -> "Plateau · scores masqués"
                        amHost -> "Plateau · appuyez sur un joueur"
                        else -> "Plateau"
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${ordered.count { it.connected }}/${ordered.size} en ligne",
                    style = MaterialTheme.typography.labelMedium,
                    color = Stage.TextMuted,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Les buzz réordonnent le plateau : sans cela, la liste suivrait le joueur qui était
            // en tête et afficherait une ligne coupée au lieu du classement de la manche.
            val boardState = rememberLazyListState()
            LaunchedEffect(state.round, state.winnerId) {
                if (boardState.firstVisibleItemIndex != 0 || boardState.firstVisibleItemScrollOffset != 0) {
                    boardState.animateScrollToItem(0)
                }
            }
            LazyColumn(
                state = boardState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(ordered, key = { it.id }) { player ->
                    PlayerRow(
                        player = player,
                        visual = state.visualFor(
                            player.id,
                            nowHost,
                            if (player.id == session.myId) localBuzzRound else null,
                        ),
                        buzz = state.buzzOf(player.id),
                        gapMillis = state.gapOf(player.id),
                        rank = state.rankOf(player.id),
                        isWinner = state.winnerId == player.id,
                        isHost = state.hostId == player.id,
                        isMe = session.myId == player.id,
                        showControls = amHost,
                        onClick = { selectedPlayer = player.id },
                        // Scores masqués : chaque joueur garde le sien sous les yeux, et
                        // l'animateur continue de voir le tableau entier.
                        showScore = amHost ||
                            !state.options.hideScores ||
                            player.id == session.myId,
                    )
                }
            }
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

    if (showOptions) {
        RoomOptionsDialog(
            state = state,
            amHost = amHost,
            onDismiss = { showOptions = false },
            onOptions = session::setOptions,
            onResetScores = session::resetScores,
        )
    }
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

private fun buzzerTitle(
    visual: BuzzerVisual,
    remaining: Long?,
    state: RoomState,
    myId: String,
): String = when (visual) {
    BuzzerVisual.COUNTDOWN -> remaining?.let { ((it / 1000) + 1).coerceAtMost(9L).toString() } ?: "!"
    BuzzerVisual.ARMED -> "BUZZ"
    BuzzerVisual.BUZZED,
    BuzzerVisual.SPEAKING,
    -> state.buzzOf(myId)?.let { Buzz.formatReaction(it.reactionMillis) } ?: "PRIS"
    BuzzerVisual.LOST -> if (myId in state.passedIds) "RATÉ" else "TROP TARD"
    BuzzerVisual.ELIMINATED -> "ÉLIMINÉ"
    BuzzerVisual.OFF -> "PRÊT ?"
}

private fun buzzerSubtitle(visual: BuzzerVisual, state: RoomState, myId: String): String = when (visual) {
    BuzzerVisual.COUNTDOWN -> "Préparez-vous"
    BuzzerVisual.ARMED -> "Appuyez !"
    BuzzerVisual.BUZZED -> "Enregistré"
    BuzzerVisual.SPEAKING -> "À vous de répondre"
    // Écarté par l'animateur, ou devancé : dans les deux cas on nomme qui a la parole.
    BuzzerVisual.LOST -> state.speakerId?.let { state.player(it)?.name.orEmpty() } ?: ""
    BuzzerVisual.ELIMINATED -> "L'animateur peut vous réactiver"
    BuzzerVisual.OFF -> "En attente du top"
}

// ------------------------------------------------------------------- en-tête

@Composable
private fun RoomHeader(
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
                    contentDescription = "Quitter le salon",
                    tint = Stage.TextSecondary,
                )
            }
            Spacer(Modifier.width(6.dp))
            // Le code prend la place qui reste une fois les badges mesurés : c'est lui qui se
            // resserre sur un écran étroit, jamais le badge « Animateur » qui viendrait le coller.
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Code du salon")
                Spacer(Modifier.height(4.dp))
                CodeDisplay(code = state.code)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                StageBadge(
                    text = if (amHost) "Animateur" else "Joueur",
                    color = if (amHost) Stage.Gold else Stage.VioletSoft,
                )
                Spacer(Modifier.height(6.dp))
                StageBadge(
                    text = if (state.options.mode == GameMode.DUEL) "Duel" else "Course",
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
                text = linkDetail.ifBlank { "Manche ${state.round}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Stage.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (pingMillis > 0) {
                // Latence de la liaison, et incertitude de la synchronisation d'horloge :
                // en dessous de cet écart, deux réflexes ne sont pas départageables.
                Text(
                    text = "$pingMillis ms · ± $precisionMillis ms",
                    style = MonoDigits,
                    color = Stage.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------ pupitre animateur

@Composable
private fun HostControls(
    state: RoomState,
    onArm: () -> Unit,
    onReset: () -> Unit,
    onOptions: () -> Unit,
) {
    val armed = state.roundState == RoundState.ARMED || state.roundState == RoundState.COUNTDOWN
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryAction(
            text = if (armed) "Relancer" else "Top !",
            icon = Icons.Filled.Bolt,
            onClick = onArm,
            colors = listOf(Stage.Gold, Color(0xFFDE9A12)),
            modifier = Modifier.weight(1f),
        )
        IconAction(
            icon = Icons.Filled.Refresh,
            contentDescription = "Réinitialiser les buzzers",
            onClick = onReset,
            accent = Stage.TextSecondary,
        )
        IconAction(
            icon = Icons.Filled.Tune,
            contentDescription = "Règles de la partie",
            onClick = onOptions,
        )
    }
}

// ------------------------------------------------------------------- bandeau

@Composable
private fun ResultBanner(
    state: RoomState,
    myId: String,
    amHost: Boolean,
    onWrongAnswer: () -> Unit,
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
                    text = if (winner.id == myId) "À vous !" else "${winner.name} a la main",
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
                append("Réaction ")
                append(Buzz.formatReaction(buzz.reactionMillis))
                if (buzz.precisionMillis > 0) append(" (± ${buzz.precisionMillis} ms)")
                if (next != null) {
                    val gap = next.atHostMillis - buzz.atHostMillis
                    append(" · devance ")
                    append(state.player(next.playerId)?.name.orEmpty())
                    append(" de ")
                    append(Buzz.formatGap(gap))
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
                    text = "Photo-finish : arbitrage en cours…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stage.Amber,
                    textAlign = TextAlign.Start,
                )
            } else if (amHost) {
                Spacer(Modifier.height(10.dp))
                GhostAction(
                    text = if (next != null) "Mauvaise réponse · au suivant" else "Mauvaise réponse",
                    icon = Icons.Filled.Close,
                    onClick = onWrongAnswer,
                    accent = Stage.Red,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            text = "Personne n'a trouvé · relancez une manche",
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

    // L'état précédent départage deux passages au gris qui ne racontent pas la même chose :
    // s'être fait coiffer au poteau, ou s'être trompé et perdre la parole.
    var previous by remember { mutableStateOf(visual) }
    LaunchedEffect(visual, state.round) {
        val before = previous
        previous = visual
        if (!roomSound) return@LaunchedEffect
        when {
            visual == BuzzerVisual.ARMED -> soundFx.go()
            visual == BuzzerVisual.BUZZED -> soundFx.buzz()
            visual == BuzzerVisual.SPEAKING -> soundFx.yourTurn()
            visual == BuzzerVisual.LOST && before == BuzzerVisual.SPEAKING -> soundFx.wrong()
            visual == BuzzerVisual.LOST -> soundFx.locked()
            else -> Unit
        }
    }
}
