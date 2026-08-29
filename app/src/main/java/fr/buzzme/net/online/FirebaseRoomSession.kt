package fr.buzzme.net.online

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import fr.buzzme.core.AppClock
import fr.buzzme.game.GameEngine
import fr.buzzme.game.LinkPhase
import fr.buzzme.game.LinkStatus
import fr.buzzme.game.RoomSession
import fr.buzzme.game.SessionEnded
import fr.buzzme.model.Buzz
import fr.buzzme.model.GameMode
import fr.buzzme.model.Player
import fr.buzzme.model.PlayerStatus
import fr.buzzme.model.RoomOptions
import fr.buzzme.model.RoomState
import fr.buzzme.model.RoundState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Salon en ligne, adossé à Firebase Realtime Database.
 *
 * Repli quand le Wi-Fi local n'est pas partageable (données mobiles, réseaux d'entreprise
 * qui isolent les clients, joueurs à distance). La base de temps commune est celle du serveur
 * Firebase (`.info/serverTimeOffset`), ce qui permet de comparer les buzz malgré des horloges
 * d'appareils différentes — avec une précision de l'ordre de la dizaine de millisecondes,
 * moins fine que le mode local.
 *
 * Le classement n'est calculé par personne en particulier : chaque appareil trie les buzz
 * horodatés qu'il reçoit, donc tout le monde aboutit au même gagnant.
 */
class FirebaseRoomSession(
    context: Context,
    config: FirebaseConfig,
    override val myId: String,
    initialName: String,
    val code: String,
    private val startAsHost: Boolean,
    private val initialOptions: RoomOptions = RoomOptions(),
) : RoomSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = FirebaseHolder.database(context, config)
    private val roomRef: DatabaseReference = database.getReference("rooms").child(code)

    private val _state = MutableStateFlow(
        RoomState(code = code, hostId = if (startAsHost) myId else "", options = initialOptions),
    )
    override val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _link = MutableStateFlow(LinkStatus(LinkPhase.STARTING, "Connexion à Firebase…"))
    override val link: StateFlow<LinkStatus> = _link.asStateFlow()

    private val _ended = MutableStateFlow<SessionEnded?>(null)
    override val ended: StateFlow<SessionEnded?> = _ended.asStateFlow()

    private val _localBuzzRound = MutableStateFlow<Int?>(null)
    override val localBuzzRound: StateFlow<Int?> = _localBuzzRound.asStateFlow()

    @Volatile
    private var serverOffsetMillis: Long = 0

    @Volatile
    private var myName: String = initialName

    @Volatile
    private var joined = false

    @Volatile
    private var closing = false

    /**
     * Numéro de la dernière manche lancée localement. L'état vient du serveur avec un aller-retour
     * de retard : sans ce garde-fou, deux appuis rapides sur « TOP ! » réutiliseraient le même
     * numéro de manche, et les buzz de la première seraient comptés dans la seconde.
     */
    @Volatile
    private var lastArmedRound = 0

    private var roomListener: ValueEventListener? = null
    private var offsetListener: ValueEventListener? = null
    private var connectedListener: ValueEventListener? = null
    private var countdownJob: Job? = null
    private var adjudicationJob: Job? = null

    private val offsetRef = database.getReference(".info/serverTimeOffset")
    private val connectedRef = database.getReference(".info/connected")

    init {
        runCatching { roomRef.keepSynced(true) }
        listenToServerClock()
        FirebaseHolder.signIn { start() }
    }

    override val isHost: Boolean get() = _state.value.hostId == myId

    // =============================================================== démarrage

    private fun listenToServerClock() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverOffsetMillis = snapshot.asLong() ?: 0
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        offsetListener = listener
        offsetRef.addValueEventListener(listener)
    }

    private fun start() {
        if (closing) return
        if (startAsHost) createRoom() else checkRoomThenJoin()
    }

    private fun createRoom() {
        val now = nowHostMillis()
        val payload: Map<String, Any?> = mapOf(
            "meta" to mapOf("code" to code, "hostId" to myId, "createdAt" to now),
            "options" to initialOptions.toMap(),
            "round" to roundMap(index = 0, state = RoundState.IDLE, armedAt = null),
        )
        roomRef.updateChildren(payload)
            .addOnFailureListener { fail("Écriture refusée par Firebase : ${it.message}") }
            .addOnSuccessListener { joinAsPlayer() }
    }

    private fun checkRoomThenJoin() {
        _link.value = LinkStatus(LinkPhase.CONNECTING, "Recherche du salon $code…")
        roomRef.child("meta").get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    _ended.value = SessionEnded("Aucun salon $code en ligne.")
                } else {
                    joinAsPlayer()
                }
            }
            .addOnFailureListener { fail("Lecture refusée par Firebase : ${it.message}") }
    }

    private fun joinAsPlayer() {
        val myRef = roomRef.child("players").child(myId)
        myRef.get().addOnSuccessListener { existing ->
            val values = mutableMapOf<String, Any?>(
                "name" to myName,
                "online" to true,
            )
            if (!existing.exists()) {
                values["status"] = PlayerStatus.ACTIVE.name
                values["score"] = 0
                values["joinedAt"] = nowHostMillis()
            }
            myRef.updateChildren(values)
                .addOnFailureListener { fail("Impossible de rejoindre : ${it.message}") }
                .addOnSuccessListener {
                    joined = true
                    _link.value = LinkStatus(LinkPhase.CONNECTED, "Connecté (en ligne)")
                    observeRoom()
                    observePresence(myRef)
                }
        }.addOnFailureListener { fail("Impossible de rejoindre : ${it.message}") }
    }

    /** Marque le joueur absent si l'application est tuée ou le réseau coupé. */
    private fun observePresence(myRef: DatabaseReference) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.getValue(Boolean::class.java) ?: false
                if (online && !closing) {
                    myRef.child("online").onDisconnect().setValue(false)
                    myRef.child("online").setValue(true)
                    _link.value = _link.value.copy(phase = LinkPhase.CONNECTED, detail = "Connecté (en ligne)")
                } else if (!online && !closing && joined) {
                    _link.value = _link.value.copy(phase = LinkPhase.RECONNECTING, detail = "Réseau perdu…")
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        connectedListener = listener
        connectedRef.addValueEventListener(listener)
    }

    private fun observeRoom() {
        if (roomListener != null) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (closing) return
                if (!snapshot.hasChild("meta")) {
                    _ended.value = SessionEnded("Le salon a été fermé par l'hôte.")
                    return
                }
                if (joined && !snapshot.child("players").hasChild(myId)) {
                    _ended.value = SessionEnded("Vous avez été retiré du salon.", kicked = true)
                    return
                }
                val next = snapshot.toRoomState()
                _state.value = next
                if (_localBuzzRound.value != null && _localBuzzRound.value != next.round) {
                    _localBuzzRound.value = null
                }
                scheduleAdjudicationRefresh(next)
            }

            override fun onCancelled(error: DatabaseError) {
                fail("Lecture interrompue : ${error.message}")
            }
        }
        roomListener = listener
        roomRef.addValueEventListener(listener)
    }

    /**
     * Pendant la fenêtre photo-finish le classement peut encore changer (un buzz plus rapide
     * mais plus lent à remonter). On force un recalcul à la fermeture de la fenêtre.
     */
    private fun scheduleAdjudicationRefresh(current: RoomState) {
        if (!current.provisional) return
        adjudicationJob?.cancel()
        adjudicationJob = scope.launch {
            delay(GameEngine.ADJUDICATION_MILLIS)
            val latest = _state.value
            if (latest.provisional) _state.value = latest.copy(provisional = false)
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        _link.value = LinkStatus(LinkPhase.ERROR, message)
    }

    // ================================================================= lecture

    private fun DataSnapshot.toRoomState(): RoomState {
        val options = child("options").toOptions()
        val hostId = child("meta").child("hostId").getValue(String::class.java).orEmpty()
        val roundIndex = child("round").child("index").asLong()?.toInt() ?: 0
        val declared = child("round").child("state").getValue(String::class.java)
            ?.let { name -> runCatching { RoundState.valueOf(name) }.getOrNull() }
            ?: RoundState.IDLE
        val armedAt = child("round").child("armedAt").asLong()

        val players = child("players").children.mapNotNull { it.toPlayer() }
            .sortedBy { it.joinedAt }

        val buzzes = child("buzzes").children
            .mapNotNull { it.toBuzz(roundIndex, armedAt) }
            .filter { buzz -> players.any { it.id == buzz.playerId && !it.isEliminated } }
            .sortedBy { it.atHostMillis }

        // Le verrouillage suit le premier buzz publié : il n'attend pas une écriture de l'hôte,
        // ce qui économise un aller-retour au moment le plus critique de la partie.
        val effective = when {
            declared == RoundState.IDLE -> RoundState.IDLE
            options.mode == GameMode.DUEL && buzzes.isNotEmpty() -> RoundState.LOCKED
            else -> declared
        }
        val firstAt = buzzes.firstOrNull()?.atHostMillis
        val provisional = firstAt != null &&
            options.mode == GameMode.DUEL &&
            nowHostMillis() - firstAt < GameEngine.ADJUDICATION_MILLIS

        return RoomState(
            code = code,
            hostId = hostId,
            round = roundIndex,
            roundState = effective,
            armedAtMillis = armedAt,
            players = players,
            buzzes = buzzes,
            winnerId = buzzes.firstOrNull()?.playerId,
            provisional = provisional,
            options = options,
        )
    }

    private fun DataSnapshot.toPlayer(): Player? {
        val id = key ?: return null
        val name = child("name").getValue(String::class.java) ?: return null
        val status = child("status").getValue(String::class.java)
            ?.let { runCatching { PlayerStatus.valueOf(it) }.getOrNull() }
            ?: PlayerStatus.ACTIVE
        return Player(
            id = id,
            name = name,
            status = status,
            score = child("score").asLong()?.toInt() ?: 0,
            connected = child("online").getValue(Boolean::class.java) ?: false,
            pingMillis = 0,
            joinedAt = child("joinedAt").asLong() ?: 0,
        )
    }

    private fun DataSnapshot.toBuzz(currentRound: Int, armedAt: Long?): Buzz? {
        val id = key ?: return null
        val round = child("round").asLong()?.toInt() ?: return null
        if (round != currentRound) return null
        val at = child("at").asLong() ?: return null
        val start = armedAt ?: return null
        val corrected = maxOf(at, start)
        return Buzz(
            playerId = id,
            round = round,
            atHostMillis = corrected,
            reactionMillis = corrected - start,
            precisionMillis = child("precision").asLong() ?: 0,
        )
    }

    private fun DataSnapshot.toOptions(): RoomOptions {
        val mode = child("mode").getValue(String::class.java)
            ?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            ?: initialOptions.mode
        return RoomOptions(
            mode = mode,
            countdown = child("countdown").getValue(Boolean::class.java) ?: initialOptions.countdown,
            sound = child("sound").getValue(Boolean::class.java) ?: initialOptions.sound,
            hideScores = child("hideScores").getValue(Boolean::class.java)
                ?: initialOptions.hideScores,
        )
    }

    private fun DataSnapshot.asLong(): Long? = (value as? Number)?.toLong()

    // ================================================================ écriture

    override fun nowHostMillis(): Long = AppClock.wallNow() + serverOffsetMillis

    override fun toHostMillis(localWallMillis: Long): Long = localWallMillis + serverOffsetMillis

    override fun buzz(atWallMillis: Long) {
        val current = _state.value
        val at = toHostMillis(atWallMillis)
        if (!current.canBuzz(myId, nowHostMillis())) return
        _localBuzzRound.value = current.round
        val entry: Map<String, Any?> = mapOf(
            "round" to current.round,
            "at" to at,
            // La synchronisation Firebase est bonne à quelques dizaines de ms près.
            "precision" to ONLINE_PRECISION_MILLIS,
        )
        roomRef.child("buzzes").child(myId).setValue(entry)
    }

    override fun rename(name: String) {
        val clean = name.trim().take(GameEngine.MAX_NAME)
        if (clean.isBlank()) return
        myName = clean
        roomRef.child("players").child(myId).child("name").setValue(clean)
    }

    override fun arm() {
        if (!isHost) return
        val options = _state.value.options
        val armAt = nowHostMillis() + if (options.countdown) COUNTDOWN_MILLIS else 0L
        val round = maxOf(_state.value.round, lastArmedRound) + 1
        lastArmedRound = round
        _localBuzzRound.value = null
        countdownJob?.cancel()
        val armUpdate: Map<String, Any?> = mapOf(
            "buzzes" to null,
            "round" to roundMap(
                index = round,
                state = if (options.countdown) RoundState.COUNTDOWN else RoundState.ARMED,
                armedAt = armAt,
            ),
        )
        roomRef.updateChildren(armUpdate)
        if (options.countdown) {
            countdownJob = scope.launch {
                delay(COUNTDOWN_MILLIS)
                if (lastArmedRound == round) {
                    roomRef.child("round").child("state").setValue(RoundState.ARMED.name)
                }
            }
        }
    }

    override fun reset() {
        if (!isHost) return
        countdownJob?.cancel(); countdownJob = null
        _localBuzzRound.value = null
        val resetUpdate: Map<String, Any?> = mapOf(
            "buzzes" to null,
            "round" to roundMap(index = _state.value.round, state = RoundState.IDLE, armedAt = null),
        )
        roomRef.updateChildren(resetUpdate)
    }

    override fun setStatus(playerId: String, status: PlayerStatus) {
        if (!isHost) return
        val updates = mutableMapOf<String, Any?>("players/$playerId/status" to status.name)
        // Un joueur éliminé ne conserve pas la main sur le buzz en cours.
        if (status == PlayerStatus.ELIMINATED) updates["buzzes/$playerId"] = null
        roomRef.updateChildren(updates.toMap())
    }

    override fun kick(playerId: String) {
        if (!isHost || playerId == myId) return
        val removal: Map<String, Any?> = mapOf("players/$playerId" to null, "buzzes/$playerId" to null)
        roomRef.updateChildren(removal)
    }

    override fun addPoints(playerId: String, delta: Int) {
        if (!isHost) return
        val player = _state.value.player(playerId) ?: return
        roomRef.child("players").child(playerId).child("score").setValue(player.score + delta)
    }

    override fun resetScores() {
        if (!isHost) return
        val updates = _state.value.players.associate { "players/${it.id}/score" to 0 as Any? }
        if (updates.isNotEmpty()) roomRef.updateChildren(updates)
    }

    override fun transferHost(playerId: String) {
        if (!isHost || playerId == myId) return
        if (_state.value.player(playerId) == null) return
        roomRef.child("meta").child("hostId").setValue(playerId)
    }

    override fun setOptions(options: RoomOptions) {
        if (!isHost) return
        roomRef.child("options").setValue(options.toMap())
    }

    override fun close() {
        if (closing) return
        closing = true
        countdownJob?.cancel()
        adjudicationJob?.cancel()
        roomListener?.let { roomRef.removeEventListener(it) }
        offsetListener?.let { offsetRef.removeEventListener(it) }
        connectedListener?.let { connectedRef.removeEventListener(it) }
        val myRef = roomRef.child("players").child(myId)
        runCatching { myRef.child("online").onDisconnect().cancel() }
        if (isHost) {
            // L'hôte emporte le salon : pas de partie fantôme laissée dans la base.
            roomRef.removeValue()
        } else {
            myRef.child("online").setValue(false)
        }
        runCatching { roomRef.keepSynced(false) }
        _link.value = LinkStatus(LinkPhase.CLOSED, "Salon quitté")
        scope.cancel()
    }

    private fun roundMap(index: Int, state: RoundState, armedAt: Long?): Map<String, Any?> = mapOf(
        "index" to index,
        "state" to state.name,
        "armedAt" to armedAt,
    )

    private fun RoomOptions.toMap(): Map<String, Any?> = mapOf(
        "mode" to mode.name,
        "countdown" to countdown,
        "sound" to sound,
        "hideScores" to hideScores,
    )

    companion object {
        private const val TAG = "FirebaseRoomSession"
        const val COUNTDOWN_MILLIS = 3_000L
        private const val ONLINE_PRECISION_MILLIS = 25L
    }
}
