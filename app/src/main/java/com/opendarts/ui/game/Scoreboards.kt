package com.opendarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.cricket.CRICKET_NUMBERS
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.engine.DartGame
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.CLOCK_BULL_INDEX
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.practice.JdcChallengeGame
import com.opendarts.game.practice.JdcPart
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Game

@Composable
fun Scoreboard(game: DartGame<*>) {
    ObserveGame()
    when (game) {
        is X01Game -> X01Board(game)
        is CricketGame -> CricketBoard(game)
        is AroundTheClockGame -> ClockBoard(game)
        is CountUpGame -> CountUpBoard(game)
        is JdcChallengeGame -> JdcBoard(game)
    }
}

@Composable
private fun PlayerCard(active: Boolean, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun X01Board(game: X01Game) {
    val state = game.gameState
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.players.forEachIndexed { i, player ->
            val p = state.players[i]
            val active = i == game.currentPlayerIndex && !game.isFinished
            PlayerCard(active) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        val legs = if (game.config.legsToWin > 1 || game.config.setsToWin > 1) "Legs ${p.legs}" + (if (game.config.setsToWin > 1) " · Sets ${p.sets}" else "") + " · " else ""
                        Text("${legs}Avg ${"%.1f".format(p.average)} · Best visit ${p.highestVisit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        if (active && game.config.outMode != InOut.STRAIGHT && p.isIn) {
                            val route = Checkout.suggest(p.remaining, game.config.outMode, game.dartsLeftInTurn.coerceAtLeast(1))
                            if (route != null) Text("Finish: ${Checkout.format(route)}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!p.isIn) Text("Needs a ${if (game.config.inMode == InOut.MASTER) "double or treble" else "double"} to start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(p.remaining.toString(), fontSize = 44.sp, fontWeight = FontWeight.Black, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun markSymbol(marks: Int) = when {
    marks <= 0 -> ""
    marks == 1 -> "/"
    marks == 2 -> "X"
    else -> "\u2297"
}

@Composable
private fun CricketBoard(game: CricketGame) {
    val state = game.gameState
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(44.dp))
                game.players.forEachIndexed { i, p ->
                    val active = i == game.currentPlayerIndex && !game.isFinished
                    Text(p.name, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                        fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
            CRICKET_NUMBERS.forEach { number ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (number == 25) "B" else number.toString(), Modifier.width(44.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    state.players.forEach { p ->
                        Text(markSymbol(p.marksOn(number)), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 22.sp,
                            color = if (p.isClosed(number)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (game.config.mode != CricketMode.NO_SCORE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pts", Modifier.width(44.dp), fontWeight = FontWeight.Bold)
                    state.players.forEach { p -> Text(p.points.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun ClockBoard(game: AroundTheClockGame) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.players.forEachIndexed { i, player ->
            val target = game.gameState.targets[i]
            val active = i == game.currentPlayerIndex && !game.isFinished
            PlayerCard(active) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (target > CLOCK_BULL_INDEX) "Finished" else "Next target", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        when { target > CLOCK_BULL_INDEX -> "\u2713"; target == CLOCK_BULL_INDEX -> "BULL"; else -> target.toString() },
                        fontSize = 40.sp, fontWeight = FontWeight.Black,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountUpBoard(game: CountUpGame) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Round ${minOf(game.round, game.config.rounds)} of ${game.config.rounds}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        game.players.forEachIndexed { i, player ->
            val active = i == game.currentPlayerIndex && !game.isFinished
            PlayerCard(active) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(game.gameState.scores[i].toString(), fontSize = 40.sp, fontWeight = FontWeight.Black,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
@Composable
private fun JdcBoard(game: JdcChallengeGame) {
    val target = game.currentTarget
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (target == null) "Challenge complete" else "Round ${game.round} of ${JdcChallengeGame.TOTAL_ROUNDS} \u00B7 aim at ${target.label}",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold,
        )
        game.players.forEachIndexed { i, player ->
            val p = game.gameState.players[i]
            val active = i == game.currentPlayerIndex && !game.isFinished
            PlayerCard(active) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "10-15: ${p.part(JdcPart.SHANGHAI_LOW)} \u00B7 Doubles: ${p.part(JdcPart.DOUBLES)} \u00B7 15-20: ${p.part(JdcPart.SHANGHAI_HIGH)}" +
                                if (p.shanghais > 0) " \u00B7 Shanghais ${p.shanghais}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Text(p.total.toString(), fontSize = 40.sp, fontWeight = FontWeight.Black,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Big number and small caption per player, for the compact strip shown over the camera. */
fun headline(game: DartGame<*>, index: Int): Pair<String, String> = when (game) {
    is X01Game -> {
        val p = game.gameState.players[index]
        val legs = if (game.config.legsToWin > 1 || game.config.setsToWin > 1) "L${p.legs}" + (if (game.config.setsToWin > 1) " S${p.sets}" else "") + " \u00B7 " else ""
        p.remaining.toString() to "${legs}Avg ${"%.1f".format(p.average)}"
    }
    is CricketGame -> {
        val p = game.gameState.players[index]
        val closed = CRICKET_NUMBERS.count { p.isClosed(it) }
        (if (game.config.mode == CricketMode.NO_SCORE) closed.toString() else p.points.toString()) to "$closed/7 closed"
    }
    is AroundTheClockGame -> {
        val target = game.gameState.targets[index]
        (when { target > CLOCK_BULL_INDEX -> "\u2713"; target == CLOCK_BULL_INDEX -> "BULL"; else -> target.toString() }) to "next"
    }
    is CountUpGame -> game.gameState.scores[index].toString() to "points"
    is JdcChallengeGame -> game.gameState.players[index].total.toString() to "total"
    else -> "" to ""
}