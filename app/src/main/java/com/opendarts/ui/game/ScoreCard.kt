package com.opendarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.party.HalveItGame
import com.opendarts.game.party.KillerGame
import com.opendarts.game.party.NumberRoundsGame
import com.opendarts.game.practice.Bobs27Game
import com.opendarts.game.practice.TargetDrillGame
import com.opendarts.game.practice.CheckoutPracticeGame
import com.opendarts.game.practice.JdcChallengeGame
import com.opendarts.game.practice.OneTwentyOneGame
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Game
import com.opendarts.ui.common.DartIcon
import com.opendarts.ui.common.avatarColour
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** What a player's tab shows: the big number plus the smaller figures around it. */
class Standing(val big: String, val caption: String, val struck: String?, val ppr: String?, val turn: Int)

fun standing(game: DartGame<*>, index: Int): Standing {
    val active = index == game.currentPlayerIndex && !game.isFinished
    if (game is X01Game) {
        val p = game.gameState.players[index]
        val match = if (game.config.legsToWin > 1 || game.config.setsToWin > 1) "L${p.legs}" + (if (game.config.setsToWin > 1) " S${p.sets}" else "") else ""
        return Standing(p.remaining.toString(), match, if (active) (p.remaining + p.turnPoints).toString() else null, "%.1f".format(p.average), if (active) p.turnPoints else 0)
    }
    val (big, caption) = headline(game, index)
    return Standing(big, caption, null, null, if (active) game.turnHits.sumOf { it.points } else 0)
}

/** What the next dart is aimed at in the games that set a target, such as "AIM D5" or "CHECK OUT 87"; null for the others. */
fun aimHint(game: DartGame<*>): String? {
    if (game.isFinished) return null
    return when (game) {
        is Bobs27Game -> game.currentTarget?.let { "AIM  D$it" }
        is TargetDrillGame -> game.currentTarget?.let { "AIM  ${if (it == 25) "BULL" else "D$it"}" }
        is CheckoutPracticeGame -> game.remaining?.let { "CHECK OUT  $it" }
        is OneTwentyOneGame -> game.gameState.players[game.currentPlayerIndex].let { "TARGET  ${it.target}  \u00B7  ${game.dartsLeft(game.currentPlayerIndex)} DARTS" }
        is NumberRoundsGame -> game.currentNumber?.let { "AIM  $it" }
        is HalveItGame -> game.currentTarget?.let { "AIM  ${it.label.uppercase()}" }
        is JdcChallengeGame -> game.currentTarget?.let { "AIM  ${it.label.uppercase()}" }
        is KillerGame -> game.gameState.let { s ->
            val me = game.currentPlayerIndex
            if (s.killers[me]) "KILLER  \u00B7  HIT THEIR DOUBLES" else "HIT  D${s.numbers[me]}  TO BECOME A KILLER"
        }
        else -> null
    }
}

/** The shortest finish for the player on the throw, such as "T20 T20 D20", or null when there is none (or there is no finish to show). */
fun checkoutHint(game: DartGame<*>): String? {
    if (game is CheckoutPracticeGame) {
        if (game.isFinished || game.turnOver) return null
        return game.remaining?.let { Checkout.suggest(it, InOut.DOUBLE, game.dartsLeftInTurn.coerceAtLeast(1)) }?.let(Checkout::format)
    }
    if (game is OneTwentyOneGame) {
        if (game.isFinished || game.turnOver) return null
        return Checkout.suggest(game.gameState.players[game.currentPlayerIndex].left, InOut.DOUBLE, game.dartsLeftInTurn.coerceAtLeast(1))?.let(Checkout::format)
    }
    if (game !is X01Game || game.isFinished || game.turnOver) return null
    val remaining = game.gameState.players[game.currentPlayerIndex].remaining
    return Checkout.suggest(remaining, game.config.outMode, game.dartsLeftInTurn.coerceAtLeast(1))?.let(Checkout::format)
}

/**
 * The darts of the current turn: a chip per dart showing what it hit, empty until it lands. With [onSlot], tapping a thrown
 * dart (or the next empty one) lets the player change, remove or enter its score. With [onDelete], a thrown dart also has a bin
 * button on its chip that removes it at once.
 */
@Composable
fun DartSlots(game: DartGame<*>, modifier: Modifier = Modifier, onSlot: ((Int) -> Unit)? = null, onDelete: ((Int) -> Unit)? = null) {
    ObserveGame()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until game.dartsPerTurn) {
            val hit = game.turnHits.getOrNull(i)
            val tappable = onSlot != null && i <= game.turnHits.size
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.weight(1f).height(46.dp).clip(shape).background(if (hit != null) Pal.Lime.copy(alpha = 0.14f) else Pal.Surface)
                    .border(BorderStroke(1.dp, if (hit != null) Pal.Lime else Pal.Line), shape)
                    .then(if (tappable && hit == null) Modifier.clickable { onSlot?.invoke(i) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (hit == null) DartIcon(Pal.Line, width = 40.dp, steel = Pal.Line) else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight().then(if (tappable) Modifier.clickable { onSlot?.invoke(i) } else Modifier), contentAlignment = Alignment.Center) {
                        Text(hit.label, color = Pal.Lime, fontFamily = Display, fontSize = if (onDelete != null) 17.sp else 20.sp, maxLines = 1)
                    }
                    if (onDelete != null) {
                        Box(Modifier.width(36.dp).fillMaxHeight().background(Pal.Coral.copy(alpha = 0.18f)).clickable { onDelete(i) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, "Delete dart ${i + 1}", tint = Pal.Coral, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The player on the throw: name, the big score, the finish route and the darts of this turn.
 * With [fill] it stretches to the height it is given and scales the number to match; otherwise it only takes the room it needs.
 */
@Composable
fun CurrentCard(game: DartGame<*>, modifier: Modifier = Modifier, fill: Boolean = false, onSlot: ((Int) -> Unit)? = null, onDelete: ((Int) -> Unit)? = null, onMenu: (() -> Unit)? = null) {
    ObserveGame()
    val i = game.currentPlayerIndex
    val s = standing(game, i)
    BoxWithConstraints(modifier.fillMaxWidth().panel(26.dp, Pal.Raised)) {
        val byWidth = maxWidth.value * 0.27f
        val byHeight = if (fill && maxHeight != Dp.Infinity) (maxHeight.value - 150f) * 0.6f else Float.MAX_VALUE
        val bigSize = minOf(byWidth, byHeight, 84f).coerceAtLeast(36f)
        Column(Modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(avatarColour(i)))
                Text(game.players[i].name.uppercase(), color = Pal.Text, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val side = if (s.ppr != null) "AVG ${s.ppr}" else s.caption
                if (side.isNotEmpty()) Text(side, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1)
                if (onMenu != null) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Pal.Surface).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = Pal.Text)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.big, color = Pal.Lime, fontFamily = Display, fontSize = bigSize.sp, lineHeight = (bigSize * 1.05f).sp, maxLines = 1)
                    if (s.struck != null) Text("from ${s.struck}  \u00B7  visit ${s.turn}", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1)
                    aimHint(game)?.let { Text(it, color = Pal.Sky, fontFamily = Display, fontSize = 16.sp, maxLines = 1) }
                    checkoutHint(game)?.let { Text("FINISH  $it", color = Pal.Amber, fontFamily = Display, fontSize = 16.sp, maxLines = 1) }
                }
            }
            DartSlots(game, onSlot = onSlot, onDelete = onDelete)
        }
    }
}

/** One chip per player, every player visible: up to four share a row, more wrap onto further rows. The player on the throw is outlined. */
@Composable
fun PlayerTabs(game: DartGame<*>, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    ObserveGame()
    val count = game.players.size
    val next = (game.currentPlayerIndex + 1) % count
    val previous = (game.currentPlayerIndex + count - 1) % count
    val back = game.turnHits.isEmpty() && game.canGoBack()
    val perRow = minOf(4, game.players.size)
    val crowded = perRow >= 3
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        game.players.indices.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i ->
                    val active = i == game.currentPlayerIndex && !game.isFinished
                    val s = standing(game, i)
                    val shape = RoundedCornerShape(16.dp)
                    // Tapping the next player hands the throw over; at the start of a turn the previous player is the way back.
                    val hint = when {
                        onTap == null || game.isFinished || i == game.currentPlayerIndex -> null
                        i == previous && back -> "\u2039 BACK"
                        i == next && game.turnHits.isNotEmpty() -> "NEXT \u203A"
                        else -> null
                    }
                    Column(
                        Modifier.weight(1f).clip(shape).background(if (active) Pal.Raised else Pal.Surface)
                            .border(BorderStroke(if (active) 1.5.dp else 1.dp, if (active) Pal.Lime else if (hint == "NEXT \u203A") Pal.Lime.copy(alpha = 0.5f) else Pal.Line), shape)
                            .then(if (onTap != null && !active && !game.isFinished) Modifier.clickable { onTap(i) } else Modifier)
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            game.players[i].name.uppercase(), color = if (active) Pal.Text else Pal.Muted, fontFamily = Display,
                            fontSize = if (crowded) 11.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                        Text(s.big, color = if (active) Pal.Lime else Pal.Text, fontFamily = Display, fontSize = if (crowded) 22.sp else 28.sp, maxLines = 1)
                        val detail = if (s.ppr != null) "AVG ${s.ppr}" else s.caption
                        if (hint != null) Text(hint, color = if (hint.startsWith("NEXT")) Pal.Lime else Pal.Muted, fontFamily = Display, fontSize = 12.sp, maxLines = 1)
                        else if (!crowded && detail.isNotEmpty()) Text(detail, color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
