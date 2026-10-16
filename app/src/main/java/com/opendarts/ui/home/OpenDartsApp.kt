package com.opendarts.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.ui.camera.CalibrationScreen
import com.opendarts.ui.game.GameScreen
import com.opendarts.ui.setup.SetupScreen

private enum class Page { HOME, SETUP, GAME, CALIBRATE }

@Composable
fun OpenDartsApp() {
    val session: GameSession = viewModel()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var returnTo by rememberSaveable { mutableStateOf(Page.HOME) }
    if (page == Page.GAME && !session.isRunning) page = Page.HOME

    BackHandler(enabled = page != Page.HOME) {
        page = when (page) {
            Page.CALIBRATE -> returnTo
            Page.GAME -> Page.HOME
            else -> Page.HOME
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (page) {
            Page.HOME -> HomeScreen(
                onPick = { kind -> session.draft = session.draft.copy(kind = kind); page = Page.SETUP },
                onResume = if (session.isRunning) ({ page = Page.GAME }) else null,
            )
            Page.SETUP -> SetupScreen(
                session,
                onStart = { page = Page.GAME },
                onBack = { page = Page.HOME },
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

@Composable
private fun HomeScreen(onPick: (GameKind) -> Unit, onResume: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("OPEN DARTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Your game.\nYour camera.\nNo paywall.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Free, offline darts scoring for you and your friends.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        if (onResume != null) Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume game in progress") }
        GameKind.values().forEach { kind ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kind.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(kind.blurb, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    Button(onClick = { onPick(kind) }, modifier = Modifier.fillMaxWidth()) { Text("Play ${kind.title}") }
                }
            }
        }
        Text("Camera scoring needs no setup: choose a game, pick Camera and point the phone at your board.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
    }
}