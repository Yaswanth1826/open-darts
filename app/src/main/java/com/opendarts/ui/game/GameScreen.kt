package com.opendarts.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendarts.session.GameSession
import com.opendarts.session.InputMode
import com.opendarts.ui.camera.CalibrationStore
import com.opendarts.ui.camera.CameraPermissionGate
import com.opendarts.ui.camera.CameraView
import com.opendarts.ui.camera.ScoringCamera
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState

@Composable
fun GameScreen(session: GameSession, onExit: () -> Unit, onCalibrate: () -> Unit) {
    val game = session.game ?: return
    @Suppress("UNUSED_VARIABLE") val observed = session.revision

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onExit) { Text("Quit") }
            Column(Modifier.weight(1f)) {
                Text(session.setup.kind.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(session.setup.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { session.undo() }, enabled = game.canUndo) { Text("Undo") }
        }

        Scoreboard(game)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (game.isFinished) "Game over" else "${game.currentPlayer.name} · dart ${minOf(game.turnHits.size + 1, game.dartsPerTurn)} of ${game.dartsPerTurn}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until game.dartsPerTurn) {
                        SuggestionChip(onClick = {}, label = { Text(game.turnHits.getOrNull(i)?.toString() ?: "-") })
                    }
                }
                Text(session.message, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { session.nextPlayer() }, enabled = !game.isFinished, modifier = Modifier.weight(1f)) { Text("Next player") }
                    OutlinedButton(onClick = { session.throwDart(com.opendarts.game.model.DartHit.MISS) }, enabled = !game.isFinished, modifier = Modifier.weight(1f)) { Text("Miss / bounce-out") }
                }
            }
        }

        if (session.setup.input == InputMode.CAMERA) {
            CameraPanel(session, onCalibrate)
            Text("Fix a wrong reading: Undo, then enter the dart below.", style = MaterialTheme.typography.bodySmall)
        }

        if (!game.isFinished) ScoreInput(game.turnHits) { session.throwDart(it) }
        Spacer(Modifier.height(16.dp))
    }

    if (game.isFinished && !session.dismissedResult) {
        val names = game.winners.orEmpty().joinToString(" & ") { game.players[it].name }
        AlertDialog(
            onDismissRequest = { session.dismissedResult = true },
            title = { Text(if (game.winners.orEmpty().size > 1) "Draw: $names" else "$names wins!") },
            text = { Text("Great game. Play again with the same players, or head back to the menu.") },
            confirmButton = { Button(onClick = { session.rematch() }) { Text("Rematch") } },
            dismissButton = { Row { TextButton(onClick = { session.dismissedResult = true }) { Text("Review") }; TextButton(onClick = onExit) { Text("Menu") } } },
        )
    }
}

@Composable
private fun CameraPanel(session: GameSession, onCalibrate: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    var state by remember { mutableStateOf(TrackerState.WAITING_FOR_BOARD) }
    var darts by remember { mutableStateOf(0) }
    var uncalibrated by remember { mutableStateOf(!store.hasAny()) }
    val camera = remember {
        ScoringCamera(
            store,
            onEvent = { event ->
                when (event) {
                    is TrackerEvent.DartLanded -> session.throwDart(event.hit, fromCamera = true)
                    is TrackerEvent.DartsRemoved -> if (event.remaining == 0) session.dartsPulled()
                }
            },
            onState = { s, d -> state = s; darts = d; uncalibrated = false },
            onUncalibrated = { uncalibrated = true },
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera scoring", fontWeight = FontWeight.Bold)
            if (uncalibrated) {
                Text("The camera is not calibrated for this orientation yet.", color = MaterialTheme.colorScheme.secondary)
                Button(onClick = onCalibrate, modifier = Modifier.fillMaxWidth()) { Text("Calibrate now") }
            }
            CameraPermissionGate {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    CameraView(Modifier.fillMaxSize(), onFrame = camera::onFrame)
                }
                Text(
                    when (state) {
                        TrackerState.WAITING_FOR_BOARD -> "Waiting for a clear, still board..."
                        TrackerState.MOVING -> "Movement detected"
                        TrackerState.SETTLING -> "Settling..."
                        TrackerState.READY -> "Ready. Throw! ($darts dart${if (darts == 1) "" else "s"} on the board)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { camera.boardIsEmpty() }, modifier = Modifier.weight(1f)) { Text("Board is clear") }
                    OutlinedButton(onClick = onCalibrate, modifier = Modifier.weight(1f)) { Text("Recalibrate") }
                }
            }
        }
    }
}