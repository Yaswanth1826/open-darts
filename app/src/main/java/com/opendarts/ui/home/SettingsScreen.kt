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
import com.opendarts.billing.PlayBilling
import com.opendarts.billing.RemoteEntitlements
import com.opendarts.ui.camera.TrainingCollector
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.common.SectionLabel
import com.opendarts.ui.common.findActivity
import com.opendarts.ui.common.SetupTipsDialog
import com.opendarts.ui.common.SoundSetting
import com.opendarts.ui.common.TopBar
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

@Composable
fun SettingsScreen(entitlements: RemoteEntitlements, billing: PlayBilling) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(SoundSetting.isOn(context)) }
    val plan = entitlements.access()
    var simulate by remember { mutableStateOf(entitlements.simulateFreePlan) }
    var tips by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(TrainingCollector.isOn(context)) }
    var notices by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Camera plan")
            Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (plan.premium) "Camera scoring unlocked" else "Camera scoring: free trial", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                Text(
                    if (plan.premium && entitlements.isDebugBuild) "This test build is unlocked, so the camera is not limited."
                    else if (plan.premium) "Your subscription is active."
                    else if (!entitlements.available) "Camera scoring is not available in this build. The keypad is always free."
                    else if (!plan.signedIn) "Sign in with Google to use your $FREE_CAMERA_GAMES free camera games. The keypad is always free."
                    else if (!plan.known) "Could not check your plan yet. Connect to the internet."
                    else if (plan.freeLeft > 0) "${plan.freeLeft} of $FREE_CAMERA_GAMES free camera games left. The keypad is always free."
                    else "Free camera games used. The keypad is always free.",
                    color = Pal.Muted, fontFamily = Body, fontSize = 13.sp,
                )
                if (plan.signedIn && !plan.premium && plan.known && plan.freeLeft == 0) {
                    PrimaryButton(billing.price?.let { "Subscribe \u00B7 $it / month" } ?: "Subscribe (not available yet)", onClick = { context.findActivity()?.let(billing::purchase) }, enabled = billing.price != null, height = 44.dp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            if (entitlements.available) {
                SectionLabel("Account", Modifier.padding(top = 12.dp))
                if (entitlements.signedIn) {
                    Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Signed in", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                        Text(entitlements.email.orEmpty(), color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth().panel(18.dp).clickable { billing.restore(manual = true) }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Restore purchases", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                    }
                    Row(Modifier.fillMaxWidth().panel(18.dp).clickable { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/account/subscriptions?sku=${PlayBilling.PRODUCT}&package=${context.packageName}"))) }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Manage subscription", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                    }
                    Row(Modifier.fillMaxWidth().panel(18.dp).clickable { context.findActivity()?.let(entitlements::signOut) }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Sign out", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
                    }
                    Row(Modifier.fillMaxWidth().panel(18.dp).clickable { confirmDelete = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Delete my account", color = Pal.Muted, fontFamily = Display, fontSize = 17.sp)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().panel(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Needed for camera scoring only. The keypad and your history stay on this phone and need no account.", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
                        PrimaryButton(if (entitlements.busy) "Signing in\u2026" else "Continue with Google", onClick = { context.findActivity()?.let(entitlements::signIn) }, enabled = !entitlements.busy, height = 48.dp, modifier = Modifier.fillMaxWidth())
                    }
                }
                (entitlements.message ?: billing.message)?.let { Text(it, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
            }
            if (entitlements.isDebugBuild) {
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
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { tips = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Camera setup tips", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
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
            SectionLabel("About", Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().panel(18.dp).clickable { notices = true }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("Open-source notices and credits", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            }
            Text("Open Darts $version", color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
    if (tips) SetupTipsDialog { tips = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This removes your account and subscription link from our server. Cancel the subscription itself in Google Play. Games and history on this phone are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; context.findActivity()?.let(entitlements::deleteAccount) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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
