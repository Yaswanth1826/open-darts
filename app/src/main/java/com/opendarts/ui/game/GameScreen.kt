@file:OptIn(ExperimentalMaterial3Api::class)

package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.x01.X01Game
import com.opendarts.game.x01.X01Summary
import com.opendarts.game.x01.summaries
import com.opendarts.session.Callout
import com.opendarts.session.CalloutKind
import com.opendarts.session.GameSession
import com.opendarts.session.InputMode
import com.opendarts.ui.common.Announcer
import com.opendarts.ui.common.LocalAnnouncer
import com.opendarts.ui.common.SoundSetting
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/**
 * The engine is a mutable object, so Compose cannot see it change. Every screen that reads it calls [ObserveGame],
 * which subscribes it to this counter; the session bumps the counter after each change.
 */
val LocalGameRevision = compositionLocalOf { 0 }

@Composable
@Suppress("NOTHING_TO_INLINE")
inline fun ObserveGame() {
    LocalGameRevision.current
}

/** Keeps clear of the system bars and the camera notch, whichever of them are showing. */
@Composable
fun Modifier.gameInsets(): Modifier = windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))

/**
 * Keeps the screen awake for the whole game, and in landscape hides the status and navigation bars (a swipe from the edge
 * brings them back for a moment) so the camera and scores get every pixel.
 */
@Composable
private fun GameWindow() {
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(view, landscape) {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        val window = (context as? android.app.Activity)?.window
        if (window == null || !landscape) return@DisposableEffect onDispose {}
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT }
        }
    }
}

@Composable
fun GameScreen(session: GameSession, onExit: () -> Unit, onCalibrate: () -> Unit) {
    val context = LocalContext.current
    val announcer = remember { Announcer(context) }
    DisposableEffect(session, announcer) {
        session.onDart = { announcer.dart() }
        session.onVisit = announcer::say
        session.onEffect = announcer::effect
        session.onTurn = announcer::sayNext
        session.fullCaller = SoundSetting.fullCaller(context)
        onDispose { session.onDart = null; session.onVisit = null; session.onEffect = null; session.onTurn = null; announcer.release() }
    }
    val game = session.game ?: return
    BotDriver(session)
    GameWindow()
    CompositionLocalProvider(LocalGameRevision provides session.revision, LocalAnnouncer provides announcer) {
        Box(Modifier.fillMaxSize()) {
            if (session.setup.input == InputMode.CAMERA) CameraGame(session, game, onExit, onCalibrate) else ManualGame(session, game, onExit)
            session.callout?.let { CalloutFlash(it, onDone = { session.clearCallout(it.id) }) }
        }

        if (game.isFinished && !session.dismissedResult) {
            val names = game.winners.orEmpty().joinToString(" & ") { game.players[it].name }
            AlertDialog(
                onDismissRequest = { session.dismissedResult = true },
                title = { Text(if (game.winners.orEmpty().size > 1) "Draw: $names" else "$names wins!") },
                text = {
                    if (game is X01Game) MatchStats(game.summaries()) else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        game.players.indices.forEach { i -> Text(summaryLine(game, i), fontSize = 15.sp) }
                    }
                },
                confirmButton = { Button(onClick = { session.rematch() }) { Text("Rematch") } },
                dismissButton = { Row { TextButton(onClick = { session.dismissedResult = true }) { Text("Review") }; TextButton(onClick = onExit) { Text("Menu") } } },
            )
        }
    }
}

/** Plays the computer opponent's turn, one dart at a time with a pause so the game can be followed. */
@Composable
private fun BotDriver(session: GameSession) {
    val game = session.game
    val botTurn = game != null && !game.isFinished && game.currentPlayer.isBot
    LaunchedEffect(session.revision, botTurn) {
        if (!botTurn) return@LaunchedEffect
        kotlinx.coroutines.delay(if (session.game?.turnOver == true) 1800L else 1000L)
        session.botStep()
    }
}

/**
 * A big word over the board for a moment when a visit ends. It never takes touches. A game shot (and a 180) throws confetti,
 * a bust shakes the word and tints the screen red.
 */
@Composable
private fun CalloutFlash(callout: Callout, onDone: () -> Unit) {
    val pop = remember(callout.id) { Animatable(0.6f) }
    val shake = remember(callout.id) { Animatable(0f) }
    val tint = remember(callout.id) { Animatable(if (callout.kind == CalloutKind.BUST) 0.28f else 0f) }
    LaunchedEffect(callout.id) {
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f))
        if (callout.kind == CalloutKind.BUST) {
            repeat(6) { shake.animateTo(if (it % 2 == 0) 16f else -16f, androidx.compose.animation.core.tween(45)) }
            shake.animateTo(0f, androidx.compose.animation.core.tween(45))
            tint.animateTo(0f, androidx.compose.animation.core.tween(600))
        }
        kotlinx.coroutines.delay(1200)
        onDone()
    }
    val (color, ink) = when (callout.kind) {
        CalloutKind.BUST -> Pal.Coral to Pal.OnLime
        CalloutKind.GOOD -> Pal.Lime to Pal.OnLime
        CalloutKind.PLAIN -> Pal.Raised to Pal.Text
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (tint.value > 0f) Box(Modifier.fillMaxSize().background(Pal.Coral.copy(alpha = tint.value)))
        if (callout.kind == CalloutKind.GOOD) Confetti(callout.id)
        Text(
            callout.text, color = ink, fontFamily = Display, fontSize = 52.sp, maxLines = 1,
            modifier = Modifier
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; translationX = shake.value }
                .clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.96f)).padding(horizontal = 28.dp, vertical = 8.dp),
        )
    }
}

private class Piece(val angle: Float, val speed: Float, val spin: Float, val colour: Color, val size: Float)

/** A burst of paper pieces from the middle of the screen that flies out, falls and fades. */
@Composable
private fun Confetti(id: Int) {
    val time = remember(id) { Animatable(0f) }
    LaunchedEffect(id) { time.animateTo(1f, androidx.compose.animation.core.tween(1700, easing = androidx.compose.animation.core.LinearEasing)) }
    val pieces = remember(id) {
        val random = kotlin.random.Random(id)
        val colours = listOf(Pal.Lime, Pal.Amber, Pal.Sky, Pal.Coral, Color.White)
        List(54) { Piece(random.nextFloat() * 6.2832f, 0.25f + random.nextFloat() * 0.55f, random.nextFloat() * 720f - 360f, colours[random.nextInt(colours.size)], 0.6f + random.nextFloat() * 0.8f) }
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val out = 1f - (1f - t) * (1f - t)
        val reach = size.minDimension
        pieces.forEach { piece ->
            val x = center.x + kotlin.math.cos(piece.angle) * piece.speed * reach * out
            val y = center.y + kotlin.math.sin(piece.angle) * piece.speed * reach * out + t * t * reach * 0.35f
            val alpha = if (t < 0.6f) 1f else ((1f - t) / 0.4f).coerceIn(0f, 1f)
            val w = 9.dp.toPx() * piece.size
            rotate(piece.spin * t, androidx.compose.ui.geometry.Offset(x, y)) {
                drawRect(piece.colour.copy(alpha = alpha), androidx.compose.ui.geometry.Offset(x - w / 2, y - w / 4), androidx.compose.ui.geometry.Size(w, w / 2))
            }
        }
    }
}

@Composable
private fun MatchStats(rows: List<X01Summary>) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { r ->
            Column {
                val result = buildString {
                    if (r.sets > 0) append("${r.sets} sets \u00B7 ")
                    if (r.legs > 0 || r.sets > 0) append("${r.legs} legs \u00B7 ")
                }
                Text(if (r.won) "${r.name}  \u2605" else r.name, fontFamily = Display, fontSize = 18.sp)
                Text("${result}Avg ${"%.1f".format(r.average)} \u00B7 Best visit ${r.highestVisit} \u00B7 ${r.darts} darts", fontSize = 13.sp)
                Text(
                    "100+ ${r.hundredPlus} \u00B7 140+ ${r.oneFortyPlus} \u00B7 180s ${r.oneEighties}" +
                        (r.checkoutPercent?.let { " \u00B7 Checkout $it%" } ?: ""),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ManualGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit) {
    ObserveGame()
    var editing by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val keypad: @Composable (Modifier) -> Unit = { modifier ->
        Keypad(
            canUndo = game.canUndo, enabled = !game.isFinished && !game.currentPlayer.isBot,
            onHit = { session.throwDart(it) }, onUndo = { session.undo() },
            modifier = modifier,
        )
    }
    val card: @Composable (Modifier, Boolean) -> Unit = { modifier, fill ->
        CurrentCard(game, modifier, fill = fill, onSlot = { editing = it }, onDelete = { session.editDart(it, null) }, onMenu = { menu = true })
    }
    val status = "${roundLabel(session, game)}  \u00B7  ${session.message}"
    Column(Modifier.fillMaxSize().background(Pal.Bg).gameInsets()) {
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keypad(Modifier.weight(1.3f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card(Modifier.weight(1f), true)
                    MessageLine(status)
                    PlayerTabs(game, onTap = session::selectPlayer)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card(Modifier, false)
                MessageLine(status)
                PlayerTabs(game, onTap = session::selectPlayer)
            }
            keypad(Modifier.weight(1f))
        }
    }
    GameMenu(session, game, menu, onClose = { menu = false }, onExit = onExit)
    editing?.let { slot ->
        DartEditor(
            slot, game.turnHits.getOrNull(slot), front = game.turnHits.take(slot),
            onPick = { session.editDart(slot, it) }, onBehind = { session.dartBehind(slot, it) }, onDismiss = { editing = null },
        )
    }
}

/** The game's name, round and leg, e.g. "501  \u00B7  R3  \u00B7  LEG 2". */
fun roundLabel(session: GameSession, game: DartGame<*>): String {
    val leg = (game as? X01Game)?.let { if (it.config.legsToWin > 1 || it.config.setsToWin > 1) "  \u00B7  LEG ${it.gameState.legsPlayed + 1}" else "" }.orEmpty()
    return "${session.setup.title.uppercase()}  \u00B7  R${game.round}$leg"
}

/**
 * The menu of a game in play. There is no bar for it: the game screens carry a small menu button on the camera or the score card,
 * which opens this sheet. It starts with the four things used most (scoreboard, undo, camera or keypad, sound), then [extra] entries
 * of the screen (the camera tools), then the less common ones and Quit.
 */
@Composable
fun GameMenu(
    session: GameSession,
    game: DartGame<*>,
    open: Boolean,
    onClose: () -> Unit,
    onExit: () -> Unit,
    extra: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    ObserveGame()
    var board by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val camera = session.setup.input == InputMode.CAMERA
    val announcer = LocalAnnouncer.current
    if (open) {
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                Text(roundLabel(session, game), color = Pal.Muted, fontFamily = Display, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuTile(Icons.AutoMirrored.Filled.List, "Scoreboard", Modifier.weight(1f)) { onClose(); board = true }
                    MenuTile(Icons.AutoMirrored.Filled.Undo, "Undo dart", Modifier.weight(1f), enabled = game.canUndo) { onClose(); session.undo() }
                    MenuTile(if (camera) Icons.Default.Keyboard else Icons.Default.CameraAlt, if (camera) "Keypad" else "Camera", Modifier.weight(1f)) {
                        onClose(); session.switchInput(if (camera) InputMode.MANUAL else InputMode.CAMERA)
                    }
                    if (announcer != null) {
                        MenuTile(if (announcer.enabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, if (announcer.enabled) "Sound on" else "Sound off", Modifier.weight(1f)) { announcer.toggle() }
                    }
                }
                Spacer(Modifier.height(6.dp))
                extra(onClose)
                if (game.players.size > 1 && !game.isFinished) MenuItem("Remove a player") { onClose(); removing = true }
                MenuItem("Quit game", ink = Pal.Coral) { onClose(); if (game.canUndo && !game.isFinished) confirmQuit = true else onExit() }
            }
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove which player?") },
            text = {
                Column {
                    game.players.forEachIndexed { i, player ->
                        TextButton(onClick = { removing = false; session.removePlayer(i) }) { Text(player.name.uppercase() + if (i == game.currentPlayerIndex) "  (throwing now)" else "") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
        )
    }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit this game?") },
            text = { Text("The game will be lost. To pause it instead, press Back: it waits under Resume game on the home screen, even if the app is closed.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; onExit() }) { Text("Quit game") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep playing") } },
        )
    }
    if (board) {
        ModalBottomSheet(onDismissRequest = { board = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObserveGame()
                Text(session.setup.kind.title.uppercase() + "  " + session.setup.subtitle, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Scoreboard(game)
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MenuTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val ink = Pal.Text.copy(alpha = if (enabled) 1f else 0.35f)
    Column(
        modifier.clip(shape).background(Pal.Raised).clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = ink)
        Text(label, color = ink, fontFamily = Display, fontSize = 13.sp, maxLines = 1)
    }
}

/** One plain entry in the game menu. */
@Composable
fun MenuItem(text: String, enabled: Boolean = true, ink: Color = Pal.Text, onClick: () -> Unit) {
    Text(
        text, color = ink.copy(alpha = if (enabled) 1f else 0.4f), fontFamily = Body, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun MessageLine(text: String) {
    Text(text, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
