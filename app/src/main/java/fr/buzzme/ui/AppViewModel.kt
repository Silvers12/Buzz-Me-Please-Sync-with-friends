package fr.buzzme.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.buzzme.core.Codes
import fr.buzzme.core.Features
import fr.buzzme.core.Prefs
import fr.buzzme.core.Settings
import fr.buzzme.core.SoundFx
import fr.buzzme.core.Transport
import fr.buzzme.game.RoomSession
import fr.buzzme.model.RoomOptions
import fr.buzzme.net.lan.LanRoomSession
import fr.buzzme.net.online.FirebaseConfig
import fr.buzzme.net.online.FirebaseRoomSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Route {
    data object Loading : Route
    data object Home : Route
    data object Join : Route
    data object Room : Route
    data object Settings : Route
    data object Tutorial : Route
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    val soundFx = SoundFx(application)

    private val _settings = MutableStateFlow(
        Settings(
            playerId = "",
            name = "",
            transport = Transport.LOCAL,
            firebase = FirebaseConfig(),
            sound = true,
            keepScreenOn = true,
            tutorialSeen = true,
        ),
    )
    val settings = _settings.asStateFlow()

    private val _route = MutableStateFlow<Route>(Route.Loading)
    val route = _route.asStateFlow()

    private val _session = MutableStateFlow<RoomSession?>(null)
    val session = _session.asStateFlow()

    /** Message ponctuel à afficher (salon fermé, exclusion, configuration manquante…). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private var endedJob: Job? = null

    /** Écran vers lequel refermer le tutoriel. */
    private var tutorialOrigin: Route = Route.Home

    init {
        viewModelScope.launch {
            prefs.ensurePlayerId()
            prefs.settings.collect { loaded ->
                // Le salon en ligne est en sommeil : un réglage enregistré avant sa fermeture ne
                // doit pas laisser l'application sur un transport injouable.
                _settings.value =
                    if (Features.ONLINE_ROOMS) loaded else loaded.copy(transport = Transport.LOCAL)
                soundFx.enabled = loaded.sound
                if (_route.value == Route.Loading) {
                    // Première ouverture : on explique le jeu avant de laisser créer un salon.
                    _route.value = if (loaded.tutorialSeen) Route.Home else Route.Tutorial
                }
            }
        }
    }

    // ------------------------------------------------------------- navigation

    fun goHome() {
        if (_session.value != null) leaveRoom() else _route.value = Route.Home
    }

    fun goJoin() {
        _route.value = Route.Join
    }

    fun goSettings() {
        _route.value = Route.Settings
    }

    fun goTutorial() {
        // Le tutoriel se referme là d'où on l'a ouvert : appelé depuis les réglages, il y
        // ramène plutôt que de renvoyer l'utilisateur à l'accueil.
        tutorialOrigin = _route.value.takeIf { it == Route.Settings } ?: Route.Home
        _route.value = Route.Tutorial
    }

    /** Sortie du tutoriel : on ne le rouvrira plus tout seul. */
    fun closeTutorial() {
        viewModelScope.launch { prefs.setTutorialSeen() }
        _route.value = tutorialOrigin
        tutorialOrigin = Route.Home
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ---------------------------------------------------------------- réglages

    fun setName(name: String) {
        val clean = name.trim().take(MAX_NAME)
        viewModelScope.launch { prefs.setName(clean) }
        _session.value?.rename(clean)
    }

    fun setTransport(transport: Transport) {
        if (!Features.ONLINE_ROOMS && transport != Transport.LOCAL) return
        viewModelScope.launch { prefs.setTransport(transport) }
    }

    fun setSound(enabled: Boolean) {
        soundFx.enabled = enabled
        viewModelScope.launch { prefs.setSound(enabled) }
        val current = _session.value ?: return
        if (current.isHost) current.setOptions(current.state.value.options.copy(sound = enabled))
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepScreenOn(enabled) }
    }

    fun setFirebase(config: FirebaseConfig) {
        viewModelScope.launch { prefs.setFirebase(config) }
    }

    // ------------------------------------------------------------------ salon

    fun createRoom() = startSession(Codes.newRoomCode(), asHost = true, hostAddress = null)

    fun joinRoom(code: String, hostAddress: String? = null) {
        val normalized = Codes.normalize(code)
        if (!Codes.isValid(normalized)) {
            _notice.value = "Le code doit comporter ${Codes.LENGTH} caractères."
            return
        }
        startSession(normalized, asHost = false, hostAddress = hostAddress)
    }

    private fun startSession(code: String, asHost: Boolean, hostAddress: String?) {
        val current = _settings.value
        val name = current.name.trim().ifBlank { "Joueur" }
        closeSession()

        val options = RoomOptions(sound = current.sound)
        val created: RoomSession = when (current.transport) {
            Transport.LOCAL -> LanRoomSession(
                context = getApplication(),
                myId = current.playerId,
                initialName = name,
                code = code,
                startAsHost = asHost,
                hostAddress = hostAddress,
                initialOptions = options,
            )

            Transport.ONLINE -> {
                if (!current.firebase.isComplete) {
                    _notice.value = "Renseignez d'abord votre projet Firebase dans les réglages."
                    _route.value = Route.Settings
                    return
                }
                val online = runCatching {
                    FirebaseRoomSession(
                        context = getApplication(),
                        config = current.firebase,
                        myId = current.playerId,
                        initialName = name,
                        code = code,
                        startAsHost = asHost,
                        initialOptions = options,
                    )
                }.getOrElse { error ->
                    _notice.value = "Firebase indisponible : ${error.message}"
                    _route.value = Route.Settings
                    return
                }
                online
            }
        }

        _session.value = created
        _route.value = Route.Room

        endedJob?.cancel()
        endedJob = viewModelScope.launch {
            created.ended.collect { end ->
                if (end == null) return@collect
                _notice.value = end.message
                _route.value = Route.Home
                // Ferme en dernier : closeSession() annule cette collecte au passage.
                closeSession()
            }
        }
    }

    fun leaveRoom() {
        closeSession()
        _route.value = Route.Home
    }

    private fun closeSession() {
        endedJob?.cancel()
        endedJob = null
        _session.value?.close()
        _session.value = null
    }

    override fun onCleared() {
        closeSession()
        soundFx.release()
        super.onCleared()
    }

    private companion object {
        const val MAX_NAME = 18
    }
}
