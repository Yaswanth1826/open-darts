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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.session.PlayerStore
import com.opendarts.ui.common.Avatar
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** The Players tab: the people who throw on this device. Their names are used by the New game screen. */
@Composable
fun PlayersScreen() {
    val context = LocalContext.current
    val store = remember { PlayerStore(context) }
    var roster by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Players", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tap a player to rename them. You choose who throws when you start a game.", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp)
            roster.names.forEachIndexed { i, name ->
                Row(
                    Modifier.fillMaxWidth().panel(18.dp).clickable { editing = i }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(name, i, 40.dp)
                    Text(name, color = Pal.Text, fontFamily = Display, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Edit, "Rename $name", tint = Pal.Muted)
                }
            }
        }
    }
    editing?.let { i ->
        var text by remember(i) { mutableStateOf(roster.names[i]) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename player") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(16) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    roster = roster.copy(names = roster.names.toMutableList().also { it[i] = text.trim().ifBlank { "Player ${i + 1}" }.take(16) })
                    store.save(roster)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
