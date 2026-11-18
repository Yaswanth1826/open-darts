package com.opendarts.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val count = if (category == Category.X01) presets.size + 1 else choices(category).size
                val landscape = maxWidth > maxHeight && count > 0
                // On a wide screen the bubbles sit side by side and shrink to fit, so nothing needs scrolling.
                val bubble = if (landscape) minOf(210.dp, maxHeight - 24.dp, (maxWidth - 24.dp) / count - 16.dp).coerceAtLeast(96.dp) else 210.dp
                val layout: @Composable (@Composable () -> Unit) -> Unit = { content ->
                    if (landscape) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { content() }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) { content() }
                }
                layout {
                when (category) {
                    Category.X01 -> {
                        presets.forEach { n -> Bubble(colour, bubble, onClick = { onPick { it.copy(kind = GameKind.X01, x01 = it.x01.copy(startingScore = n)) } }) { BigText(n.toString(), colour, bubble) } }
                        CustomX01(colour, bubble) { n -> onPick { it.copy(kind = GameKind.X01, x01 = it.x01.copy(startingScore = n)) } }
                    }
                    Category.PARTY -> {
                        Spacer(Modifier.height(60.dp))
                        Text("PARTY GAMES", color = Color.White, fontFamily = Condensed, fontSize = 34.sp)
                        Text("Shanghai, Bob's 27, Killer and more are on the way.", color = Color.White.copy(alpha = 0.85f), fontFamily = CondensedRegular, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                    }
                    else -> choices(category).forEach { c ->
                        Bubble(colour, bubble, onClick = { onPick(c.apply) }) {
                            val scale = bubble.value / 210f
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = (26 * scale).dp)) {
                                Text(c.label, color = colour, fontFamily = Condensed, fontSize = (34 * scale).sp, textAlign = TextAlign.Center, lineHeight = (36 * scale).sp)
                                Text(c.note, color = Palette.Charcoal.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = (14 * scale).sp, textAlign = TextAlign.Center, lineHeight = (16 * scale).sp)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun Bubble(colour: Color, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.White).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun BigText(text: String, colour: Color, bubble: Dp) = Text(text, color = colour, fontFamily = Condensed, fontSize = (84 * bubble.value / 210f).sp)

@Composable
private fun CustomX01(colour: Color, bubble: Dp, onPick: (Int) -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(1001) }
    val scale = bubble.value / 210f
    Bubble(colour, bubble, onClick = { onPick(value) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(value.toString(), colour, bubble)
            Row(horizontalArrangement = Arrangement.spacedBy((18 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton(colour, Icons.Default.Remove, (44 * scale).dp) { value = (value - 100).coerceAtLeast(101) }
                StepButton(colour, Icons.Default.Add, (44 * scale).dp) { value = (value + 100).coerceAtMost(1501) }
            }
        }
    }
}

@Composable
private fun StepButton(colour: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(colour).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.64f))
    }
}
