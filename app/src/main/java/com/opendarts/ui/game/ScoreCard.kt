package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.X01Game
import com.opendarts.ui.common.DartIcon
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette

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

/** The shortest finish for the player on the throw, such as "T20 T20 D20", or null when there is none (or this is not X01). */
fun checkoutHint(game: DartGame<*>): String? {
    if (game !is X01Game || game.isFinished || game.turnOver) return null
    val remaining = game.gameState.players[game.currentPlayerIndex].remaining
    return Checkout.suggest(remaining, game.config.outMode, game.dartsLeftInTurn.coerceAtLeast(1))?.let(Checkout::format)
}

/**
 * The darts of the current turn: a dart icon until it lands, then what it hit. With [onSlot], tapping a thrown dart
 * (or the next empty one) lets the player change, remove or enter its score.
 */
@Composable
fun DartSlots(game: DartGame<*>, iconColour: Color, modifier: Modifier = Modifier, onSlot: ((Int) -> Unit)? = null) {
    ObserveGame()
    Row(modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until game.dartsPerTurn) {
            val hit = game.turnHits.getOrNull(i)
            val tappable = onSlot != null && i <= game.turnHits.size
            BoxWithConstraints(
                Modifier.weight(1f).height(46.dp).then(if (tappable) Modifier.clickable { onSlot?.invoke(i) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (hit == null) DartIcon(iconColour, width = (maxWidth - 8.dp).coerceIn(24.dp, 92.dp)) else Text(hit.toString(), color = Palette.Ink, fontFamily = Condensed, fontSize = 32.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Struck(text: String, dark: Boolean, size: Int = 22) {
    Text(text, color = (if (dark) Palette.Ink else Color.White).copy(alpha = 0.45f), fontFamily = Condensed, fontSize = size.sp, textDecoration = TextDecoration.LineThrough, lineHeight = (size + 2).sp)
}

@Composable
private fun TurnBox(total: Int, dark: Boolean, size: Int = 22) {
    val colour = if (dark) Palette.Ink else Color.White
    Box(Modifier.border(BorderStroke(1.5.dp, colour.copy(alpha = 0.7f))).padding(horizontal = if (size < 20) 6.dp else 12.dp, vertical = 1.dp)) {
        Text(total.toString(), color = colour, fontFamily = Condensed, fontSize = size.sp, lineHeight = (size + 2).sp)
    }
}

/**
 * Big white card over the camera: the current player's score with the darts of this turn above it.
 * With [fill] it stretches to the height it is given and scales the number to match; otherwise it only takes the room it needs.
 */
@Composable
fun CurrentCard(game: DartGame<*>, modifier: Modifier = Modifier, fill: Boolean = false, onSlot: ((Int) -> Unit)? = null) {
    ObserveGame()
    val i = game.currentPlayerIndex
    val s = standing(game, i)
    BoxWithConstraints(modifier.fillMaxWidth().background(Color.White)) {
        val byWidth = maxWidth.value * 0.15f
        val byHeight = if (fill && maxHeight != Dp.Infinity) (maxHeight.value - 130f) * 0.55f else Float.MAX_VALUE
        val bigSize = minOf(byWidth, byHeight, 84f).coerceAtLeast(40f)
        Column(Modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier).padding(horizontal = 14.dp, vertical = 8.dp)) {
            DartSlots(game, Palette.Orange, onSlot = onSlot)
            com.opendarts.ui.common.DashedDivider(Color.Gray.copy(alpha = 0.6f), Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.struck != null) Struck(s.struck, dark = true)
                    TurnBox(s.turn, dark = true)
                }
                Column(Modifier.weight(2.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.big, color = Palette.Charcoal, fontFamily = Condensed, fontSize = bigSize.sp, lineHeight = (bigSize * 1.03f).sp, maxLines = 1)
                    Text(game.players[i].name.uppercase(), color = Palette.Ink, fontFamily = Condensed, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    checkoutHint(game)?.let { Text(it, color = Palette.Teal, fontFamily = Condensed, fontSize = 18.sp, maxLines = 1) }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (s.ppr != null) "PPR" else s.caption.uppercase(), color = Color.Gray, fontFamily = CondensedRegular, fontSize = 16.sp)
                    if (s.ppr != null) Text(s.ppr, color = Palette.Ink, fontFamily = Condensed, fontSize = 28.sp)
                }
            }
        }
    }
}

/**
 * One tab per player, every player visible: up to four share a row, more wrap onto further rows. [roomy] (keypad mode, portrait)
 * is the big banner version with the turn box and PPR line, two to a row; otherwise slim tabs sit under the camera card.
 */
@Composable
fun PlayerTabs(game: DartGame<*>, roomy: Boolean, modifier: Modifier = Modifier) {
    ObserveGame()
    val perRow = if (roomy) 2 else minOf(4, game.players.size)
    // Slim tabs shrink their type as more of them share the width.
    val crowded = !roomy && perRow >= 3
    Column(modifier.fillMaxWidth()) {
        game.players.indices.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val player = game.players[i]
                    val active = i == game.currentPlayerIndex && !game.isFinished
                    val s = standing(game, i)
                    Column(Modifier.weight(1f).background(if (active) Palette.Red else Palette.Charcoal)) {
                        if (roomy) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (s.struck != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { Struck(s.struck, dark = false, size = 17); TurnBox(s.turn, dark = false, size = 17) }
                                    } else if (s.caption.isNotEmpty()) Text(s.caption, color = Color.White.copy(alpha = 0.6f), fontFamily = Condensed, fontSize = 18.sp)
                                    Text(player.name.uppercase(), color = Color.White.copy(alpha = if (active) 1f else 0.55f), fontFamily = Condensed, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(s.big, color = Color.White, fontFamily = Condensed, fontSize = 38.sp, lineHeight = 40.sp, maxLines = 1)
                            }
                            Box(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                                Text(if (s.ppr != null) "PPR: ${s.ppr}" else s.caption.uppercase(), color = Color.White, fontFamily = Condensed, fontSize = 17.sp)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.big, color = Color.White, fontFamily = Condensed, fontSize = if (crowded) 26.sp else 32.sp, lineHeight = if (crowded) 28.sp else 34.sp, maxLines = 1)
                                Text(
                                    player.name.uppercase(), color = Color.White.copy(alpha = if (active) 1f else 0.6f), fontFamily = Condensed,
                                    fontSize = if (crowded) 13.sp else 18.sp, lineHeight = if (crowded) 14.sp else 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(if (active) 8.dp else 4.dp).background(Color.Black.copy(alpha = 0.35f)))
                        }
                    }
                }
            }
        }
    }
}
