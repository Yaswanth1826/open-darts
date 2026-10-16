@file:OptIn(ExperimentalMaterial3Api::class)

package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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

@Composable
private fun ManualGame(session: GameSession, game: DartGame<*>, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.KeyLight).systemBarsPadding()) {
        GameTopBar(session, game, onExit, onNextPlayer = null)
        Column(Modifier.fillMaxWidth().background(Color(0xFFE2E2E2)).padding(bottom = 2.dp)) {
            DartSlots(game, Color(0xFF7A7A7A))
            Text(
                session.message, color = Palette.Ink.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 14.sp, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        PlayerTabs(game, roomy = true)
        Keypad(
            canUndo = game.canUndo, enabled = !game.isFinished,
            onHit = { session.throwDart(it) }, onUndo = { session.undo() }, onNext = { session.nextPlayer() },
            modifier = Modifier.weight(1f),
        )
    }
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
    onNextPlayer: (() -> Unit)?,
    extraMenu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }
    val camera = session.setup.input == InputMode.CAMERA
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            SquareButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu", tint = Palette.Ink) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Scoreboard") }, onClick = { menu = false; board = true })
                DropdownMenuItem(text = { Text("Undo last dart") }, enabled = game.canUndo, onClick = { menu = false; session.undo() })
                extraMenu { menu = false }
                DropdownMenuItem(text = { Text("Quit game") }, onClick = { menu = false; onExit() })
            }
        }
        SquareButton(onClick = { session.switchInput(if (camera) InputMode.MANUAL else InputMode.CAMERA) }) {
            Text("AI", color = if (camera) Palette.Teal else Palette.Ink.copy(alpha = 0.4f), fontFamily = Condensed, fontSize = 22.sp)
        }
        val leg = (game as? X01Game)?.let { if (it.config.legsToWin > 1 || it.config.setsToWin > 1) ", LEG ${it.gameState.legsPlayed + 1}" else "" }.orEmpty()
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Palette.Ink, fontSize = 30.sp)) { append(session.setup.title.uppercase()) }
                withStyle(SpanStyle(color = Palette.Ink.copy(alpha = 0.5f), fontSize = 18.sp)) { append("  R${game.round}$leg") }
            },
            fontFamily = Condensed, maxLines = 1, modifier = Modifier.weight(1f),
        )
        if (onNextPlayer != null) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Sand).clickable(enabled = !game.isFinished, onClick = onNextPlayer).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("NEXT PLAYER", color = Palette.Ink, fontFamily = Condensed, fontSize = 18.sp)
            }
        } else {
            SquareButton(onClick = { board = true }) { Icon(Icons.AutoMirrored.Filled.List, "Scoreboard", tint = Palette.Ink) }
        }
    }
    if (board) {
        ModalBottomSheet(onDismissRequest = { board = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.setup.kind.title.uppercase() + "  " + session.setup.subtitle, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Scoreboard(game)
                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun SquareButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Sand).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
