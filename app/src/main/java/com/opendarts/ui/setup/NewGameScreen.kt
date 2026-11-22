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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.bot.DartBot
import com.opendarts.game.cricket.CricketMode
import com.opendarts.game.practice.CheckoutLevel
import com.opendarts.game.x01.InOut
import com.opendarts.session.Category
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.billing.Access
import com.opendarts.billing.FREE_CAMERA_GAMES
import com.opendarts.session.HistoryStore
import com.opendarts.session.InputMode
import com.opendarts.session.canHaveBot
import com.opendarts.session.x01Average
import com.opendarts.session.PlayerStore
import com.opendarts.session.category
import com.opendarts.session.kinds
import com.opendarts.session.minPlayers
import com.opendarts.session.Roster
import com.opendarts.ui.common.Avatar
import com.opendarts.ui.common.OptionChip
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.Segmented
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

private enum class Match { OFF, LEGS, SETS }

private val presets = listOf(301, 501, 701)

/** One screen to build a game: what to play, the rules, how it is scored and who is throwing. */
@Composable
fun NewGameScreen(session: GameSession, onStart: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlayerStore(context) }
    var roster by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val setup = session.draft
    val botLevel = if (setup.kind.canHaveBot) setup.botLevel else 0
    val ready = roster.selected.size + (if (botLevel > 0) 1 else 0) >= setup.kind.minPlayers
    val firstPlayer = roster.chosenNames.firstOrNull()
    val ownLevel = remember(firstPlayer) { firstPlayer?.let { HistoryStore(context.applicationContext).all().x01Average(it) }?.coerceIn(20, 105) }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    fun update(new: Roster) { roster = new; store.save(new) }
    fun toggle(i: Int) = update(roster.copy(selected = if (i in roster.selected) roster.selected - i else roster.selected + i))
    val start = {
        val names = roster.chosenNames + if (botLevel > 0) listOf("Bot " + DartBot.levelName(botLevel)) else emptyList()
        if (session.start(setup.copy(playerNames = names, botLevel = botLevel))) onStart()
    }
    val count = roster.selected.size + if (botLevel > 0) 1 else 0
    val summary = "${setup.kind.title}${if (setup.kind == GameKind.X01) " ${setup.x01.startingScore}" else ""} \u00B7 $count ${if (count == 1) "player" else "players"}"

    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            TopBar("New game", onBack) {
                if (landscape) PrimaryButton("Start game", start, enabled = ready, height = 44.dp)
            }
            val access = session.entitlements?.access()
            val rules: @Composable () -> Unit = { RulesSection(setup, access) { session.draft = it } }
            val players: @Composable () -> Unit = {
                PlayersSection(roster, if (botLevel > 0) 0 else setup.kind.minPlayers, onToggle = ::toggle, onEdit = { editing = it })
                if (setup.kind.canHaveBot) BotSection(botLevel, ownLevel, premium = access?.premium != false) { session.draft = setup.copy(botLevel = it) }
            }
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { rules() }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { players() }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    rules()
                    players()
                }
                Column(Modifier.fillMaxWidth().background(Pal.Surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary, color = Pal.Muted, fontFamily = Body, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PrimaryButton("Start game", start, enabled = ready, modifier = Modifier.fillMaxWidth())
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
}

@Composable
private fun RulesSection(setup: GameSetup, access: Access?, onChange: (GameSetup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Game") {
            Segmented(Category.entries, setup.kind.category, { it.title }, { c -> if (c != setup.kind.category) onChange(setup.copy(kind = c.kinds.first())) })
            val sameCategory = setup.kind.category.kinds
            if (sameCategory.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sameCategory.forEach { k -> OptionChip(k.title, k == setup.kind, { onChange(setup.copy(kind = k)) }) }
            }
            Text(setup.kind.blurb, color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
        }
        when (setup.kind) {
            GameKind.X01 -> X01Rules(setup, onChange)
            GameKind.CRICKET -> Group("Mode") {
                Segmented(CricketMode.values().toList(), setup.cricketMode, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.replace('_', '-') }, { onChange(setup.copy(cricketMode = it)) })
            }
            GameKind.AROUND_THE_CLOCK -> {
                Group("Progress") {
                    Segmented(listOf(false, true), setup.clock.skipOnMultiplier, { if (it) "Doubles and trebles skip ahead" else "One number at a time" }, { onChange(setup.copy(clock = setup.clock.copy(skipOnMultiplier = it))) })
                }
                Group("Counts") {
                    Segmented(listOf(false, true), setup.clock.doublesOnly, { if (it) "Doubles only" else "Any hit" }, { onChange(setup.copy(clock = setup.clock.copy(doublesOnly = it))) })
                }
            }
            GameKind.COUNT_UP -> Group("Rounds") {
                Segmented(listOf(3, 5, 8, 10, 15), setup.countUpRounds, { it.toString() }, { onChange(setup.copy(countUpRounds = it)) })
            }
            GameKind.JDC_CHALLENGE -> Fixed("Fixed format: 57 darts over 19 rounds, maximum score 3,380.")
            GameKind.BOBS_27 -> Fixed("Fixed format: start on 27, 20 rounds from D1 to D20. A hit adds double the number, a visit with no hit takes it away. Below zero you are out; the highest score wins.")
            GameKind.CHECKOUT_PRACTICE -> {
                Group("Level") {
                    Segmented(CheckoutLevel.entries, setup.checkoutLevel, { it.label }, { onChange(setup.copy(checkoutLevel = it)) })
                }
                Group("Checkouts to try") {
                    Segmented(listOf(5, 10, 15, 20), setup.checkoutAttempts, { it.toString() }, { onChange(setup.copy(checkoutAttempts = it)) })
                }
                Fixed("Easy 2-60, medium 61-110, hard 111-170. Each round everyone gets the same number, double out, three darts. Every checkout is a point.")
            }
            GameKind.ONE_TWENTY_ONE -> {
                Group("Start number") {
                    Segmented(listOf(81, 101, 121), setup.oneTwentyOne.start, { it.toString() }, { onChange(setup.copy(oneTwentyOne = setup.oneTwentyOne.copy(start = it))) })
                }
                Group("Attempts") {
                    Segmented(listOf(5, 10, 15, 20), setup.oneTwentyOne.attempts, { it.toString() }, { onChange(setup.copy(oneTwentyOne = setup.oneTwentyOne.copy(attempts = it))) })
                }
                Fixed("Check out the number (double out) with nine darts, three visits. A checkout moves the target up by one; running out of darts sends you back to the start. The highest number checked out wins.")
            }
            GameKind.SHANGHAI -> {
                Group("Rounds") { Segmented(listOf(7, 20), setup.shanghaiRounds, { if (it == 7) "7 (1 to 7)" else "20 (1 to 20)" }, { onChange(setup.copy(shanghaiRounds = it)) }) }
                Fixed("Round n scores only hits on number n. A single, double and treble in one visit ends the game at once.")
            }
            GameKind.BASEBALL -> Fixed("Fixed format: 9 innings, inning n is number n. Runs: single 1, double 2, treble 3. Most runs wins.")
            GameKind.HALVE_IT -> Fixed("Fixed format: start on 40. Targets: 15, 16, any double, 17, 18, any treble, 19, 20, bull. Miss them all in a visit and your score is halved.")
            GameKind.KILLER -> Fixed("Everyone gets a number and 3 lives. Your own double makes you a killer; a killer's hit on another player's double takes a life. Last one standing wins.")
        }
        Group("Scoring") {
            Segmented(listOf(InputMode.CAMERA, InputMode.MANUAL), setup.input, { if (it == InputMode.CAMERA) "Auto camera" else "Manual keypad" }, { onChange(setup.copy(input = it)) })
            if (setup.input == InputMode.CAMERA && access != null && !access.premium) {
                Text(
                    if (!access.signedIn) "Sign in with Google to use the camera"
                    else if (!access.known) "Checking your plan\u2026"
                    else if (access.freeLeft > 0) "${access.freeLeft} of $FREE_CAMERA_GAMES free camera games left" else "No free camera games left",
                    color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun Fixed(text: String) = Text(text, color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)

@Composable
private fun X01Rules(setup: GameSetup, onChange: (GameSetup) -> Unit) {
    val x = setup.x01
    val custom = x.startingScore !in presets
    Group("Start score") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { n -> OptionChip(n.toString(), x.startingScore == n, { onChange(setup.copy(x01 = x.copy(startingScore = n))) }) }
            OptionChip("Custom", custom, { if (!custom) onChange(setup.copy(x01 = x.copy(startingScore = 1001))) })
        }
        if (custom) {
            Row(Modifier.fillMaxWidth().panel(16.dp, Pal.Raised).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Step(Icons.Default.Remove, "Lower") { onChange(setup.copy(x01 = x.copy(startingScore = (x.startingScore - 100).coerceAtLeast(101)))) }
                Text(x.startingScore.toString(), color = Pal.Text, fontFamily = Display, fontSize = 28.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Step(Icons.Default.Add, "Raise") { onChange(setup.copy(x01 = x.copy(startingScore = (x.startingScore + 100).coerceAtMost(1501)))) }
            }
        }
    }
    val match = when { x.setsToWin > 1 -> Match.SETS; x.legsToWin > 1 -> Match.LEGS; else -> Match.OFF }
    Group("Match") {
        Segmented(Match.values().toList(), match, { when (it) { Match.OFF -> "Single game"; Match.LEGS -> "Legs"; Match.SETS -> "Sets" } }, onSelect = {
            onChange(setup.copy(x01 = when (it) {
                Match.OFF -> x.copy(legsToWin = 1, setsToWin = 1)
                Match.LEGS -> x.copy(legsToWin = 3, setsToWin = 1)
                Match.SETS -> x.copy(legsToWin = 3, setsToWin = 2)
            }))
        })
    }
    if (match == Match.LEGS) Group("First to (legs)") {
        Segmented(listOf(2, 3, 4, 5, 7), x.legsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(legsToWin = it))) })
    }
    if (match == Match.SETS) {
        Group("Sets to win") { Segmented(listOf(2, 3, 4, 5), x.setsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(setsToWin = it))) }) }
        Group("Legs per set") { Segmented(listOf(1, 2, 3, 4, 5), x.legsToWin, { it.toString() }, { onChange(setup.copy(x01 = x.copy(legsToWin = it))) }) }
    }
    Group("Start with") { Segmented(InOut.values().toList(), x.inMode, { "${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }} in" }, { onChange(setup.copy(x01 = x.copy(inMode = it))) }) }
    Group("Finish with") { Segmented(InOut.values().toList(), x.outMode, { "${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }} out" }, { onChange(setup.copy(x01 = x.copy(outMode = it))) }) }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Pal.Lime).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Pal.OnLime)
    }
}

@Composable
private fun PlayersSection(roster: Roster, minPlayers: Int, onToggle: (Int) -> Unit, onEdit: (Int) -> Unit) {
    Group("Players") {
        Text(
            if (roster.selected.size < minPlayers) "Pick at least $minPlayers ${if (minPlayers == 1) "player" else "players"}." else "Throwing order follows the numbers. Tap a chosen player to rename or remove them.",
            color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            roster.names.indices.forEach { i ->
                val order = roster.selected.indexOf(i).takeIf { it >= 0 }?.plus(1)
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.clip(shape).background(if (order != null) Pal.Raised else Pal.Surface)
                        .border(BorderStroke(1.dp, if (order != null) Pal.Lime else Pal.Line), shape)
                        .clickable { if (order != null) onEdit(i) else onToggle(i) }.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(roster.names[i], i, 36.dp, order)
                    Text(roster.names[i], color = if (order != null) Pal.Text else Pal.Muted, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Picks a computer opponent: none, one of the levels, or one that plays like the first player has been playing. */
@Composable
private fun BotSection(level: Int, ownLevel: Int?, premium: Boolean, onPick: (Int) -> Unit) {
    Group("Computer opponent") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip("None", level == 0, { onPick(0) })
            DartBot.LEVELS.forEach { OptionChip("${it.label} \u00B7 ${it.average}", level == it.average, { onPick(it.average) }) }
            if (ownLevel != null && DartBot.LEVELS.none { it.average == ownLevel }) OptionChip("Like you \u00B7 $ownLevel", level == ownLevel, { onPick(ownLevel) })
        }
        Text(
            if (premium) "The number is its three-dart average. It throws after you, and you can play on the camera or the keypad."
            else "Premium: play against the bot at six levels. The number is its three-dart average.",
            color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        content()
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
                if (selected) TextButton(onClick = onDeselect) { Text("Remove from game") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
