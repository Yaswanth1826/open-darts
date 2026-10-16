package com.opendarts.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.game.x01.ThrowStatus
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import com.opendarts.ui.camera.CameraPrototypeScreen
import com.opendarts.ui.scoring.Dartboard

private enum class Page { HOME, X01, CAMERA }

@Composable
fun OpenDartsApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (page) {
            Page.HOME -> HomeScreen(onPlay = { page = Page.X01 }, onCamera = { page = Page.CAMERA })
            Page.X01 -> X01Screen(onBack = { page = Page.HOME })
            Page.CAMERA -> CameraPrototypeScreen(onBack = { page = Page.HOME })
        }
    }
}

@Composable
private fun HomeScreen(onPlay: () -> Unit, onCamera: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("OPEN DARTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("Your game.\nYour practice.\nNo paywall.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A free, offline-first darts companion.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QUICK PLAY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("X01 · 501", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Double out, three darts per visit, undo, board taps and quick score entry.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Start a game") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CAMERA LAB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text("Explore camera scoring", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Early prototype: live preview first; board calibration and dart detection come next.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Open camera prototype") }
            }
        }
        Text("Coming next: Cricket · Around the Clock · Shanghai · Killer · training drills", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}

@Composable
private fun X01Screen(onBack: () -> Unit) {
    var startScore by remember { mutableIntStateOf(501) }
    var game by remember { mutableStateOf(X01Game(X01Config(startingScore = startScore, doubleOut = true))) }
    var revision by remember { mutableIntStateOf(0) }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var feedback by remember { mutableStateOf("Tap a dartboard segment or use quick entry.") }
    var lastStatus by remember { mutableStateOf<ThrowStatus?>(null) }
    // Reading revision makes the screen observe mutations in the plain Kotlin game engine.
    @Suppress("UNUSED_EXPRESSION") val observedRevision = revision

    fun throwHit(hit: DartHit) {
        val result = game.throwDart(hit)
        lastStatus = result.status
        feedback = when (result.status) {
            ThrowStatus.SCORED -> "${hit.points} scored · ${result.remaining} remaining"
            ThrowStatus.NOT_IN_YET -> "Not in yet — hit a double to start scoring"
            ThrowStatus.BUST -> "BUST — score restored to ${result.remaining}"
            ThrowStatus.LEG_WON -> "LEG WON! 🎯"
            ThrowStatus.LEG_ALREADY_FINISHED -> "Leg finished — start a new game"
        }
        revision++
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("←") }
            Column(Modifier.weight(1f)) {
                Text("X01 · DOUBLE OUT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Quick match", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { game.reset(); feedback = "New ${startScore} game started"; lastStatus = null; revision++ }) { Text("Reset") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("REMAINING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                Text(game.remaining.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = if (game.isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text("Visit: ${game.currentVisitPoints}  ·  Darts: ${game.dartsThrown}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        Text("Starting score", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(301, 501, 701).forEach { score ->
                FilterChip(selected = startScore == score, onClick = {
                    startScore = score
                    game = X01Game(X01Config(startingScore = score, doubleOut = true))
                    feedback = "New $score game started"; lastStatus = null; revision++
                }, label = { Text(score.toString()) })
            }
        }
        Text("Tap the board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Dartboard(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { throwHit(it) }
        Text("Quick entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Multiplier.values().forEach { item ->
                FilterChip(selected = multiplier == item, onClick = { multiplier = item }, label = { Text(when (item) { Multiplier.SINGLE -> "Single"; Multiplier.DOUBLE -> "Double"; Multiplier.TRIPLE -> "Treble" }) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            (1..20).toList().chunked(5).forEach { rowNumbers ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowNumbers.forEach { number ->
                        OutlinedButton(onClick = {
                            val m = if (number > 20) Multiplier.SINGLE else multiplier
                            throwHit(DartHit(number, m))
                        }, modifier = Modifier.weight(1f)) { Text(number.toString()) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { throwHit(DartHit.MISS) }, modifier = Modifier.weight(1f)) { Text("MISS") }
                OutlinedButton(onClick = { throwHit(DartHit.OUTER_BULL) }, modifier = Modifier.weight(1f)) { Text("25") }
                OutlinedButton(onClick = { throwHit(DartHit.INNER_BULL) }, modifier = Modifier.weight(1f)) { Text("BULL 50") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(feedback, fontWeight = FontWeight.SemiBold)
                if (lastStatus == ThrowStatus.LEG_WON) Text("Nice finish. Reset to play another leg.", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (game.undo()) feedback = "Last dart undone" else feedback = "Nothing to undo"; lastStatus = null; revision++ }, modifier = Modifier.weight(1f)) { Text("Undo last dart") }
                    Button(onClick = { game = X01Game(X01Config(startingScore = startScore, doubleOut = true)); feedback = "New $startScore game started"; lastStatus = null; revision++ }, modifier = Modifier.weight(1f)) { Text("New game") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
