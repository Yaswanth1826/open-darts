package com.opendarts.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.opendarts.billing.FREE_CAMERA_GAMES
import com.opendarts.billing.PlayBilling
import com.opendarts.billing.RemoteEntitlements
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/**
 * Shown when the camera is refused. What it offers depends on why: no account yet, the plan could not be checked,
 * or the free games are used up (subscribe). It closes itself once the camera is allowed.
 */
@Composable
fun PaywallDialog(account: RemoteEntitlements, billing: PlayBilling, onKeypad: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val access = account.access()
    LaunchedEffect(access.canUseCamera) { if (access.canUseCamera) onDismiss() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().panel(26.dp, colour = Pal.Raised).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CAMERA SCORING", color = Pal.Lime, fontFamily = Display, fontSize = 13.sp, letterSpacing = 2.sp)
            when {
                !account.available -> {
                    Text("Not available in this build", color = Pal.Text, fontFamily = Display, fontSize = 22.sp)
                    Text("Camera scoring needs an account, and this build has none set up. The keypad works as always.", color = Pal.Muted, fontFamily = Body, fontSize = 15.sp)
                }
                !access.signedIn -> {
                    Text("Sign in to use the camera", color = Pal.Text, fontFamily = Display, fontSize = 22.sp)
                    Text(
                        "Camera scoring needs a free Google account, so your $FREE_CAMERA_GAMES free games and any subscription stay with you. The keypad needs no account.",
                        color = Pal.Muted, fontFamily = Body, fontSize = 15.sp,
                    )
                    PrimaryButton(if (account.busy) "Signing in\u2026" else "Continue with Google", onClick = { context.findActivity()?.let(account::signIn) }, enabled = !account.busy, modifier = Modifier.fillMaxWidth())
                }
                !access.known -> {
                    Text("Could not check your plan", color = Pal.Text, fontFamily = Display, fontSize = 22.sp)
                    Text("Connect to the internet and try again.", color = Pal.Muted, fontFamily = Body, fontSize = 15.sp)
                    PrimaryButton("Try again", onClick = { account.refresh() }, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Text("Your $FREE_CAMERA_GAMES free camera games are used", color = Pal.Text, fontFamily = Display, fontSize = 22.sp)
                    Text(
                        "Subscribe to keep scoring every dart automatically. The keypad stays free, with every game mode and unlimited matches.",
                        color = Pal.Muted, fontFamily = Body, fontSize = 15.sp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Darts scored by the camera, on your phone", "Corrections and undo on every dart", "Works with any board and room").forEach {
                            Row { Text("\u2022  $it", color = Pal.Text, fontFamily = Body, fontSize = 14.sp) }
                        }
                    }
                    PlanButtons(billing.plans, emphasise = true) { plan -> context.findActivity()?.let { billing.purchase(it, plan) } }
                    OutlineButton("Restore purchases", { billing.restore(manual = true) }, Modifier.fillMaxWidth())
                }
            }
            (account.message ?: billing.message)?.let { Text(it, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp) }
            OutlineButton("Play with the keypad", onKeypad, Modifier.fillMaxWidth())
        }
    }
}
