package fr.buzzme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.buzzme.ui.screens.HomeScreen
import fr.buzzme.ui.screens.JoinScreen
import fr.buzzme.ui.screens.RoomScreen
import fr.buzzme.ui.screens.SettingsScreen
import fr.buzzme.ui.screens.TutorialScreen
import fr.buzzme.ui.theme.BuzzMeTheme
import fr.buzzme.ui.theme.Stage

@Composable
fun BuzzMeApp(viewModel: AppViewModel = viewModel()) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

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
                        )
                    }
                }

                Route.Settings -> SettingsScreen(
                    firebase = settings.firebase,
                    sound = settings.sound,
                    keepScreenOn = settings.keepScreenOn,
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

    // Le retour système ramène à l'accueil ; quitter le salon reste un geste explicite.
    BackHandler(enabled = route != Route.Home && route != Route.Loading) {
        when (route) {
            Route.Tutorial -> viewModel.closeTutorial()
            Route.Room -> viewModel.leaveRoom()
            else -> viewModel.goHome()
        }
    }
}
