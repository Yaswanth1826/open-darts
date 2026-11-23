package com.opendarts.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.BullOff
import com.opendarts.game.engine.BullOffState
import com.opendarts.game.engine.Toss
import com.opendarts.game.model.DartHit
import com.opendarts.session.GameSession
import com.opendarts.ui.common.Avatar
import com.opendarts.ui.common.OutlineButton
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal
import kotlinx.coroutines.delay

/**
 * Decides who throws first with the keypad: everyone throws one dart at the bull and enters what it hit (the bot throws for itself).
 * On a tie the tied players throw again, or the players say who was closer. [onWinner] gets the index of the player to throw first.
 * With the camera the same bull-off runs on the game screen instead ([BullOffPanel]) and measures the distance from the bull.
 */
@Composable
fun BullOffScreen(names: List<String>, botIndex: Int, botAverage: Int, onWinner: (Int) -> Unit, onBack: () -> Unit) {
    var state by remember { mutableStateOf(BullOffState(names.size, botIndex)) }
    val next = state.next
    LaunchedEffect(next, state.round) {
        if (next != null && next == botIndex) {
            delay(1000)
            state = state.thrown(next, BullOff.botToss(botAverage))
        }
    }
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            TopBar("Bull-off", onBack)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Everyone throws one dart at the bull. The closest throws first." + if (state.round > 1) " Round ${state.round}." else "",
                    color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
                )
                SectionLabel("Players")
                BullOffRows(names, state, compact = false)
            }
            Column(Modifier.fillMaxWidth().background(Pal.Surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BullOffControls(
                    names, state, handEntry = true, hint = null,
                    onToss = { state = state.thrown(state.next ?: return@BullOffControls, it) },
                    onAgain = { state = state.again() }, onCloser = { state = state.closer(it) }, onUndo = { state = state.undone() }, onStart = onWinner,
                )
            }
        }
    }
}

/** The bull-off on the camera game screen: the camera reads where each dart lands, so the closest to the centre of the bull wins. */
@Composable
fun BullOffPanel(session: GameSession, modifier: Modifier = Modifier) {
    val state = session.bullOff ?: return
    val names = session.game?.players?.map { it.name } ?: return
    Column(modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Bull-off" + (if (state.round > 1) " \u00B7 round ${state.round}" else "") + ": everyone throws one dart at the bull and leaves it in. The closest to the centre throws first.",
            color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
        )
        Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) { BullOffRows(names, state, compact = true) }
        BullOffControls(
            names, state, handEntry = false, hint = "Take the darts out of the board and ${state.winner?.let { names[it] }} throws first.",
            onToss = {}, onAgain = session::bullOffAgain, onCloser = session::bullOffCloser, onUndo = session::bullOffUndo, onStart = { session.bullOffStart(forget = true) },
        )
    }
}

@Composable
private fun BullOffRows(names: List<String>, state: BullOffState, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
        names.forEachIndexed { i, name ->
            val toss = state.tosses[i]
            Row(
                Modifier.fillMaxWidth().panel(14.dp, if (i == state.next) Pal.Raised else Pal.Surface, if (i == state.winner) Pal.Lime else Pal.Line)
                    .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(name, i, if (compact) 26.dp else 36.dp)
                Text(name, color = if (i in state.contenders) Pal.Text else Pal.Muted, fontFamily = Display, fontSize = if (compact) 15.sp else 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    when {
                        toss != null -> BullOff.label(toss)
                        i == state.next && i == state.bot -> "throwing\u2026"
                        i == state.next -> "to throw"
                        i !in state.contenders -> "out"
                        else -> ""
                    },
                    color = if (toss != null && i == state.winner) Pal.Lime else Pal.Muted, fontFamily = Display, fontSize = if (compact) 15.sp else 17.sp,
                )
            }
        }
    }
}

/** What the players can do now. [handEntry] gives buttons for entering a dart; without it the camera reads the darts. */
@Composable
private fun BullOffControls(
    names: List<String>, state: BullOffState, handEntry: Boolean, hint: String?,
    onToss: (Toss) -> Unit, onAgain: () -> Unit, onCloser: (Int) -> Unit, onUndo: () -> Unit, onStart: (Int) -> Unit,
) {
    val winner = state.winner
    val next = state.next
    when {
        winner != null -> {
            Text("${names[winner]} throws first", color = Pal.Text, fontFamily = Display, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (hint != null) Text(hint, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Start game", { onStart(winner) }, Modifier.fillMaxWidth(), height = 48.dp)
        }
        state.tied -> {
            Text("Tied. Throw again, or choose who was closer.", color = Pal.Text, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Throw again", onAgain, Modifier.fillMaxWidth(), height = 48.dp)
            state.leaders.forEach { OutlineButton("${names[it]} was closer", { onCloser(it) }, Modifier.fillMaxWidth(), height = 44.dp) }
        }
        next == state.bot -> Text("${names[next!!]} is throwing\u2026", color = Pal.Muted, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(48.dp))
        next != null && handEntry -> {
            Text("${names[next]}: what did your dart hit?", color = Pal.Text, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Bull", { onToss(Toss(DartHit.INNER_BULL)) }, Modifier.weight(1f))
                OutlineButton("25", { onToss(Toss(DartHit.OUTER_BULL)) }, Modifier.weight(1f))
                OutlineButton("Miss", { onToss(Toss(DartHit.MISS)) }, Modifier.weight(1f))
            }
        }
        next != null -> Text("${names[next]}: throw at the bull and leave the dart in.", color = Pal.Text, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    if (state.tosses.isNotEmpty() && next != state.bot) {
        TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text(if (handEntry) "Undo last dart" else "Wrong read? Remove that dart and throw again", color = Pal.Muted) }
    }
}