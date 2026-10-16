package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.DartGame
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

/** The darts of the current turn: a dart icon until it lands, then what it hit. */
@Composable
fun DartSlots(game: DartGame<*>, iconColour: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until game.dartsPerTurn) {
            val hit = game.turnHits.getOrNull(i)
            Box(Modifier.widthIn(min = 92.dp), contentAlignment = Alignment.Center) {
                if (hit == null) DartIcon(iconColour) else Text(hit.toString(), color = Palette.Ink, fontFamily = Condensed, fontSize = 32.sp)
            }
        }
    }
}

@Composable
private fun Struck(text: String, dark: Boolean) {
    Text(text, color = (if (dark) Palette.Ink else Color.White).copy(alpha = 0.45f), fontFamily = Condensed, fontSize = 22.sp, textDecoration = TextDecoration.LineThrough, lineHeight = 24.sp)
}

@Composable
private fun TurnBox(total: Int, dark: Boolean) {
    val colour = if (dark) Palette.Ink else Color.White
    Box(Modifier.border(BorderStroke(1.5.dp, colour.copy(alpha = 0.7f))).padding(horizontal = 12.dp, vertical = 1.dp)) {
        Text(total.toString(), color = colour, fontFamily = Condensed, fontSize = 22.sp, lineHeight = 24.sp)
    }
}

/** Big white card over the camera: the current player's score with the darts of this turn above it. */
@Composable
fun CurrentCard(game: DartGame<*>, modifier: Modifier = Modifier) {
    val i = game.currentPlayerIndex
    val s = standing(game, i)
    Column(modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp)) {
        DartSlots(game, Palette.Orange)
        com.opendarts.ui.common.DashedDivider(Color.Gray.copy(alpha = 0.6f), Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.struck != null) Struck(s.struck, dark = true)
                TurnBox(s.turn, dark = true)
            }
            Column(Modifier.weight(2.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.big, color = Palette.Charcoal, fontFamily = Condensed, fontSize = 76.sp, lineHeight = 78.sp, maxLines = 1)
                Text(game.players[i].name.uppercase(), color = Palette.Ink, fontFamily = Condensed, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(if (s.ppr != null) "PPR" else s.caption.uppercase(), color = Color.Gray, fontFamily = CondensedRegular, fontSize = 16.sp)
                if (s.ppr != null) Text(s.ppr, color = Palette.Ink, fontFamily = Condensed, fontSize = 28.sp)
            }
        }
    }
}

/**
 * One tab per player. [roomy] (keypad mode) is the big banner version with the turn box and PPR line;
 * otherwise a slim tab sits under the camera card.
 */
@Composable
fun PlayerTabs(game: DartGame<*>, roomy: Boolean, modifier: Modifier = Modifier) {
    val few = game.players.size <= 2
    val scroll = rememberScrollState()
    Row(modifier.fillMaxWidth().then(if (few) Modifier else Modifier.horizontalScroll(scroll))) {
        game.players.forEachIndexed { i, player ->
            val active = i == game.currentPlayerIndex && !game.isFinished
            val s = standing(game, i)
            val tabModifier = if (few) Modifier.weight(1f) else Modifier.width(170.dp)
            Column(tabModifier.background(if (active) Palette.Red else Palette.Charcoal)) {
                if (roomy) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (s.struck != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Struck(s.struck, dark = false); TurnBox(s.turn, dark = false) }
                            } else if (s.caption.isNotEmpty()) Text(s.caption, color = Color.White.copy(alpha = 0.6f), fontFamily = Condensed, fontSize = 18.sp)
                            Text(player.name.uppercase(), color = Color.White.copy(alpha = if (active) 1f else 0.55f), fontFamily = Condensed, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(s.big, color = Color.White, fontFamily = Condensed, fontSize = 62.sp, lineHeight = 64.sp, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                        Text(if (s.ppr != null) "PPR: ${s.ppr}" else s.caption.uppercase(), color = Color.White, fontFamily = Condensed, fontSize = 17.sp)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.big, color = Color.White, fontFamily = Condensed, fontSize = 38.sp, lineHeight = 40.sp, maxLines = 1)
                        Text(player.name.uppercase(), color = Color.White.copy(alpha = if (active) 1f else 0.6f), fontFamily = Condensed, fontSize = 18.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = 0.35f)))
                }
            }
        }
    }
}
