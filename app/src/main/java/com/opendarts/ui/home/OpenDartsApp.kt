package com.opendarts.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opendarts.billing.LocalEntitlements
import com.opendarts.game.x01.X01Config
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.session.GameStore
import com.opendarts.session.InputMode
import com.opendarts.ui.camera.CalibrationScreen
import com.opendarts.ui.common.PaywallDialog
import com.opendarts.ui.game.GameScreen
import com.opendarts.ui.setup.NewGameScreen

private enum class Page { HOME, NEW, GAME, CALIBRATE }

@Composable
fun OpenDartsApp() {
    val session: GameSession = viewModel()
    val context = LocalContext.current
    val entitlements = remember { LocalEntitlements(context) }
    session.entitlements = entitlements
    LaunchedEffect(session) { session.attach(GameStore(context.applicationContext)) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var tab by rememberSaveable { mutableStateOf(Tab.PLAY) }
    var returnTo by rememberSaveable { mutableStateOf(Page.HOME) }
    if (page == Page.GAME && !session.isRunning) page = Page.HOME

    BackHandler(enabled = page != Page.HOME || tab != Tab.PLAY) {
        if (page == Page.HOME) tab = Tab.PLAY else page = if (page == Page.CALIBRATE) returnTo else Page.HOME
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (page) {
            Page.HOME -> HomeShell(tab, onTab = { tab = it }) {
                when (tab) {
                    Tab.PLAY -> PlayScreen(
                        onMode = { kind -> session.draft = session.draft.copy(kind = kind); page = Page.NEW },
                        onQuick = {
                            session.draft = GameSetup(kind = GameKind.X01, x01 = X01Config(501), input = session.draft.input)
                            page = Page.NEW
                        },
                        onResume = if (session.isRunning) ({ page = Page.GAME }) else null,
                        resumeDetail = session.game?.let { g ->
                            "${session.setup.title} \u00B7 ${g.players.joinToString(" vs ") { it.name }} \u00B7 round ${g.round}"
                        } ?: "",
                    )
                    Tab.PLAYERS -> PlayersScreen()
                    Tab.SETTINGS -> SettingsScreen(entitlements)
                }
            }
            Page.NEW -> NewGameScreen(session, onStart = { page = Page.GAME }, onBack = { page = Page.HOME })
            Page.GAME -> GameScreen(
                session,
                onExit = { session.leave(); page = Page.HOME },
                onCalibrate = { returnTo = Page.GAME; page = Page.CALIBRATE },
            )
            Page.CALIBRATE -> CalibrationScreen(onDone = { session.manualCalibration = true; page = returnTo }, onCancel = { page = returnTo })
        }
        if (session.paywall) {
            PaywallDialog(
                onKeypad = { session.draft = session.draft.copy(input = InputMode.MANUAL); session.paywall = false },
                onDismiss = { session.paywall = false },
            )
        }
    }
}
