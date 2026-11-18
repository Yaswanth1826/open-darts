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
import com.opendarts.billing.FREE_CAMERA_GAMES
import com.opendarts.billing.LocalEntitlements
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.common.SoundSetting
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

@Composable
fun SettingsScreen(entitlements: LocalEntitlements) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(SoundSetting.isOn(context)) }
    var plan by remember { mutableStateOf(entitlements.access()) }
    var simulate by remember { mutableStateOf(entitlements.simulateFreePlan) }
    var tips by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Camera plan")
            Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (plan.premium) "Camera scoring unlocked" else "Camera scoring: free trial", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                Text(
                    if (plan.premium) "This test build is unlocked, so the camera is not limited."
                    else if (plan.freeLeft > 0) "${plan.freeLeft} of $FREE_CAMERA_GAMES free camera games left. The keypad is always free."
                    else "Free camera games used. The keypad is always free.",
                    color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
                )
            }
            if (entitlements.isDebugBuild) {
                Row(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Test: behave like the free plan", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                        Text("Only in test builds. Shows the free-game counter and the paywall.", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
                    }
                    Switch(
                        checked = simulate, onCheckedChange = { simulate = it; entitlements.simulateFreePlan = it; plan = entitlements.access() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pal.Lime, checkedThumbColor = Pal.OnLime, uncheckedTrackColor = Pal.Raised, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Line),
                    )
                }
                Row(Modifier.fillMaxWidth().panel(18.dp).clickable { entitlements.resetFreeGames(); plan = entitlements.access() }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("Test: reset free camera games", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                }
            }
            SectionLabel("Game", Modifier.padding(top = 12.dp))
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
