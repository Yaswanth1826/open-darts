package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.x01.X01Game
import com.opendarts.geometry.Point
import com.opendarts.session.GameSession
import com.opendarts.ui.camera.BoardOverlay
import com.opendarts.ui.camera.CalibrationStore
import com.opendarts.ui.camera.CameraPermissionGate
import com.opendarts.ui.camera.CameraPhase
import com.opendarts.ui.camera.CameraStatus
import com.opendarts.vision.ml.BoardView
import com.opendarts.vision.ml.Placement
import com.opendarts.ui.camera.CameraView
import com.opendarts.ui.camera.DebugCapture
import com.opendarts.ui.camera.Guide
import com.opendarts.ui.camera.GuideOverlay
import com.opendarts.ui.camera.ScoringCamera
import com.opendarts.ui.camera.TfliteDartNet
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState

@Composable
fun CameraGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit, onCalibrate: () -> Unit) {
    ObserveGame()
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    var status by remember { mutableStateOf(CameraStatus()) }
    val markers = remember { mutableStateListOf<Point>() }
    var aligning by remember { mutableStateOf(false) }
    var tips by remember { mutableStateOf(false) }
    var outline by remember { mutableStateOf<Boolean?>(null) }
    var everyDart by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val showBoard = outline ?: !status.usingAi
    var guide by remember { mutableStateOf(Guide()) }
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
            net = TfliteDartNet.loadOrNull(context),
        ).also { if (session.manualCalibration) it.useManualCalibration() }
    }
    DisposableEffect(camera) { onDispose { camera.release() } }
    DisposableEffect(aligning) {
        camera.searchPaused = aligning
        onDispose { camera.searchPaused = false }
    }

    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val round = "${session.setup.title.uppercase()}  \u00B7  R${game.round}" + ((game as? X01Game)?.let { if (it.config.legsToWin > 1 || it.config.setsToWin > 1) " \u00B7 LEG ${it.gameState.legsPlayed + 1}" else "" }.orEmpty())
    val cameraBox: @Composable (Modifier) -> Unit = { modifier ->
        CameraArea(
            camera, status, markers, aligning, guide, { guide = it }, showBoard, round, session.message, game.isFinished,
            onAlign = { aligning = true }, onLock = { camera.useGuide(guide); aligning = false }, onCancelAlign = { aligning = false },
            onCalibrate = onCalibrate, onTips = { tips = true }, onUndo = { session.undo() }, canUndo = game.canUndo, modifier = modifier,
        )
    }
    val panel: @Composable (Boolean) -> Unit = { fill ->
        Column(if (fill) Modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)) else Modifier.clip(RoundedCornerShape(20.dp))) {
            CurrentCard(game, if (fill) Modifier.weight(1f) else Modifier, fill = fill, onSlot = { editing = it })
            PlayerTabs(game, roomy = false)
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Slate).systemBarsPadding()) {
        GameTopBar(session, game, onExit, onPrevious = { session.previousPlayer() }, onNextPlayer = { session.nextPlayer() }) { dismiss ->
            DropdownMenuItem(text = { Text("Rescan board") }, onClick = { dismiss(); camera.findBoardAgain() })
            DropdownMenuItem(text = { Text("Board is clear \u2013 start now") }, enabled = status.phase == CameraPhase.TRACKING, onClick = { dismiss(); camera.startNow() })
            DropdownMenuItem(text = { Text("Align board by hand") }, onClick = { dismiss(); aligning = true })
            DropdownMenuItem(text = { Text("Calibrate with 4 taps") }, onClick = { dismiss(); onCalibrate() })
            DropdownMenuItem(text = { Text((if (showBoard) "\u2713 " else "") + "Show board outline") }, onClick = { dismiss(); outline = !showBoard })
            DropdownMenuItem(text = { Text("Save this frame for debugging") }, onClick = {
                dismiss()
                camera.saveFrame("misread") { name -> saved = if (name.isEmpty()) "Could not save the frame" else "Saved to Pictures/OpenDarts" }
            })
            DropdownMenuItem(text = { Text((if (everyDart) "\u2713 " else "") + "Save a frame for every dart") }, onClick = { dismiss(); everyDart = !everyDart; camera.captureEveryDart = everyDart })
            DropdownMenuItem(text = { Text("Setup tips") }, onClick = { dismiss(); tips = true })
        }
        if (landscape) {
            Row(Modifier.weight(1f).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cameraBox(Modifier.weight(1.15f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { panel(true) }
            }
        } else {
            cameraBox(Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { panel(false) }
        }
    }
    if (tips) SetupTipsDialog { tips = false }
    editing?.let { slot -> DartEditor(slot, game.turnHits.getOrNull(slot), onPick = { session.editDart(slot, it) }, onDismiss = { editing = null }) }
    saved?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { saved = null }, confirmButton = { androidx.compose.material3.TextButton(onClick = { saved = null }) { Text("OK") } }, text = { Text(msg) },
        )
    }
}

@Composable
private fun CameraArea(
    camera: ScoringCamera, status: CameraStatus, markers: List<Point>, aligning: Boolean, guide: Guide, onGuide: (Guide) -> Unit,
    showBoard: Boolean, round: String, message: String, finished: Boolean,
    onAlign: () -> Unit, onLock: () -> Unit, onCancelAlign: () -> Unit, onCalibrate: () -> Unit, onTips: () -> Unit,
    onUndo: () -> Unit, canUndo: Boolean, modifier: Modifier,
) {
    val portrait = LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val aspect = if (status.frameWidth > 0) status.frameWidth.toFloat() / status.frameHeight else if (portrait) 0.75f else 1.3333f
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        CameraPermissionGate(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(aspect)) {
                    CameraView(Modifier.fillMaxSize(), onFrame = camera::onFrame)
                    if (aligning) GuideOverlay(guide, onGuide, Modifier.fillMaxSize()) else BoardOverlay(status, markers, Modifier.fillMaxSize(), showBoard = showBoard)
                }
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp)) {
                    when {
                        aligning -> AlignCard(onLock, onCancelAlign)
                        !finished && status.phase != CameraPhase.TRACKING -> FindingCard(status.phase == CameraPhase.NOT_FOUND, camera, onAlign, onCalibrate, onTips)
                        else -> StatusPill(status, message, Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.75f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(round, color = Palette.Ink, fontFamily = Condensed, fontSize = 18.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(0.01f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayButton(enabled = canUndo, onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Color.White) }
                        OverlayButton(onClick = { camera.findBoardAgain() }) { Icon(Icons.Default.Refresh, "Rescan", tint = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Charcoal.copy(alpha = if (enabled) 0.85f else 0.45f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StatusPill(status: CameraStatus, message: String, modifier: Modifier) {
    val text = when (status.phase) {
        CameraPhase.TRACKING -> when (status.tracker) {
            TrackerState.WAITING_FOR_BOARD -> "Scanning the empty board \u2013 keep hands clear"
            TrackerState.MOVING -> "Detecting\u2026"
            TrackerState.SETTLING -> "Hold on\u2026"
            TrackerState.READY -> coaching(status.view) ?: (message + (if (status.darts > 0) "  \u00B7  ${status.darts} on board" else ""))
        }
        else -> message
    }
    Box(modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontFamily = Condensed, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A short instruction when the phone is badly placed, or null when the view is fine. */
private fun coaching(view: BoardView?): String? = when (view?.placement) {
    Placement.CUT_OFF -> "Part of the board is out of view \u2013 move the phone back"
    Placement.TOO_FAR -> "Move the phone closer to the board"
    Placement.TOO_SIDEWAYS -> "Too far to the side \u2013 move the phone round in front a little"
    else -> null
}

/** Dark card over the camera while the board is not locked yet. */
@Composable
private fun FindingCard(notFound: Boolean, camera: ScoringCamera, onAlign: () -> Unit, onCalibrate: () -> Unit, onTips: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xE6322E26)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (notFound) "Can't see the board clearly. Put the phone beside the board, a little to one side and facing it, with the whole board in view." else "Point the camera at the dartboard from a little to the side, with the whole board in view.",
            color = Color.White.copy(alpha = 0.92f), fontFamily = CondensedRegular, fontSize = 18.sp, textAlign = TextAlign.Center,
        )
        Text("Setup tips \u203A", color = Palette.Link, fontFamily = Condensed, fontSize = 19.sp, modifier = Modifier.clickable(onClick = onTips))
        if (notFound) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { camera.findBoardAgain() }) { Text("Try again", maxLines = 1) }
                OutlinedButton(onClick = onAlign) { Text("Align by hand", maxLines = 1) }
                OutlinedButton(onClick = onCalibrate) { Text("4 taps", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun AlignCard(onLock: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xE6322E26)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Drag the green circle onto the board and pinch to resize it until the outer ring sits on the outside edge of the doubles. The red line must point at the 20. Clear the board of darts first.",
            color = Color.White.copy(alpha = 0.92f), fontFamily = CondensedRegular, fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLock, modifier = Modifier.weight(1f)) { Text("Lock board") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}
