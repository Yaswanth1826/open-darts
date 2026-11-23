package com.opendarts.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.engine.BullOff
import com.opendarts.game.model.DartHit
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
 * Decides who throws first: everyone throws one dart at the bull and enters what it hit (the bot throws for itself). On a tie
 * the tied players throw again, or the players say who was closer. [onWinner] gets the index of the player to throw first.
 */
@Composable
fun BullOffScreen(names: List<String>, botIndex: Int, botAverage: Int, onWinner: (Int) -> Unit, onBack: () -> Unit) {
    var contenders by remember { mutableStateOf(names.indices.toList()) }
    val results = remember { mutableStateMapOf<Int, DartHit>() }
    var round by remember { mutableIntStateOf(1) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val next = contenders.firstOrNull { it !in results }
    val leaders = if (next == null) BullOff.leaders(results) else emptyList()
    val winner = picked ?: leaders.singleOrNull()

    LaunchedEffect(next, round) {
        if (next != null && next == botIndex) {
            delay(1000)
            results[next] = BullOff.botDart(botAverage)
        }
    }

    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            TopBar("Bull-off", onBack)
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Everyone throws one dart at the bull. The closest throws first." + if (round > 1) " Round $round." else "",
                    color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
                )
                SectionLabel("Players")
                names.forEachIndexed { i, name ->
                    val here = i in contenders
                    val hit = results[i]
                    Row(
                        Modifier.fillMaxWidth().panel(16.dp, if (i == next) Pal.Raised else Pal.Surface, if (i == winner) Pal.Lime else Pal.Line).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(name, i, 36.dp)
                        Text(name, color = if (here) Pal.Text else Pal.Muted, fontFamily = Display, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            when {
                                hit != null -> BullOff.label(hit)
                                i == next && i == botIndex -> "throwing\u2026"
                                i == next -> "to throw"
                                !here -> "out"
                                else -> ""
                            },
                            color = if (hit != null && BullOff.rank(hit) > 0) Pal.Lime else Pal.Muted, fontFamily = Display, fontSize = 17.sp,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Pal.Surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    winner != null -> {
                        Text("${names[winner]} throws first", color = Pal.Text, fontFamily = Display, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        PrimaryButton("Start game", { onWinner(winner) }, Modifier.fillMaxWidth())
                    }
                    next == null -> {
                        Text("Tied. Throw again, or choose who was closer.", color = Pal.Text, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        PrimaryButton("Throw again", { contenders = leaders; results.clear(); round++ }, Modifier.fillMaxWidth())
                        leaders.forEach { OutlineButton("${names[it]} was closer", { picked = it }, Modifier.fillMaxWidth(), height = 48.dp) }
                    }
                    next == botIndex -> Text("${names[next]} is throwing\u2026", color = Pal.Muted, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(56.dp))
                    else -> {
                        Text("${names[next]}: what did your dart hit?", color = Pal.Text, fontFamily = Body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("Bull", { results[next] = DartHit.INNER_BULL }, Modifier.weight(1f))
                            OutlineButton("25", { results[next] = DartHit.OUTER_BULL }, Modifier.weight(1f))
                            OutlineButton("Miss", { results[next] = DartHit.MISS }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}