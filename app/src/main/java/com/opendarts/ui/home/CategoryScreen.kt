package com.opendarts.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.cricket.CricketMode
import com.opendarts.session.Category
import com.opendarts.session.GameKind
import com.opendarts.session.GameSetup
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette

val Category.colour: Color
    get() = when (this) {
        Category.X01 -> Palette.Red
        Category.CRICKET -> Palette.Charcoal
        Category.PRACTICE -> Palette.Orange
        Category.PARTY -> Palette.Teal
    }

private class Choice(val label: String, val note: String, val apply: (GameSetup) -> GameSetup)

private fun choices(category: Category): List<Choice> = when (category) {
    Category.X01 -> emptyList()
    Category.CRICKET -> listOf(
        Choice("STANDARD", "Score on open numbers") { it.copy(kind = GameKind.CRICKET, cricketMode = CricketMode.STANDARD) },
        Choice("CUT-THROAT", "Points go to opponents") { it.copy(kind = GameKind.CRICKET, cricketMode = CricketMode.CUT_THROAT) },
        Choice("NO SCORE", "First to close everything") { it.copy(kind = GameKind.CRICKET, cricketMode = CricketMode.NO_SCORE) },
    )
    Category.PRACTICE -> listOf(
        Choice("AROUND THE CLOCK", GameKind.AROUND_THE_CLOCK.blurb) { it.copy(kind = GameKind.AROUND_THE_CLOCK) },
        Choice("COUNT UP", GameKind.COUNT_UP.blurb) { it.copy(kind = GameKind.COUNT_UP) },
        Choice("JDC CHALLENGE", "Shanghai, doubles and bull") { it.copy(kind = GameKind.JDC_CHALLENGE) },
    )
    Category.PARTY -> emptyList()
}

private val presets = listOf(301, 501, 701)

@Composable
fun CategoryScreen(category: Category, onBack: () -> Unit, onPick: ((GameSetup) -> GameSetup) -> Unit) {
    val colour = category.colour
    ScreenBox(colour) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("${category.title.uppercase()} GAMES", color = Color.White, fontFamily = Condensed, fontSize = 30.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (category) {
                    Category.X01 -> {
                        presets.forEach { n -> Bubble(colour, onClick = { onPick { it.copy(kind = GameKind.X01, x01 = it.x01.copy(startingScore = n)) } }) { BigText(n.toString(), colour) } }
                        CustomX01(colour) { n -> onPick { it.copy(kind = GameKind.X01, x01 = it.x01.copy(startingScore = n)) } }
                    }
                    Category.PARTY -> {
                        Spacer(Modifier.height(60.dp))
                        Text("PARTY GAMES", color = Color.White, fontFamily = Condensed, fontSize = 34.sp)
                        Text("Shanghai, Bob's 27, Killer and more are on the way.", color = Color.White.copy(alpha = 0.85f), fontFamily = CondensedRegular, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                    }
                    else -> choices(category).forEach { c ->
                        Bubble(colour, onClick = { onPick(c.apply) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 26.dp)) {
                                Text(c.label, color = colour, fontFamily = Condensed, fontSize = 34.sp, textAlign = TextAlign.Center, lineHeight = 36.sp)
                                Text(c.note, color = Palette.Charcoal.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(colour: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(210.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun BigText(text: String, colour: Color) = Text(text, color = colour, fontFamily = Condensed, fontSize = 84.sp)

@Composable
private fun CustomX01(colour: Color, onPick: (Int) -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(1001) }
    Bubble(colour, onClick = { onPick(value) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(value.toString(), colour)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton(colour, Icons.Default.Remove) { value = (value - 100).coerceAtLeast(101) }
                StepButton(colour, Icons.Default.Add) { value = (value + 100).coerceAtMost(1501) }
            }
        }
    }
}

@Composable
private fun StepButton(colour: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(colour).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
