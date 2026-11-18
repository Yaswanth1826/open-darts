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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.opendarts.billing.FREE_CAMERA_GAMES
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** Shown when the free camera games are used up. Buying is not wired yet, so the subscribe button is a placeholder. */
@Composable
fun PaywallDialog(onKeypad: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().panel(26.dp, colour = Pal.Raised).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CAMERA SCORING", color = Pal.Lime, fontFamily = Display, fontSize = 13.sp, letterSpacing = 2.sp)
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
            PrimaryButton("Subscribe (coming soon)", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            OutlineButton("Play with the keypad", onKeypad, Modifier.fillMaxWidth())
        }
    }
}
