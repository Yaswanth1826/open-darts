@file:OptIn(ExperimentalMaterial3Api::class)

package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.x01.X01Game
import com.opendarts.session.GameSession
import com.opendarts.session.InputMode
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette

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

@Composable
fun GameScreen(session: GameSession, onExit: () -> Unit, onCalibrate: () -> Unit) {
    val game = session.game ?: return
    CompositionLocalProvider(LocalGameRevision provides session.revision) {
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
}

@Composable
private fun ManualGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit) {
    ObserveGame()
    var editing by remember { mutableStateOf<Int?>(null) }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val keypad: @Composable (Modifier) -> Unit = { modifier ->
        Keypad(
            canUndo = game.canUndo, enabled = !game.isFinished,
            onHit = { session.throwDart(it) }, onUndo = { session.undo() }, onPrevious = { session.previousPlayer() }, onNext = { session.nextPlayer() },
            modifier = modifier,
        )
    }
    Column(Modifier.fillMaxSize().background(Palette.KeyLight).systemBarsPadding()) {
        GameTopBar(session, game, onExit, onPrevious = null, onNextPlayer = null)
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                keypad(Modifier.weight(1.3f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE2E2E2))) {
                    CurrentCard(game, Modifier.weight(1f), fill = true, onSlot = { editing = it })
                    Text(
                        session.message, color = Palette.Ink.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 14.sp, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFE2E2E2)).padding(horizontal = 12.dp, vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    PlayerTabs(game, roomy = false)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().background(Color(0xFFE2E2E2)).padding(bottom = 2.dp)) {
                DartSlots(game, Color(0xFF7A7A7A), onSlot = { editing = it })
                Text(
                    session.message, color = Palette.Ink.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            PlayerTabs(game, roomy = true)
            keypad(Modifier.weight(1f))
        }
    }
    editing?.let { slot -> DartEditor(slot, game.turnHits.getOrNull(slot), onPick = { session.editDart(slot, it) }, onDismiss = { editing = null }) }
}

/**
 * White header: menu, the AI/keypad switch, the round, and (when the camera does the scoring) the Next player button.
 * [extraMenu] lets the camera screen add its own entries.
 */
@Composable
fun GameTopBar(
    session: GameSession,
    game: DartGame<*>,
    onExit: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNextPlayer: (() -> Unit)?,
    extraMenu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    ObserveGame()
    var menu by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }
    val camera = session.setup.input == InputMode.CAMERA
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600
    val short = configuration.screenHeightDp < 480
    val side = if (short) 40.dp else 46.dp
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = if (short) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            SquareButton(onClick = { menu = true }, size = side) { Icon(Icons.Default.MoreVert, "Menu", tint = Palette.Ink) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Scoreboard") }, onClick = { menu = false; board = true })
                DropdownMenuItem(text = { Text("Undo last dart") }, enabled = game.canUndo, onClick = { menu = false; session.undo() })
                extraMenu { menu = false }
                DropdownMenuItem(text = { Text("Quit game") }, onClick = { menu = false; onExit() })
            }
        }
        SquareButton(onClick = { session.switchInput(if (camera) InputMode.MANUAL else InputMode.CAMERA) }, size = side) {
            Text("AI", color = if (camera) Palette.Teal else Palette.Ink.copy(alpha = 0.4f), fontFamily = Condensed, fontSize = 22.sp)
        }
        val leg = (game as? X01Game)?.let { if (it.config.legsToWin > 1 || it.config.setsToWin > 1) ", LEG ${it.gameState.legsPlayed + 1}" else "" }.orEmpty()
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // With the player buttons on the right there may be no room left: the title then lives on the camera instead.
            if (maxWidth >= 96.dp) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Palette.Ink, fontSize = if (onNextPlayer != null) (if (wide) 24.sp else 18.sp) else 30.sp)) { append(session.setup.title.uppercase()) }
                        if (onNextPlayer == null) withStyle(SpanStyle(color = Palette.Ink.copy(alpha = 0.5f), fontSize = 18.sp)) { append("  R${game.round}$leg") }
                    },
                    fontFamily = Condensed, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onNextPlayer != null) {
            if (onPrevious != null) PlayerButton(if (wide) "\u2039 PREV PLAYER" else "\u2039 PREV", onPrevious, height = side, compact = !wide)
            PlayerButton(if (wide) "NEXT PLAYER \u203A" else "NEXT \u203A", onNextPlayer, enabled = !game.isFinished, height = side, compact = !wide)
        } else {
            SquareButton(onClick = { board = true }, size = side) { Icon(Icons.AutoMirrored.Filled.List, "Scoreboard", tint = Palette.Ink) }
        }
    }
    if (board) {
        ModalBottomSheet(onDismissRequest = { board = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ObserveGame()
                Text(session.setup.kind.title.uppercase() + "  " + session.setup.subtitle, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Scoreboard(game)
                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PlayerButton(label: String, onClick: () -> Unit, enabled: Boolean = true, height: androidx.compose.ui.unit.Dp = 46.dp, compact: Boolean = false) {
    Box(
        Modifier.height(height).clip(RoundedCornerShape(12.dp)).background(Palette.Sand).clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (compact) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Palette.Ink.copy(alpha = if (enabled) 1f else 0.4f), fontFamily = Condensed, fontSize = if (compact) 16.sp else 19.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
fun SquareButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 46.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Palette.Sand).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
