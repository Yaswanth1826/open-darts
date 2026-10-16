@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.opendarts.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.x01.InOut
import com.opendarts.session.Category
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.session.InputMode
import com.opendarts.session.PlayerStore
import com.opendarts.session.Roster
import com.opendarts.ui.common.Avatar
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.common.Segmented
import com.opendarts.ui.home.colour
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.CondensedRegular
import com.opendarts.ui.theme.Palette

@Composable
fun SetupScreen(session: GameSession, category: Category, onStart: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlayerStore(context) }
    var roster by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var rules by remember { mutableStateOf(false) }
    val setup = session.draft
    val colour = category.colour

    fun update(new: Roster) { roster = new; store.save(new) }
    fun toggle(i: Int) = update(roster.copy(selected = if (i in roster.selected) roster.selected - i else roster.selected + i))

    ScreenBox(colour) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(setup.title.uppercase(), color = Color.White, fontFamily = Condensed, fontSize = 34.sp, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                RuleBadge(setup) { rules = true }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (roster.selected.isEmpty()) {
                    Text("Pick at least one player below.", color = Color.White.copy(alpha = 0.85f), fontFamily = CondensedRegular, fontSize = 20.sp)
                } else {
                    val big = roster.selected.size <= 4
                    FlowRow(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        roster.selected.forEach { i ->
                            Column(Modifier.clickable { editing = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(roster.names[i], i, if (big) 96.dp else 72.dp)
                                Text(roster.names[i].uppercase(), color = Color.White, fontFamily = Condensed, fontSize = if (big) 22.sp else 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(if (big) 110.dp else 84.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("PLAYER NUMBERS ALLOWED: 1~${Roster.SIZE}", color = Color.White, fontFamily = CondensedRegular, fontSize = 15.sp)
                roster.names.indices.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { i ->
                            val chosen = i in roster.selected
                            Column(Modifier.weight(1f).clickable { if (chosen) editing = i else toggle(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(roster.names[i], i, 56.dp, selected = chosen)
                                Text(roster.names[i].uppercase(), color = Color.White.copy(alpha = if (chosen) 1f else 0.8f), fontFamily = CondensedRegular, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp)).border(BorderStroke(2.dp, Color.White), RoundedCornerShape(14.dp))
                            .clickable { rules = true }.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(setup.matchLabel, color = Color.White, fontFamily = Condensed, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
                    }
                    val ready = roster.selected.isNotEmpty()
                    Box(
                        Modifier.weight(1.4f).height(60.dp).clip(RoundedCornerShape(14.dp)).background(if (ready) Color.White else Color.White.copy(alpha = 0.4f))
                            .clickable(enabled = ready) { session.start(setup.copy(playerNames = roster.chosenNames)); onStart() },
                        contentAlignment = Alignment.Center,
                    ) { Text("START GAME", color = colour, fontFamily = Condensed, fontSize = 28.sp, letterSpacing = 1.sp) }
                }
            }
        }
    }

    editing?.let { i ->
        PlayerDialog(
            name = roster.names[i], selected = i in roster.selected,
            onSave = { name -> update(roster.copy(names = roster.names.toMutableList().also { it[i] = name.trim().ifBlank { "Player ${i + 1}" }.take(16) })); editing = null },
            onDeselect = { toggle(i); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (rules) RulesDialog(setup, onChange = { session.draft = it }, onClose = { rules = false })
}

@Composable
private fun RuleBadge(setup: GameSetup, onClick: () -> Unit) {
    val (first, second) = setup.ruleBadge
    Column(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(first, color = Palette.Ink, fontFamily = Condensed, fontSize = 18.sp, lineHeight = 19.sp)
        Text(second, color = Palette.Ink.copy(alpha = 0.7f), fontFamily = CondensedRegular, fontSize = 12.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun PlayerDialog(name: String, selected: Boolean, onSave: (String) -> Unit, onDeselect: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(16) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (selected) TextButton(onClick = onDeselect) { Text("Deselect") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ---- rules ---------------------------------------------------------------------------------------------------------

private enum class Match { OFF, LEGS, SETS }

@Composable
private fun RulesDialog(setup: GameSetup, onChange: (GameSetup) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF2140D0D))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 28.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("RULES", color = Color.White, fontFamily = Condensed, fontSize = 32.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.Teal).clickable(onClick = onClose).padding(horizontal = 22.dp, vertical = 8.dp)) {
                        Text("DONE", color = Color.White, fontFamily = Condensed, fontSize = 20.sp)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (setup.kind) {
                        GameKind.X01 -> X01Rules(setup, onChange)
                        GameKind.CRICKET -> Section("Mode") {
                            Segmented(CricketMode.values().toList(), setup.cricketMode, { it.name.replace('_', '-') }, { onChange(setup.copy(cricketMode = it)) })
                        }
                        GameKind.AROUND_THE_CLOCK -> {
                            Section("Progress") {
                                Segmented(listOf(false, true), setup.clock.skipOnMultiplier, { if (it) "Doubles and trebles skip ahead" else "One number at a time" }, { onChange(setup.copy(clock = setup.clock.copy(skipOnMultiplier = it))) })
                            }
                            Section("Counts") {
                                Segmented(listOf(false, true), setup.clock.doublesOnly, { if (it) "Doubles only" else "Any hit" }, { onChange(setup.copy(clock = setup.clock.copy(doublesOnly = it))) })
                            }
                        }
                        GameKind.COUNT_UP -> Section("Rounds") {
                            Segmented(listOf(3, 5, 8, 10, 15), setup.countUpRounds, { it.toString() }, { onChange(setup.copy(countUpRounds = it)) })
                        }
                        GameKind.JDC_CHALLENGE -> Text("Fixed format: 57 darts over 19 rounds, maximum score 3,380.", color = Color.White.copy(alpha = 0.8f), fontFamily = CondensedRegular, fontSize = 17.sp)
                    }
                    Section("Scoring") {
                        Segmented(listOf(InputMode.CAMERA, InputMode.MANUAL), setup.input, { if (it == InputMode.CAMERA) "Auto camera" else "Manual keypad" }, { onChange(setup.copy(input = it)) })
                    }
                    Text(setup.kind.blurb, color = Color.White.copy(alpha = 0.6f), fontFamily = CondensedRegular, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun X01Rules(setup: GameSetup, onChange: (GameSetup) -> Unit) {
    val x = setup.x01
    val match = when { x.setsToWin > 1 -> Match.SETS; x.legsToWin > 1 -> Match.LEGS; else -> Match.OFF }
    Section("Match") {
        Segmented(Match.values().toList(), match, { when (it) { Match.OFF -> "Match off"; Match.LEGS -> "Legs"; Match.SETS -> "Sets" } }, onSelect = {
            onChange(setup.copy(x01 = when (it) {
                Match.OFF -> x.copy(legsToWin = 1, setsToWin = 1)
                Match.LEGS -> x.copy(legsToWin = 3, setsToWin = 1)
                Match.SETS -> x.copy(legsToWin = 3, setsToWin = 2)
            }))
        })
    }
    if (match == Match.LEGS) Section("First to (legs)") {
        Segmented(listOf(2, 3, 4, 5, 7), x.legsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(legsToWin = it))) })
    }
    if (match == Match.SETS) {
        Section("Sets to win") { Segmented(listOf(2, 3, 4, 5), x.setsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(setsToWin = it))) }) }
        Section("Legs per set") { Segmented(listOf(1, 2, 3, 4, 5), x.legsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(legsToWin = it))) }) }
    }
    Section("In") { Segmented(InOut.values().toList(), x.inMode, { "${it.name} in" }, { onChange(setup.copy(x01 = x.copy(inMode = it))) }) }
    Section("Out") { Segmented(InOut.values().toList(), x.outMode, { "${it.name} out" }, { onChange(setup.copy(x01 = x.copy(outMode = it))) }) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), color = Color.White.copy(alpha = 0.75f), fontFamily = CondensedRegular, fontSize = 15.sp, letterSpacing = 1.sp)
        content()
    }
}
