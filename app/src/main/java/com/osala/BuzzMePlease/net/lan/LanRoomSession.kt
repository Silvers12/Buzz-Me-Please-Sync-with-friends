package com.osala.BuzzMePlease.net.lan

import android.content.Context
import android.util.Log
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppClock
import com.osala.BuzzMePlease.core.appVersionCode
import com.osala.BuzzMePlease.core.appVersionName
import com.osala.BuzzMePlease.core.AppLocale
import com.osala.BuzzMePlease.game.BuzzOutcome
import com.osala.BuzzMePlease.game.GameEngine
import com.osala.BuzzMePlease.game.LinkPhase
import com.osala.BuzzMePlease.game.LinkStatus
import com.osala.BuzzMePlease.game.RoomSession
import com.osala.BuzzMePlease.game.SessionEnded
import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.AlertKind
import com.osala.BuzzMePlease.model.RoomAlert
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
import com.osala.BuzzMePlease.model.RoundState
import com.osala.BuzzMePlease.net.AlertBroadcast
import com.osala.BuzzMePlease.net.Bye
import com.osala.BuzzMePlease.net.ByeCause
import com.osala.BuzzMePlease.net.BuzzRequest
import com.osala.BuzzMePlease.net.messageRes
import com.osala.BuzzMePlease.net.GAME_PORT
import com.osala.BuzzMePlease.net.Hello
import com.osala.BuzzMePlease.net.HostTransfer
import com.osala.BuzzMePlease.net.NetMessage
import com.osala.BuzzMePlease.net.PROTOCOL_VERSION
import com.osala.BuzzMePlease.net.Ping
import com.osala.BuzzMePlease.net.Pong
import com.osala.BuzzMePlease.net.RenameRequest
import com.osala.BuzzMePlease.net.StateSync
import com.osala.BuzzMePlease.net.Welcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * Salon en Wi-Fi local, sans serveur ni Internet.
 *
 * Topologie en étoile autour de l'hôte : les joueurs ouvrent une connexion TCP persistante
 * (Nagle désactivé) vers lui, mesurent en continu le décalage de leur horloge, et envoient
 * l'heure exacte de leur appui. L'hôte tranche.
 *
 * La même instance sait tenir les deux rôles : la passation d'hôte bascule le serveur d'un
 * appareil à l'autre sans que personne ne quitte le salon.
 */
class LanRoomSession(
    context: Context,
    override val myId: String,
    initialName: String,
    val code: String,
    startAsHost: Boolean,
    hostAddress: String? = null,
    private val initialOptions: RoomOptions = RoomOptions(),
) : RoomSession {

    private val appContext = context.applicationContext

    /**
     * La session possède son propre périmètre de coroutines : la fermeture du salon
     * (dernier message, libération des sockets) doit aboutir même si l'écran est déjà détruit.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val advertiser = NsdAdvertiser(appContext)

    /**
     * La version installée sur cet appareil. Tout le salon doit avoir la même : c'est le
     * seul contrat qui garantisse que chacun voit le même plateau et joue aux mêmes règles.
     */
    private val appVersion = appContext.appVersionCode()

    /** La même version, telle qu'elle se lit : « 1.06 ». Annoncée avec le salon. */
    private val appVersionLabel = appContext.appVersionName()
    private val clock = ClockSync()

    @Volatile
    private var myName: String = initialName

    private val _state = MutableStateFlow(
        RoomState(code = code, hostId = if (startAsHost) myId else "", options = initialOptions),
    )
    override val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _link = MutableStateFlow(LinkStatus())
    override val link: StateFlow<LinkStatus> = _link.asStateFlow()

    private val _joined = MutableStateFlow(startAsHost)
    override val joined: StateFlow<Boolean> = _joined.asStateFlow()

    private val _ended = MutableStateFlow<SessionEnded?>(null)
    override val ended: StateFlow<SessionEnded?> = _ended.asStateFlow()

    private val _localBuzzRound = MutableStateFlow<Int?>(null)
    override val localBuzzRound: StateFlow<Int?> = _localBuzzRound.asStateFlow()

    // Un événement, donc aucune valeur conservée : qui arrive après l'annonce ne la reçoit pas.
    // Le tampon évite qu'une alerte se perde si l'écran n'écoute pas encore.
    private val _alerts = MutableSharedFlow<RoomAlert>(extraBufferCapacity = 4)
    override val alerts: SharedFlow<RoomAlert> = _alerts.asSharedFlow()

    // -- rôle hôte
    @Volatile
    private var engine: GameEngine? = null
    private var server: LanServer? = null
    private var engineJob: Job? = null
    private var countdownJob: Job? = null
    private var adjudicationJob: Job? = null
    private var rightJob: Job? = null

    // -- rôle joueur
    private var clientJob: Job? = null
    private var pingJob: Job? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var clientLink: PeerLink? = null

    @Volatile
    private var targetAddress: String? = hostAddress

    @Volatile
    private var closing = false

    init {
        if (startAsHost) becomeHost(null) else becomeGuest(hostAddress)
    }

    /** Les messages affichés ici le sont sur cet appareil : ils suivent la langue choisie. */
    private fun text(resId: Int, vararg args: Any): String =
        with(AppLocale.wrap(appContext)) {
            if (args.isEmpty()) getString(resId) else getString(resId, *args)
        }

    override val isHost: Boolean get() = engine != null

    // ======================================================================== hôte

    private fun becomeHost(restoreFrom: RoomState?) {
        stopGuest()
        val created = GameEngine(code, myId, restoreFrom?.options ?: initialOptions)
        if (restoreFrom != null) created.restore(restoreFrom, myId)
        created.join(myId, myName, AppClock.wallNow())
        created.setPing(myId, 0)
        engine = created
        _joined.value = true
        clock.reset()

        engineJob = scope.launch {
            created.state.collect { snapshot ->
                _state.value = snapshot
                server?.broadcast(StateSync(snapshot))
            }
        }

        val lan = LanServer(appContext, scope, hostCallbacks())
        server = lan
        lan.start()
        advertiser.register(code, myName, appVersionLabel, appVersion)
        _link.value = LinkStatus(LinkPhase.CONNECTED, text(R.string.link_hosting))
    }

    // Fabriqué à la demande, et non stocké dans une propriété : le bloc `init` ci-dessus prend
    // déjà le rôle d'hôte, avant que les propriétés déclarées plus bas ne soient initialisées.
    private fun hostCallbacks(): LanServer.Callbacks = object : LanServer.Callbacks {

        override fun onListening() {
            _link.value = LinkStatus(LinkPhase.CONNECTED, text(R.string.link_hosting))
        }

        override fun onError(message: String) {
            _link.value = LinkStatus(LinkPhase.ERROR, message)
        }

        override fun onHello(peer: PeerLink, hello: Hello) {
            // On se présente d'abord, même à qui va être refusé : c'est ainsi qu'il apprend
            // notre version, et qu'il peut dire à son porteur laquelle mettre à jour.
            peer.send(Welcome(code, myId, appVersion = appVersion))
            // Une version différente, c'est un salon incohérent : un buzzer rouge ici et bleu
            // là-bas, une annonce qui n'arrive jamais. On refuse la porte plutôt que de
            // laisser la partie se dérégler en silence.
            if (hello.appVersion != appVersion) {
                peer.send(Bye(ByeCause.VERSION_MISMATCH))
                scope.launch { delay(200); server?.disconnect(hello.playerId) }
                return
            }
            val current = engine ?: return
            current.join(hello.playerId, hello.name, AppClock.wallNow())
            peer.send(StateSync(current.snapshot))
        }

        override fun onMessage(peer: PeerLink, message: NetMessage) {
            val playerId = peer.playerId ?: return
            val current = engine ?: return
            when (message) {
                // Répondu depuis la boucle de lecture : aucun détour, la mesure reste juste.
                is Ping -> peer.send(Pong(message.seq, message.clientSent, AppClock.wallNow()))

                is BuzzRequest -> handleBuzz(current, playerId, message)

                is RenameRequest -> current.rename(playerId, message.name)

                else -> Unit
            }
        }

        override fun onGone(peer: PeerLink) {
            val playerId = peer.playerId ?: return
            // Une reconnexion remplace la connexion précédente : la fermeture de l'ancienne
            // ne doit pas faire passer le joueur pour absent.
            if (server?.isConnected(playerId) == true) return
            engine?.setConnected(playerId, false)
        }
    }

    private fun handleBuzz(current: GameEngine, playerId: String, request: BuzzRequest) {
        // L'heure annoncée par le joueur, ramenée sur celle de l'hôte. Elle vient de sa base
        // monotone, qu'aucun réglage d'heure ne déplace — mais rien n'empêche une application
        // modifiée d'annoncer ce qu'elle veut. L'hôte sait au moins quand le message lui est
        // parvenu : un buzz prétendument plus vieux que ce que le réseau permet est ramené à
        // cette limite, faute de quoi il suffirait d'antidater son appui pour gagner à coup sûr.
        val floor = AppClock.wallNow() - MAX_BACKDATE_MILLIS
        val hostTime = maxOf(request.clientWall + request.offset, floor)
        val precision = request.rtt / 2
        val outcome = current.registerBuzz(playerId, request.round, hostTime, precision)
        if (outcome == BuzzOutcome.FIRST) startAdjudication(request.round)
        // La mesure d'aller-retour du joueur nous sert d'indicateur de qualité de liaison.
        publishPing(playerId, request.rtt)
    }

    /**
     * Le verrouillage part tout de suite, mais l'hôte garde la fenêtre ouverte le temps qu'un
     * buzz déjà en vol nous parvienne. Sans cela, le gagnant serait « celui dont le paquet est
     * arrivé en premier » et non « celui qui a appuyé en premier ».
     */
    private fun startAdjudication(round: Int) {
        adjudicationJob?.cancel()
        adjudicationJob = scope.launch {
            delay(GameEngine.ADJUDICATION_MILLIS)
            engine?.closeAdjudication(round)
        }
    }

    private fun publishPing(playerId: String, rtt: Long) {
        val current = engine ?: return
        // Aucune écriture d'état pendant qu'un buzzer est armé : la manche doit rester silencieuse.
        if (current.snapshot.roundState != RoundState.IDLE) return
        val quantized = (rtt / 5) * 5
        current.setPing(playerId, quantized)
    }

    private fun stopHost() {
        countdownJob?.cancel(); countdownJob = null
        adjudicationJob?.cancel(); adjudicationJob = null
        engineJob?.cancel(); engineJob = null
        advertiser.unregister()
        server?.stop()
        server = null
        engine = null
    }

    // ====================================================================== joueur

    private fun becomeGuest(address: String?) {
        stopHost()
        targetAddress = address
        clock.reset()
        _link.value = LinkStatus(LinkPhase.SEARCHING, text(R.string.link_searching, code))
        clientJob = scope.launch { guestLoop() }
    }

    private suspend fun guestLoop() {
        var attempts = 0
        while (coroutineContext.isActive && !closing) {
            val address = targetAddress ?: run {
                _link.value =
                    LinkStatus(LinkPhase.SEARCHING, text(R.string.link_searching_wifi, code))
                NsdBrowser.findRoom(appContext, code, DISCOVERY_TIMEOUT_MILLIS)?.address
            }
            if (address == null) {
                attempts++
                _link.value = LinkStatus(
                    LinkPhase.SEARCHING,
                    text(R.string.link_not_found, code),
                )
                delay(RETRY_MILLIS)
                continue
            }
            targetAddress = address

            _link.value = LinkStatus(LinkPhase.CONNECTING, text(R.string.link_connecting))
            val socket = openSocket(address)
            if (socket == null) {
                attempts++
                // Après quelques échecs, l'adresse mémorisée est probablement périmée.
                if (attempts >= STALE_ADDRESS_ATTEMPTS) {
                    targetAddress = null
                    attempts = 0
                }
                _link.value = LinkStatus(LinkPhase.RECONNECTING, text(R.string.link_retrying))
                delay(RETRY_MILLIS)
                continue
            }

            attempts = 0
            runConnection(socket)
            if (closing || !coroutineContext.isActive) break
            _link.value = LinkStatus(LinkPhase.RECONNECTING, text(R.string.link_lost))
            delay(RECONNECT_MILLIS)
        }
    }

    private suspend fun openSocket(address: String): Socket? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().apply { connect(InetSocketAddress(address, GAME_PORT), CONNECT_TIMEOUT_MILLIS) }
        }.onFailure { Log.d(TAG, "connexion à $address impossible: ${it.message}") }.getOrNull()
    }

    private suspend fun runConnection(socket: Socket) {
        val peer = PeerLink(socket).apply {
            playerId = myId
            startWriter(scope)
        }
        clientLink = peer
        clock.reset()
        peer.send(Hello(myId, myName, appVersion = appVersion))
        _link.value = LinkStatus(LinkPhase.CONNECTED, text(R.string.link_connected))

        pingJob?.cancel()
        pingJob = scope.launch { pingLoop(peer) }
        watchdogJob?.cancel()
        watchdogJob = scope.launch { watchHost(peer) }

        withContext(Dispatchers.IO) { peer.readLoop { onGuestMessage(it) } }

        pingJob?.cancel(); pingJob = null
        watchdogJob?.cancel(); watchdogJob = null
        peer.close()
        if (clientLink === peer) clientLink = null
    }

    /**
     * L'hôte répond à chaque sonde : son silence prolongé veut dire que la liaison est morte
     * sans que TCP l'ait signalé — Wi-Fi qui décroche, téléphone qui s'endort, hôte qui a
     * planté. La lecture attendrait alors indéfiniment et le buzzer resterait éteint au go,
     * comme si le joueur avait quitté le salon. On coupe donc nous-mêmes : la boucle de
     * connexion enchaîne aussitôt sur une reconnexion, sans rien demander à personne.
     */
    private suspend fun watchHost(peer: PeerLink) {
        while (coroutineContext.isActive) {
            delay(WATCHDOG_TICK_MILLIS)
            if (peer.silentForMillis() <= HOST_SILENCE_MILLIS) continue
            Log.d(TAG, "hôte muet depuis ${peer.silentForMillis()} ms, on se reconnecte")
            peer.close()
            return
        }
    }

    /**
     * Sonde d'horloge. Rafale rapide au début — pour être synchronisé avant le premier top —
     * puis entretien régulier afin d'absorber toute dérive.
     */
    private suspend fun pingLoop(peer: PeerLink) {
        var seq = 0
        var burst = BURST_COUNT
        while (coroutineContext.isActive) {
            peer.send(Ping(seq++, AppClock.elapsedNow()))
            if (burst > 0) {
                burst--
                delay(BURST_INTERVAL_MILLIS)
            } else {
                delay(PING_INTERVAL_MILLIS)
            }
        }
    }

    private fun onGuestMessage(message: NetMessage) {
        when (message) {
            is Pong -> {
                clock.record(message.clientSent, message.hostWall, AppClock.elapsedNow())
                _link.value = _link.value.copy(
                    phase = LinkPhase.CONNECTED,
                    detail = text(R.string.link_connected),
                    pingMillis = clock.lastRttMillis,
                    clockPrecisionMillis = clock.precisionMillis,
                )
            }

            is StateSync -> {
                // Le salon a répondu : à partir d'ici, il y a quelque chose à montrer.
                _joined.value = true
                val incoming = message.state
                _state.value = incoming
                // L'affichage optimiste ne sert qu'à devancer l'aller-retour du buzz. Il n'a
                // plus rien à devancer dès que la manche change, ni quand elle revient au
                // repos : l'hôte a effacé les buzz, et le buzzer resterait bleu « PRIS » sur
                // un plateau éteint — c'est ce qui se voyait après un « vrai ».
                val optimistic = _localBuzzRound.value
                if (optimistic != null &&
                    (optimistic != incoming.round || incoming.roundState == RoundState.IDLE)
                ) {
                    _localBuzzRound.value = null
                }
                if (incoming.player(myId) == null) {
                    // Retiré du salon par l'hôte.
                    _ended.value = SessionEnded(text(R.string.end_kicked), kicked = true)
                }
            }

            is Welcome -> {
                // Le contrôle vaut dans les deux sens, et il se fait ici plutôt qu'à la porte :
                // seul le joueur connaît les deux versions, donc seul lui peut dire laquelle
                // des deux doit être mise à jour.
                if (message.appVersion != appVersion) {
                    val advice = if (message.appVersion > appVersion) {
                        R.string.end_version_update
                    } else {
                        R.string.end_version_host_old
                    }
                    _ended.value = SessionEnded(text(advice))
                    closing = true
                    clientLink?.close()
                }
            }

            // Une annonce de l'animateur : elle traverse et s'affiche, sans toucher à l'état.
            is AlertBroadcast -> scope.launch { _alerts.emit(message.alert) }

            is HostTransfer -> onHostTransfer(message)

            is Bye -> {
                // Un message déjà posé — la version, que nous seuls savons formuler — ne se
                // fait pas écraser par le motif générique de l'hôte.
                if (_ended.value == null) {
                    _ended.value = SessionEnded(text(message.cause.messageRes()), message.kicked)
                }
                closing = true
                clientLink?.close()
            }

            else -> Unit
        }
    }

    private fun onHostTransfer(message: HostTransfer) {
        _state.value = message.state
        if (message.newHostId == myId) {
            scope.launch {
                _link.value = LinkStatus(LinkPhase.STARTING, text(R.string.link_taking_over))
                becomeHost(message.state)
            }
        } else {
            targetAddress = message.address
            _link.value = LinkStatus(LinkPhase.RECONNECTING, text(R.string.link_new_host))
            clientLink?.close()
        }
    }

    private fun stopGuest() {
        clientJob?.cancel(); clientJob = null
        pingJob?.cancel(); pingJob = null
        watchdogJob?.cancel(); watchdogJob = null
        clientLink?.close()
        clientLink = null
    }

    // =================================================================== commandes

    override fun buzz(atUptimeMillis: Long) {
        val current = _state.value
        if (!current.canBuzz(myId, nowHostMillis())) return
        // Retour visuel immédiat : le buzzer vire au bleu sans attendre l'aller-retour réseau.
        _localBuzzRound.value = current.round

        val host = engine
        if (host != null) {
            // Chez l'hôte, l'heure du salon est son heure murale : l'appui s'y ramène directement.
            val outcome = host.registerBuzz(
                myId,
                current.round,
                AppClock.wallFromUptime(atUptimeMillis),
                0,
            )
            if (outcome == BuzzOutcome.FIRST) startAdjudication(current.round)
        } else {
            // Chez le joueur, l'appui est daté sur l'horloge monotone — celle qu'aucun réglage
            // d'heure ne déplace — et c'est elle que l'offset ramène sur l'heure de l'hôte.
            clientLink?.send(
                BuzzRequest(
                    round = current.round,
                    clientWall = AppClock.elapsedFromUptime(atUptimeMillis),
                    offset = clock.offsetMillis,
                    rtt = clock.rttMillis,
                ),
            )
        }
    }

    override fun rename(name: String) {
        val clean = name.trim().take(GameEngine.MAX_NAME)
        if (clean.isBlank()) return
        myName = clean
        val host = engine
        if (host != null) {
            host.rename(myId, clean)
            advertiser.register(code, clean)
        } else {
            clientLink?.send(RenameRequest(clean))
        }
    }

    override fun arm() {
        val host = engine ?: return
        val withCountdown = host.snapshot.options.countdown
        val armAt = AppClock.wallNow() + if (withCountdown) COUNTDOWN_MILLIS else 0L
        _localBuzzRound.value = null
        countdownJob?.cancel()
        adjudicationJob?.cancel()
        rightJob?.cancel(); rightJob = null
        host.arm(armAt, withCountdown)
        if (withCountdown) {
            val round = host.snapshot.round
            countdownJob = scope.launch {
                delay(COUNTDOWN_MILLIS)
                engine?.markArmed(round)
            }
        }
    }

    override fun reset() {
        val host = engine ?: return
        countdownJob?.cancel(); countdownJob = null
        adjudicationJob?.cancel(); adjudicationJob = null
        rightJob?.cancel(); rightJob = null
        _localBuzzRound.value = null
        host.reset()
    }

    override fun markWrong() {
        val host = engine ?: return
        rightJob?.cancel(); rightJob = null
        host.markWrong(host.snapshot.round)
    }

    override fun markRight() {
        val host = engine ?: return
        val round = host.snapshot.round
        host.markRight(round)
        if (host.snapshot.rightId == null) return
        rightJob?.cancel()
        // Le vert et le son ont le temps d'arriver jusqu'au bout de la table, puis les buzzers
        // s'éteignent d'eux-mêmes. Si l'animateur a déjà relancé entre-temps, on ne touche à rien.
        rightJob = scope.launch {
            delay(RIGHT_HOLD_MILLIS)
            val current = engine ?: return@launch
            if (current.snapshot.round == round && current.snapshot.rightId != null) reset()
        }
    }

    override fun passSpeaker() {
        val host = engine ?: return
        host.passSpeaker(host.snapshot.round)
    }

    override fun clearCards(playerId: String) {
        engine?.clearCards(playerId)
    }

    override fun giveFloor(playerId: String) {
        // Le verdict précédent est annulé avec la parole : le vert de la bonne réponse ne
        // doit pas éteindre les buzzers pendant qu'un autre parle.
        rightJob?.cancel(); rightJob = null
        engine?.giveFloor(playerId)
    }

    override fun setStatus(playerId: String, status: PlayerStatus) {
        engine?.setStatus(playerId, status)
    }

    override fun resetBoard() {
        _localBuzzRound.value = null
        engine?.resetBoard()
    }

    override fun kick(playerId: String) {
        val host = engine ?: return
        if (playerId == myId) return
        server?.sendTo(playerId, Bye(ByeCause.KICKED, kicked = true))
        host.remove(playerId)
        scope.launch {
            delay(250)
            server?.disconnect(playerId)
        }
    }

    override fun addPoints(playerId: String, delta: Int) {
        engine?.addPoints(playerId, delta)
    }

    override fun resetScores() {
        engine?.resetScores()
    }

    override fun transferHost(playerId: String) {
        val host = engine ?: return
        if (playerId == myId) return
        val lan = server ?: return
        val address = lan.addressOf(playerId) ?: return
        host.transferHost(playerId)
        val snapshot = host.snapshot
        lan.broadcast(HostTransfer(playerId, address, GAME_PORT, snapshot))
        scope.launch {
            // Laisse partir la diffusion, puis rend la main : le nouvel hôte ouvre son port
            // pendant que les autres tentent déjà de s'y reconnecter.
            delay(HANDOVER_FLUSH_MILLIS)
            _state.value = snapshot
            becomeGuest(address)
        }
    }

    override fun setOptions(options: RoomOptions) {
        engine?.setOptions(options)
    }

    /**
     * L'annonce part telle quelle sur le réseau, complétée par ce que l'hôte sait du salon : le
     * nom du joueur visé, ou celui du vainqueur. Elle est aussi jouée sur place — l'animateur
     * voit ce qu'il vient d'envoyer.
     */
    override fun sendAlert(alert: RoomAlert) {
        val host = engine ?: return
        val snapshot = host.snapshot
        val filled = when (alert.kind) {
            AlertKind.GAME_OVER -> {
                val ranking = snapshot.leaderboard
                val best = ranking.firstOrNull()
                // Personne n'a marqué, ou deux joueurs à égalité en tête : pas de vainqueur à
                // proclamer. Mieux vaut le dire que de désigner quelqu'un au hasard du tri.
                val tied = best == null || best.score == 0 ||
                    ranking.count { it.score == best.score } > 1
                alert.copy(
                    playerId = if (tied) "" else best?.id.orEmpty(),
                    playerName = if (tied) "" else best?.name.orEmpty(),
                    score = best?.score ?: 0,
                    tied = tied,
                )
            }

            else -> {
                val target = snapshot.player(alert.playerId) ?: return
                // Le carton part et se compte du même geste : il reste au tableau une fois
                // l'annonce passée.
                host.card(target.id, alert.kind)
                alert.copy(playerName = target.name, score = target.score)
            }
        }
        server?.broadcast(AlertBroadcast(filled))
        scope.launch { _alerts.emit(filled) }
    }

    override fun close() {
        if (closing) return
        closing = true
        if (engine != null) {
            server?.broadcast(Bye(ByeCause.HOST_CLOSED))
        } else {
            clientLink?.send(Bye(ByeCause.LEFT))
        }
        scope.launch {
            delay(CLOSE_FLUSH_MILLIS)
            stopHost()
            stopGuest()
            _link.value = LinkStatus(LinkPhase.CLOSED, text(R.string.link_closed))
            scope.cancel()
        }
    }

    /** Heure locale ramenée sur l'horloge de l'hôte (identité quand on est l'hôte). */
    override fun nowHostMillis(): Long =
        if (engine != null) AppClock.wallNow() else clock.toHostTime(AppClock.elapsedNow())

    companion object {
        private const val TAG = "LanRoomSession"
        const val COUNTDOWN_MILLIS = 3_000L

        /** Combien de temps le buzzer reste vert après « vrai », avant l'extinction générale. */
        const val RIGHT_HOLD_MILLIS = 1_600L
        private const val CONNECT_TIMEOUT_MILLIS = 3_000
        private const val DISCOVERY_TIMEOUT_MILLIS = 6_000L
        private const val RETRY_MILLIS = 900L
        private const val RECONNECT_MILLIS = 400L
        private const val STALE_ADDRESS_ATTEMPTS = 3
        private const val PING_INTERVAL_MILLIS = 2_000L

        /** Trois sondes sans réponse : la liaison avec l'hôte est morte, on repart de zéro. */
        private const val HOST_SILENCE_MILLIS = 6_000L
        private const val WATCHDOG_TICK_MILLIS = 1_000L

        /**
         * Antériorité maximale acceptée pour un buzz annoncé. Très au-delà de ce qu'un Wi-Fi
         * local demande, et bien en deçà de ce qu'une triche exigerait pour être utile.
         */
        private const val MAX_BACKDATE_MILLIS = 1_500L
        private const val BURST_INTERVAL_MILLIS = 150L
        private const val BURST_COUNT = 8
        private const val HANDOVER_FLUSH_MILLIS = 300L
        private const val CLOSE_FLUSH_MILLIS = 200L
    }
}
