package com.opendarts.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.session.GameKind
import com.opendarts.session.HistoryStore
import com.opendarts.session.MatchHistory
import com.opendarts.session.MatchRecord
import com.opendarts.session.PlayerStats
import com.opendarts.session.PlayerStore
import com.opendarts.session.playerStats
import com.opendarts.ui.common.Avatar
import com.opendarts.ui.common.OutlineButton
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Stats tab: every player's figures over all finished games, and the list of those games. */
@Composable
fun StatsScreen(history: MatchHistory? = null) {
    val context = LocalContext.current
    val book = remember { history ?: HistoryStore(context) }
    val roster = remember { PlayerStore(context).load().names }
    var records by remember { mutableStateOf(book.all()) }
    var open by remember { mutableStateOf<MatchRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val stats = remember(records) { records.playerStats() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Stats", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (records.isEmpty()) {
                Column(Modifier.fillMaxWidth().panel(18.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No finished games yet", color = Pal.Text, fontFamily = Display, fontSize = 18.sp)
                    Text("Play a game to the end and its result and every player's figures appear here.", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
                }
            } else {
                SectionLabel("Players")
                stats.forEach { PlayerCard(it, roster.indexOf(it.name).coerceAtLeast(0)) }
                SectionLabel("Recent games", Modifier.padding(top = 6.dp))
                records.take(50).forEach { MatchRow(it) { open = it } }
                OutlineButton("Clear history", { confirmClear = true }, Modifier.fillMaxWidth().padding(top = 8.dp), height = 48.dp)
            }
        }
    }
    open?.let { record ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("${record.title} \u00B7 ${dateOf(record)}") },
            text = { MatchDetail(record) },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { book.remove(record.id); records = book.all(); open = null }) { Text("Delete") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All finished games and the figures built from them are removed. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { book.clear(); records = book.all(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun dateOf(record: MatchRecord): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(record.id))

@Composable
private fun PlayerCard(s: PlayerStats, colourIndex: Int) {
    Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(s.name, colourIndex, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Pal.Text, fontFamily = Display, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.games} ${if (s.games == 1) "game" else "games"} \u00B7 ${s.wins} won", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
            }
            if (s.x01Games > 0) Column(horizontalAlignment = Alignment.End) {
                Text("%.1f".format(Locale.US, s.average), color = Pal.Lime, fontFamily = Display, fontSize = 26.sp)
                Text("X01 AVERAGE", color = Pal.Muted, fontFamily = Body, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
        if (s.x01Games > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure("Best", s.bestVisit.toString(), Modifier.weight(1f))
            Figure("100+", s.hundredPlus.toString(), Modifier.weight(1f))
            Figure("140+", s.oneFortyPlus.toString(), Modifier.weight(1f))
            Figure("180s", s.oneEighties.toString(), Modifier.weight(1f))
            Figure("Finish", s.checkoutPercent?.let { "$it%" } ?: "-", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Pal.Text, fontFamily = Display, fontSize = 17.sp, maxLines = 1)
        Text(label, color = Pal.Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MatchRow(record: MatchRecord, onClick: () -> Unit) {
    val result = if (record.winners.isEmpty()) "No winner" else record.winners.joinToString(" & ") { it.name } + " won"
    Row(Modifier.fillMaxWidth().panel(16.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(record.players.joinToString(" vs ") { it.name }, color = Pal.Text, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result, color = Pal.Lime, fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(record.title, color = Pal.Text, fontFamily = Display, fontSize = 15.sp, maxLines = 1)
            Text(dateOf(record), color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MatchDetail(record: MatchRecord) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${record.kind.title} \u00B7 ${record.rounds} ${if (record.rounds == 1) "round" else "rounds"} \u00B7 ${if (record.input.name == "CAMERA") "camera" else "keypad"}", fontSize = 13.sp)
        record.players.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name + if (p.won) "  \u2013 winner" else "", fontFamily = Display, fontSize = 16.sp)
                if (record.kind == GameKind.X01) {
                    Text("Average %.1f \u00B7 ${p.darts} darts \u00B7 best visit ${p.bestVisit}".format(Locale.US, p.average), fontSize = 13.sp)
                    Text("100+ ${p.hundredPlus} \u00B7 140+ ${p.oneFortyPlus} \u00B7 180s ${p.oneEighties} \u00B7 checkout ${p.checkoutPercent?.let { "$it%" } ?: "-"}", fontSize = 13.sp)
                }
            }
        }
    }
}
