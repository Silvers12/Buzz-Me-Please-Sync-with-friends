package com.osala.BuzzMePlease.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
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
import com.osala.BuzzMePlease.ui.screens.HomeScreen
import com.osala.BuzzMePlease.ui.screens.JoinScreen
import com.osala.BuzzMePlease.ui.screens.RoomScreen
import com.osala.BuzzMePlease.ui.screens.SettingsScreen
import com.osala.BuzzMePlease.ui.screens.TutorialScreen
import com.osala.BuzzMePlease.ui.theme.BuzzMeTheme
import com.osala.BuzzMePlease.ui.theme.Stage
import java.util.Locale

@Composable
fun BuzzMeApp(viewModel: AppViewModel = viewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val playingClip by viewModel.clipPlayer.playing.collectAsStateWithLifecycle()

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
                    transport = settings.transport,
                    onNameChange = viewModel::setName,
                    onTransportChange = viewModel::setTransport,
                    onCreate = viewModel::createRoom,
                    onJoin = viewModel::goJoin,
                    onSettings = viewModel::goSettings,
                )

                Route.Join -> JoinScreen(
                    transport = settings.transport,
                    onBack = viewModel::goHome,
                    onJoin = { code, address -> viewModel.joinRoom(code, address) },
                )

                Route.Room -> {
                    val current = session
                    if (current == null) {
                        LaunchedEffect(Unit) { viewModel.goHome() }
                    } else {
                        RoomScreen(
                            session = current,
                            soundFx = viewModel.soundFx,
                            onLeave = viewModel::leaveRoom,
                            soundLibrary = viewModel.soundLibrary,
                            soundboard = settings.soundboard,
                            playingClipId = playingClip,
                            onPlayClip = viewModel::playClip,
                            onPickClip = viewModel::setSoundSlot,
                        )
                    }
                }

                Route.Settings -> SettingsScreen(
                    firebase = settings.firebase,
                    sound = settings.sound,
                    keepScreenOn = settings.keepScreenOn,
                    language = settings.language,
                    onLanguage = viewModel::setLanguage,
                    onSound = viewModel::setSound,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                    onFirebase = viewModel::setFirebase,
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
    return remember(base, language) {
        val tag = language.tag ?: return@remember base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        base.createConfigurationContext(configuration)
    }
}
