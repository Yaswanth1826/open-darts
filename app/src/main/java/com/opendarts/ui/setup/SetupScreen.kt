@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.opendarts.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.x01.InOut
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.session.InputMode
import com.opendarts.session.PlayerStore

private const val MAX_PLAYERS = 8

@Composable
fun SetupScreen(session: GameSession, onStart: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val players = remember { PlayerStore(context) }
    val setup = session.draft
    fun update(new: GameSetup) { session.draft = new }

    LaunchedEffect(Unit) { players.load()?.let { if (session.draft.playerNames == listOf("Player 1", "Player 2")) update(session.draft.copy(playerNames = it)) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("New game", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Section("Game")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameKind.values().forEach { k -> FilterChip(selected = setup.kind == k, onClick = { update(setup.copy(kind = k)) }, label = { Text(k.title) }) }
        }
        Text(setup.kind.blurb, style = MaterialTheme.typography.bodySmall)

        Section("Players")
        setup.playerNames.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { v -> update(setup.copy(playerNames = setup.playerNames.toMutableList().also { it[i] = v.take(20) })) },
                    modifier = Modifier.weight(1f), singleLine = true, label = { Text("Player ${i + 1}") },
                )
                if (setup.playerNames.size > 1) OutlinedButton(onClick = { update(setup.copy(playerNames = setup.playerNames.filterIndexed { j, _ -> j != i })) }) { Text("Remove") }
            }
        }
        if (setup.playerNames.size < MAX_PLAYERS) {
            OutlinedButton(onClick = { update(setup.copy(playerNames = setup.playerNames + "Player ${setup.playerNames.size + 1}")) }) { Text("Add player") }
        }

        Section("Options")
        when (setup.kind) {
            GameKind.X01 -> {
                Label("Starting score")
                Chips(listOf(101, 201, 301, 501, 701, 1001), setup.x01.startingScore, { it.toString() }) { update(setup.copy(x01 = setup.x01.copy(startingScore = it))) }
                Label("In")
                Chips(InOut.values().toList(), setup.x01.inMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { update(setup.copy(x01 = setup.x01.copy(inMode = it))) }
                Label("Out")
                Chips(InOut.values().toList(), setup.x01.outMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { update(setup.copy(x01 = setup.x01.copy(outMode = it))) }
                Label("First to (legs)")
                Chips((1..7).toList(), setup.x01.legsToWin, { it.toString() }) { update(setup.copy(x01 = setup.x01.copy(legsToWin = it))) }
                Label("Sets")
                Chips((1..5).toList(), setup.x01.setsToWin, { it.toString() }) { update(setup.copy(x01 = setup.x01.copy(setsToWin = it))) }
            }
            GameKind.CRICKET -> Chips(CricketMode.values().toList(), setup.cricketMode, { it.name.lowercase().replace('_', '-') }) { update(setup.copy(cricketMode = it)) }
            GameKind.AROUND_THE_CLOCK -> {
                Chips(listOf(false, true), setup.clock.skipOnMultiplier, { if (it) "Doubles/trebles skip ahead" else "One number at a time" }) { update(setup.copy(clock = setup.clock.copy(skipOnMultiplier = it))) }
                Chips(listOf(false, true), setup.clock.doublesOnly, { if (it) "Doubles only" else "Any hit counts" }) { update(setup.copy(clock = setup.clock.copy(doublesOnly = it))) }
            }
            GameKind.COUNT_UP -> {
                Label("Rounds")
                Chips(listOf(3, 5, 8, 10, 15), setup.countUpRounds, { it.toString() }) { update(setup.copy(countUpRounds = it)) }
            }
            GameKind.JDC_CHALLENGE -> Text("Fixed format: 57 darts over 19 rounds. Max score 3,380.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Scoring")
        Chips(InputMode.values().toList(), setup.input, { if (it == InputMode.CAMERA) "Camera (auto)" else "Manual (tap)" }) { update(setup.copy(input = it)) }
        if (setup.input == InputMode.CAMERA) {
            Text(
                "Put the phone on a stand facing the board, with the whole board in view. The app finds the board by itself - no calibration needed. You can still correct any dart by hand.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { players.save(setup.playerNames); session.start(setup); onStart() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start game") }
    }
}

@Composable
private fun Section(title: String) = Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = selected == o, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}