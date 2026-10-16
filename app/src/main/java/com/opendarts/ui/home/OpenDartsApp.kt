package com.opendarts.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opendarts.game.x01.X01Config
import com.opendarts.session.Category
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.session.category
import com.opendarts.ui.camera.CalibrationScreen
import com.opendarts.ui.game.GameScreen
import com.opendarts.ui.setup.SetupScreen

private enum class Page { HOME, CATEGORY, SETUP, GAME, CALIBRATE }

@Composable
fun OpenDartsApp() {
    val session: GameSession = viewModel()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var setupFrom by rememberSaveable { mutableStateOf(Page.HOME) }
    var returnTo by rememberSaveable { mutableStateOf(Page.HOME) }
    var category by rememberSaveable { mutableStateOf(Category.X01) }
    if (page == Page.GAME && !session.isRunning) page = Page.HOME

    BackHandler(enabled = page != Page.HOME) {
        page = when (page) {
            Page.CALIBRATE -> returnTo
            Page.SETUP -> setupFrom
            else -> Page.HOME
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (page) {
            Page.HOME -> HomeScreen(
                onCategory = { category = it; page = Page.CATEGORY },
                onQuick = {
                    session.draft = GameSetup(kind = GameKind.X01, x01 = X01Config(501), input = session.draft.input)
                    setupFrom = Page.HOME; page = Page.SETUP
                },
                onResume = if (session.isRunning) ({ page = Page.GAME }) else null,
            )
            Page.CATEGORY -> CategoryScreen(
                category,
                onBack = { page = Page.HOME },
                onPick = { change -> session.draft = change(session.draft); setupFrom = Page.CATEGORY; page = Page.SETUP },
            )
            Page.SETUP -> SetupScreen(
                session,
                category = session.draft.kind.category,
                onStart = { page = Page.GAME },
                onBack = { page = setupFrom },
            )
            Page.GAME -> GameScreen(
                session,
                onExit = { session.leave(); page = Page.HOME },
                onCalibrate = { returnTo = Page.GAME; page = Page.CALIBRATE },
            )
            Page.CALIBRATE -> CalibrationScreen(onDone = { session.manualCalibration = true; page = returnTo }, onCancel = { page = returnTo })
        }
    }
}
