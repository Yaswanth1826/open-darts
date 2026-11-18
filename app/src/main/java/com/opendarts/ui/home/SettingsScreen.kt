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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.common.SoundSetting
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(SoundSetting.isOn(context)) }
    var tips by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Game")
            Row(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sound and spoken score", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                    Text("A beep for every dart and the score read out after each visit.", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
                }
                Switch(
                    checked = sound, onCheckedChange = { sound = it; SoundSetting.set(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pal.Lime, checkedThumbColor = Pal.OnLime, uncheckedTrackColor = Pal.Raised, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Line),
                )
            }
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { tips = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Camera setup tips", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
            SectionLabel("About", Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { notices = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Open-source notices and credits", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
            Text("Open Darts $version", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
    if (tips) SetupTipsDialog { tips = false }
    if (notices) {
        val text = remember { runCatching { context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() } }.getOrDefault("Notices could not be loaded.") }
        AlertDialog(
            onDismissRequest = { notices = false },
            title = { Text("Credits") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, fontSize = 13.sp) } },
            confirmButton = { TextButton(onClick = { notices = false }) { Text("Close") } },
        )
    }
}
