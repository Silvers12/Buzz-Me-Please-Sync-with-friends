package com.osala.BuzzMePlease.net.lan

import android.content.Context
import android.util.Log
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppClock
import com.osala.BuzzMePlease.core.AppLocale
import com.osala.BuzzMePlease.game.BuzzOutcome
import com.osala.BuzzMePlease.game.GameEngine
import com.osala.BuzzMePlease.game.LinkPhase
import com.osala.BuzzMePlease.game.LinkStatus
import com.osala.BuzzMePlease.game.RoomSession
import com.osala.BuzzMePlease.game.SessionEnded
import com.osala.BuzzMePlease.model.PlayerStatus
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.model.RoomState
import com.osala.BuzzMePlease.model.RoundState
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val clock = ClockSync()

    @Volatile
    private var myName: String = initialName

    private val _state = MutableStateFlow(
        RoomState(code = code, hostId = if (startAsHost) myId else "", options = initialOptions),
    )
    override val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _link = MutableStateFlow(LinkStatus())
    override val link: StateFlow<LinkStatus> = _link.asStateFlow()

    private val _ended = MutableStateFlow<SessionEnded?>(null)
    override val ended: StateFlow<SessionEnded?> = _ended.asStateFlow()

    private val _localBuzzRound = MutableStateFlow<Int?>(null)
    override val localBuzzRound: StateFlow<Int?> = _localBuzzRound.asStateFlow()

    // -- rôle hôte
    @Volatile
    private var engine: GameEngine? = null
    private var server: LanServer? = null
    private var engineJob: Job? = null
    private var countdownJob: Job? = null
    private var adjudicationJob: Job? = null

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
        advertiser.register(code, myName)
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
            if (hello.protocol != PROTOCOL_VERSION) {
                peer.send(Bye(ByeCause.VERSION_MISMATCH))
                scope.launch { delay(200); server?.disconnect(hello.playerId) }
                return
            }
            val current = engine ?: return
            current.join(hello.playerId, hello.name, AppClock.wallNow())
            peer.send(Welcome(code, myId))
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
        val hostTime = request.clientWall + request.offset
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
        peer.send(Hello(myId, myName))
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
            peer.send(Ping(seq++, AppClock.wallNow()))
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
                clock.record(message.clientSent, message.hostWall, AppClock.wallNow())
                _link.value = _link.value.copy(
                    phase = LinkPhase.CONNECTED,
                    detail = text(R.string.link_connected),
                    pingMillis = clock.lastRttMillis,
                    clockPrecisionMillis = clock.precisionMillis,
                )
            }

            is StateSync -> {
                val incoming = message.state
                _state.value = incoming
                // La manche a changé : l'affichage optimiste local n'a plus lieu d'être.
                if (_localBuzzRound.value != null && _localBuzzRound.value != incoming.round) {
                    _localBuzzRound.value = null
                }
                if (incoming.player(myId) == null) {
                    // Retiré du salon par l'hôte.
                    _ended.value = SessionEnded(text(R.string.end_kicked), kicked = true)
                }
            }

            is Welcome -> Unit

            is HostTransfer -> onHostTransfer(message)

            is Bye -> {
                _ended.value = SessionEnded(text(message.cause.messageRes()), message.kicked)
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

    override fun buzz(atWallMillis: Long) {
        val current = _state.value
        if (!current.canBuzz(myId, nowHostMillis())) return
        // Retour visuel immédiat : le buzzer vire au rouge sans attendre l'aller-retour réseau.
        _localBuzzRound.value = current.round

        val host = engine
        if (host != null) {
            val outcome = host.registerBuzz(myId, current.round, atWallMillis, 0)
            if (outcome == BuzzOutcome.FIRST) startAdjudication(current.round)
        } else {
            clientLink?.send(
                BuzzRequest(
                    round = current.round,
                    clientWall = atWallMillis,
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
        _localBuzzRound.value = null
        host.reset()
    }

    override fun passSpeaker() {
        val host = engine ?: return
        host.passSpeaker(host.snapshot.round)
    }

    override fun setStatus(playerId: String, status: PlayerStatus) {
        engine?.setStatus(playerId, status)
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
    override fun nowHostMillis(): Long {
        val now = AppClock.wallNow()
        return if (engine != null) now else clock.toHostTime(now)
    }

    override fun toHostMillis(localWallMillis: Long): Long =
        if (engine != null) localWallMillis else clock.toHostTime(localWallMillis)

    companion object {
        private const val TAG = "LanRoomSession"
        const val COUNTDOWN_MILLIS = 3_000L
        private const val CONNECT_TIMEOUT_MILLIS = 3_000
        private const val DISCOVERY_TIMEOUT_MILLIS = 6_000L
        private const val RETRY_MILLIS = 900L
        private const val RECONNECT_MILLIS = 400L
        private const val STALE_ADDRESS_ATTEMPTS = 3
        private const val PING_INTERVAL_MILLIS = 2_000L

        /** Trois sondes sans réponse : la liaison avec l'hôte est morte, on repart de zéro. */
        private const val HOST_SILENCE_MILLIS = 6_000L
        private const val WATCHDOG_TICK_MILLIS = 1_000L
        private const val BURST_INTERVAL_MILLIS = 150L
        private const val BURST_COUNT = 8
        private const val HANDOVER_FLUSH_MILLIS = 300L
        private const val CLOSE_FLUSH_MILLIS = 200L
    }
}
