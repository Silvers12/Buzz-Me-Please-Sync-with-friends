package com.osala.BuzzMePlease.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osala.BuzzMePlease.core.AppLanguage
import com.osala.BuzzMePlease.core.AppLocale
import com.osala.BuzzMePlease.ui.screens.HomeScreen
import com.osala.BuzzMePlease.ui.screens.JoinScreen
import com.osala.BuzzMePlease.ui.screens.JoiningScreen
import com.osala.BuzzMePlease.ui.screens.RoomScreen
import com.osala.BuzzMePlease.ui.screens.SettingsScreen
import com.osala.BuzzMePlease.ui.screens.TutorialScreen
import com.osala.BuzzMePlease.ui.theme.BuzzMeTheme
import com.osala.BuzzMePlease.ui.theme.Stage

@Composable
fun BuzzMeApp(viewModel: AppViewModel = viewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val playingClip by viewModel.clipPlayer.playing.collectAsStateWithLifecycle()
    val soundLibrary by viewModel.soundLibrary.collectAsStateWithLifecycle()
    val buzzerLibrary by viewModel.buzzerLibrary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Une partie dure : l'écran ne doit pas s'éteindre au milieu d'une manche.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissNotice()
    }

    // Langue de l'application : celle du téléphone par défaut, celle qu'on a choisie sinon. On
    // remplace le contexte fourni à l'arbre plutôt que de recréer l'activité : le changement se
    // voit immédiatement, sans repasser par l'écran d'accueil.
    val localized = rememberLocalizedContext(settings.language)

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
    ) {
    BuzzMeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (route) {
                Route.Loading -> Box(Modifier.fillMaxSize())

                Route.Home -> HomeScreen(
                    name = settings.name,
                    onNameChange = viewModel::setName,
                    onCreate = viewModel::createRoom,
                    onJoin = viewModel::goJoin,
                    onSettings = viewModel::goSettings,
                )

                Route.Join -> JoinScreen(
                    onBack = viewModel::goHome,
                    onJoin = { code, address -> viewModel.joinRoom(code, address) },
                )

                Route.Room -> {
                    val current = session
                    if (current == null) {
                        LaunchedEffect(Unit) { viewModel.goHome() }
                    } else if (!current.isHost && !current.joined.collectAsStateWithLifecycle().value) {
                        // Le salon n'a pas encore répondu : on ne montre rien du jeu, sans quoi
                        // un code tapé au hasard ouvrirait un salon d'apparence normale.
                        val link by current.link.collectAsStateWithLifecycle()
                        JoiningScreen(
                            code = current.state.value.code,
                            phase = link.phase,
                            detail = link.detail,
                            onCancel = viewModel::leaveRoom,
                        )
                    } else {
                        RoomScreen(
                            session = current,
                            soundFx = viewModel.soundFx,
                            onLeave = viewModel::leaveRoom,
                            soundLibrary = soundLibrary,
                            soundboard = settings.soundboard,
                            playingClipId = playingClip,
                            onPlayClip = viewModel::playClip,
                            onPickClip = viewModel::setSoundSlot,
                        )
                    }
                }

                Route.Settings -> SettingsScreen(
                    sound = settings.sound,
                    keepScreenOn = settings.keepScreenOn,
                    language = settings.language,
                    buzzerSound = settings.buzzerSound,
                    buzzerImport = settings.buzzerImport,
                    buzzerLibrary = buzzerLibrary,
                    onLanguage = viewModel::setLanguage,
                    onSound = viewModel::setSound,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                    onBuzzerSound = viewModel::setBuzzerSound,
                    onPreviewSound = viewModel::previewBuzzerSound,
                    onTutorial = viewModel::goTutorial,
                    onBack = viewModel::goHome,
                )

                Route.Tutorial -> TutorialScreen(onBack = viewModel::closeTutorial)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { data ->
                Snackbar(
                    containerColor = Stage.PanelHigh,
                    contentColor = Stage.TextPrimary,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(data.visuals.message)
                }
            }
        }
    }
    }

    // Le retour système ramène à l'accueil ; quitter le salon reste un geste explicite.
    BackHandler(enabled = route != Route.Home && route != Route.Loading) {
        when (route) {
            Route.Tutorial -> viewModel.closeTutorial()
            Route.Room -> viewModel.leaveRoom()
            else -> viewModel.goHome()
        }
    }
}

/**
 * Un contexte dont les ressources parlent la langue demandée. `SYSTEM` rend le contexte tel
 * quel : c'est Android qui choisit alors entre `values-fr` et l'anglais par défaut.
 */
@Composable
private fun rememberLocalizedContext(language: AppLanguage): Context {
    val base = LocalContext.current
    // La configuration doit être une clé : une tablette qu'on tourne change de dimensions sans
    // que l'activité soit recréée. Sans cela, l'arbre continuerait de lire la largeur d'avant la
    // rotation — et de choisir la disposition qui allait avec.
    val configuration = LocalConfiguration.current
    return remember(base, language, configuration) { AppLocale.wrap(base) }
}
