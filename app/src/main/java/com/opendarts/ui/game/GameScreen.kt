package com.opendarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.model.DartHit
import com.opendarts.geometry.Point
import com.opendarts.session.GameSession
import com.opendarts.session.InputMode
import com.opendarts.ui.camera.BoardOverlay
import com.opendarts.ui.camera.CalibrationStore
import com.opendarts.ui.camera.CameraPermissionGate
import com.opendarts.ui.camera.CameraPhase
import com.opendarts.ui.camera.CameraStatus
import com.opendarts.ui.camera.CameraView
import com.opendarts.ui.camera.DebugCapture
import com.opendarts.ui.camera.ScoringCamera
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState

@Composable
fun GameScreen(session: GameSession, onExit: () -> Unit, onCalibrate: () -> Unit) {
    val game = session.game ?: return
    @Suppress("UNUSED_VARIABLE") val observed = session.revision

    if (session.setup.input == InputMode.CAMERA) CameraGame(session, game, onExit, onCalibrate) else ManualGame(session, game, onExit)

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

// ---- manual -------------------------------------------------------------------------------------------------------

@Composable
private fun ManualGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TopBar(session, game, onExit)
        Scoreboard(game)
        TurnPanel(session, game)
        if (!game.isFinished) ScoreInput(game.turnHits) { session.throwDart(it) }
        Spacer(Modifier.height(16.dp))
    }
}

// ---- shared pieces -----------------------------------------------------------------------------------------------

@Composable
private fun TopBar(session: GameSession, game: DartGame<*>, onExit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onExit) { Text("Quit") }
        Column(Modifier.weight(1f)) {
            Text(session.setup.kind.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(session.setup.subtitle, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { session.undo() }, enabled = game.canUndo) { Text("Undo") }
    }
}

/** The three dart slots of the current turn, the status line and the turn buttons. */
@Composable
private fun TurnPanel(session: GameSession, game: DartGame<*>, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until game.dartsPerTurn) {
                val hit = game.turnHits.getOrNull(i)
                val active = !game.isFinished && hit == null && i == game.turnHits.size
                Surface(
                    modifier = Modifier.weight(1f).height(if (compact) 52.dp else 60.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(hit?.toString() ?: "\u2013", fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (hit != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
            val total = game.turnHits.sumOf { it.points }
            Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TURN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(total.toString(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text(session.message, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { session.nextPlayer() }, enabled = !game.isFinished, modifier = Modifier.weight(1f)) { Text("Next player") }
            OutlinedButton(onClick = { session.throwDart(DartHit.MISS) }, enabled = !game.isFinished, modifier = Modifier.weight(1f)) { Text("Miss") }
        }
    }
}

/** One compact card per player; the thrower is outlined. */
@Composable
private fun PlayerStrip(game: DartGame<*>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        game.players.forEachIndexed { i, player ->
            val active = i == game.currentPlayerIndex && !game.isFinished
            val (big, small) = headline(game, i)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.widthIn(min = 108.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(player.name, maxLines = 1, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(big, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1)
                }
            }
        }
    }
}

// ---- camera ------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit, onCalibrate: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    var status by remember { mutableStateOf(CameraStatus()) }
    val markers = remember { mutableStateListOf<Point>() }
    var showManual by remember { mutableStateOf(false) }
    val camera = remember {
        ScoringCamera(
            store,
            onEvent = { event ->
                when (event) {
                    is TrackerEvent.DartLanded -> { markers.add(event.pixel); session.throwDart(event.hit, fromCamera = true) }
                    is TrackerEvent.DartsRemoved -> {
                        while (markers.size > event.remaining && markers.isNotEmpty()) markers.removeAt(markers.lastIndex)
                        if (event.remaining == 0) session.dartsPulled()
                    }
                }
            },
            onStatus = { status = it },
            capture = DebugCapture(context),
        ).also { if (session.manualCalibration) it.useManualCalibration() }
    }
    DisposableEffect(camera) { onDispose { camera.release() } }

    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!game.isFinished && status.phase == CameraPhase.NOT_FOUND) NotFoundHelp(camera, onCalibrate)
            TurnPanel(session, game, compact = true)
            CameraControls(camera, status, onManual = { showManual = true })
            DebugRow(camera)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TopBar(session, game, onExit)
        PlayerStrip(game)
        if (landscape) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraBox(camera, status, markers, Modifier.weight(1.3f).fillMaxHeight())
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
            }
        } else {
            CameraBox(camera, status, markers, Modifier.weight(1f).fillMaxWidth())
            controls()
        }
    }

    if (showManual) {
        ModalBottomSheet(onDismissRequest = { showManual = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter or correct a dart", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Wrong reading? Close this, tap Undo, then enter the right dart here.", style = MaterialTheme.typography.bodySmall)
                if (!game.isFinished) ScoreInput(game.turnHits) { session.throwDart(it) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CameraBox(camera: ScoringCamera, status: CameraStatus, markers: List<Point>, modifier: Modifier) {
    val portrait = LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val aspect = if (status.frameWidth > 0) status.frameWidth.toFloat() / status.frameHeight else if (portrait) 0.75f else 1.3333f
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        CameraPermissionGate(Modifier.fillMaxSize()) {
            Box(Modifier.aspectRatio(aspect)) {
                CameraView(Modifier.fillMaxSize(), onFrame = camera::onFrame)
                BoardOverlay(status, markers, Modifier.fillMaxSize())
            }
            StatusPill(status, Modifier.align(Alignment.TopCenter).padding(10.dp))
        }
    }
}

@Composable
private fun StatusPill(status: CameraStatus, modifier: Modifier) {
    val (text, color) = when (status.phase) {
        CameraPhase.FINDING_BOARD -> "Looking for the dartboard \u2013 keep the whole board in view" to Color(0xFFFFC857)
        CameraPhase.NOT_FOUND -> "Can't see the board clearly" to Color(0xFFFF6B6B)
        CameraPhase.TRACKING -> when (status.tracker) {
            TrackerState.WAITING_FOR_BOARD -> "Scanning the empty board \u2013 keep hands clear" to Color(0xFFFFC857)
            TrackerState.MOVING -> "Detecting\u2026" to Color(0xFFFFC857)
            TrackerState.SETTLING -> "Hold on\u2026" to Color(0xFFFFC857)
            TrackerState.READY -> "Ready \u2013 throw!" + (if (status.darts > 0) "  \u00B7  ${status.darts} on board" else "") to Color(0xFF63E6BE)
        }
    }
    Surface(modifier, shape = RoundedCornerShape(50), color = Color(0xCC000000)) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CameraControls(camera: ScoringCamera, status: CameraStatus, onManual: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(onClick = { camera.findBoardAgain() }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Rescan", maxLines = 1) }
        FilledTonalButton(
            onClick = { camera.startNow() }, enabled = status.phase == CameraPhase.TRACKING,
            modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        ) { Text("Board clear", maxLines = 1) }
        FilledTonalButton(onClick = { camera.rotateBoard(-18.0) }, enabled = status.phase == CameraPhase.TRACKING, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("\u21BA") }
        FilledTonalButton(onClick = { camera.rotateBoard(18.0) }, enabled = status.phase == CameraPhase.TRACKING, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("\u21BB") }
        Button(onClick = onManual, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Manual", maxLines = 1) }
    }
}

@Composable
private fun NotFoundHelp(camera: ScoringCamera, onCalibrate: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Move the phone so the whole board is visible, square-on if possible, with even light and no glare.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { camera.findBoardAgain() }, modifier = Modifier.weight(1f)) { Text("Try again") }
                OutlinedButton(onClick = onCalibrate, modifier = Modifier.weight(1f)) { Text("Set up by hand") }
            }
        }
    }
}
@Composable
private fun DebugRow(camera: ScoringCamera) {
    var everyDart by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { camera.saveFrame("misread") { name -> saved = if (name.isEmpty()) "Could not save" else "Saved to Pictures/OpenDarts" } },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        ) { Text("Save frame", maxLines = 1) }
        Switch(checked = everyDart, onCheckedChange = { everyDart = it; camera.captureEveryDart = it })
        Text(saved ?: "Save every dart", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}