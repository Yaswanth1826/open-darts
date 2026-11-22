package com.opendarts.ui.home

import android.content.Intent
import android.net.Uri
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
import com.opendarts.billing.PlayBilling
import com.opendarts.billing.RemoteEntitlements
import com.opendarts.ui.camera.TrainingCollector
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.findActivity
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.common.Segmented
import com.opendarts.ui.common.SoundSetting
import com.opendarts.ui.common.VoiceSpeed
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

@Composable
fun SettingsScreen(entitlements: RemoteEntitlements, billing: PlayBilling) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(SoundSetting.isOn(context)) }
    var speed by remember { mutableStateOf(SoundSetting.speed(context)) }
    var requirement by remember { mutableStateOf(SoundSetting.fullCaller(context)) }
    var simulate by remember { mutableStateOf(entitlements.simulateFreePlan) }
    var tips by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(TrainingCollector.isOn(context)) }
    var notices by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Account")
            AccountSection(entitlements, billing, onDelete = { confirmDelete = true })
            if (entitlements.isDebugBuild) {
                SectionLabel("Test tools (test builds only)", Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Test: behave like the free plan", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                        Text("Only in test builds. Uses your real account: sign-in, the server's free-game counter and the paywall.", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
                    }
                    Switch(
                        checked = simulate, onCheckedChange = { simulate = it; entitlements.simulateFreePlan = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pal.Lime, checkedThumbColor = Pal.OnLime, uncheckedTrackColor = Pal.Raised, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Line),
                    )
                }
                Row(Modifier.fillMaxWidth().panel(18.dp).clickable { entitlements.refresh() }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("Test: refresh plan from the server", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
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
            Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Caller voice speed", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                Segmented(VoiceSpeed.entries, speed, { it.label }, { speed = it; SoundSetting.setSpeed(context, it) })
            }
            val premium = entitlements.access().premium
            Row(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Call what you require", color = if (premium) Pal.Text else Pal.Muted, fontFamily = Display, fontSize = 17.sp)
                    Text(
                        if (premium) "In X01, as a player steps up within checkout range, the caller says how much they need."
                        else "Premium: in X01, as a player steps up within checkout range, the caller says how much they need.",
                        color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = premium && requirement, onCheckedChange = { requirement = it; SoundSetting.setFullCaller(context, it) }, enabled = premium,
                    colors = SwitchDefaults.colors(checkedTrackColor = Pal.Lime, checkedThumbColor = Pal.OnLime, uncheckedTrackColor = Pal.Raised, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Line),
                )
            }
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { tips = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Camera setup tips", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
            if (entitlements.isDebugBuild) {
                SectionLabel("Help improve camera scoring", Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Save camera photos", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                        Text(
                            "Off by default. When on, camera games save photos of your board to Pictures/OpenDarts/Training on this phone. Nothing is uploaded; you can delete them any time from the Gallery.",
                            color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = photos, onCheckedChange = { photos = it; TrainingCollector.setOn(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pal.Lime, checkedThumbColor = Pal.OnLime, uncheckedTrackColor = Pal.Raised, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Line),
                    )
                }
            }
            SectionLabel("About", Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { notices = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Open-source notices and credits", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
            Text("Open Darts $version", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
    if (tips) SetupTipsDialog { tips = false }
    if (confirmDelete) {
        val subscribed = entitlements.access().premium && !entitlements.isDebugBuild
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    if (subscribed) "You have an active subscription. Deleting your account does NOT cancel it, so you would keep being charged. Cancel it in Google Play first. Games and history on this phone are kept."
                    else "This removes your account and subscription link from our server. Games and history on this phone are kept.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; context.findActivity()?.let(entitlements::deleteAccount) }) { Text("Delete") } },
            dismissButton = {
                Row {
                    if (subscribed) TextButton(onClick = {
                        confirmDelete = false
                        val link = "https://play.google.com/store/account/subscriptions?sku=${PlayBilling.PRODUCT}&package=${context.packageName}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    }) { Text("Manage subscription") }
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            },
        )
    }
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
