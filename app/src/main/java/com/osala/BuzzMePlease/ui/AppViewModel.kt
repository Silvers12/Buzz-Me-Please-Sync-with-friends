package com.osala.BuzzMePlease.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osala.BuzzMePlease.R
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.core.AppLocale
import com.osala.BuzzMePlease.core.ClipPlayer
import com.osala.BuzzMePlease.core.Codes
import com.osala.BuzzMePlease.core.Prefs
import com.osala.BuzzMePlease.core.Settings
import com.osala.BuzzMePlease.core.SoundClip
import com.osala.BuzzMePlease.core.SoundFx
import com.osala.BuzzMePlease.core.SoundLibrary
import com.osala.BuzzMePlease.game.RoomSession
import com.osala.BuzzMePlease.model.RoomOptions
import com.osala.BuzzMePlease.net.lan.LanRoomSession
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

    private fun text(resId: Int, vararg args: Any): String =
        with(AppLocale.wrap(getApplication())) {
            if (args.isEmpty()) getString(resId) else getString(resId, *args)
        }

    private val prefs = Prefs(application)
    val soundFx = SoundFx(application)

    /** Sonothèque de l'animateur : la bibliothèque disponible et le lecteur. Les libellés des
     * sons suivent la langue choisie, la liste se refait donc quand elle change. */
    private val _soundLibrary = MutableStateFlow(SoundLibrary.clips(AppLocale.wrap(application)))
    val soundLibrary = _soundLibrary.asStateFlow()
    val clipPlayer = ClipPlayer(application)

    private val _settings = MutableStateFlow(
        Settings(
            playerId = "",
            name = "",
            sound = true,
            keepScreenOn = true,
            tutorialSeen = true,
            soundboard = List(SoundLibrary.SLOTS) { "" },
            language = AppLanguage.SYSTEM,
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
                // La langue d'abord : le réseau et la sonothèque la lisent ici, faute de
                // contexte Compose.
                if (AppLocale.current != loaded.language) {
                    AppLocale.current = loaded.language
                    _soundLibrary.value = SoundLibrary.clips(AppLocale.wrap(getApplication()))
                }
                _settings.value = loaded
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

    fun setSound(enabled: Boolean) {
        soundFx.enabled = enabled
        viewModelScope.launch { prefs.setSound(enabled) }
        val current = _session.value ?: return
        if (current.isHost) current.setOptions(current.state.value.options.copy(sound = enabled))
    }

    /** Pose un son sur une touche de la sonothèque, ou libère la touche avec `null`. */
    fun setSoundSlot(index: Int, clipId: String?) {
        val current = _settings.value.soundboard
        if (index !in current.indices) return
        val next = current.toMutableList().also { it[index] = clipId.orEmpty() }
        viewModelScope.launch { prefs.setSoundboard(next) }
    }

    fun playClip(clip: SoundClip) = clipPlayer.play(clip)

    fun stopClip() = clipPlayer.stop()

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(language) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepScreenOn(enabled) }
    }

    // ------------------------------------------------------------------ salon

    fun createRoom() = startSession(Codes.newRoomCode(), asHost = true, hostAddress = null)

    fun joinRoom(code: String, hostAddress: String? = null) {
        val normalized = Codes.normalize(code)
        if (!Codes.isValid(normalized)) {
            _notice.value = text(R.string.notice_code_length, Codes.LENGTH)
            return
        }
        startSession(normalized, asHost = false, hostAddress = hostAddress)
    }

    private fun startSession(code: String, asHost: Boolean, hostAddress: String?) {
        val current = _settings.value
        val name = current.name.trim().ifBlank { text(R.string.default_player_name) }
        closeSession()

        val created: RoomSession = LanRoomSession(
            context = getApplication(),
            myId = current.playerId,
            initialName = name,
            code = code,
            startAsHost = asHost,
            hostAddress = hostAddress,
            initialOptions = RoomOptions(sound = current.sound),
        )

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
        clipPlayer.release()
        soundFx.release()
        super.onCleared()
    }

    private companion object {
        const val MAX_NAME = 18
    }
}
