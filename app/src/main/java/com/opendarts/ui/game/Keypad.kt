package com.opendarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.Palette

/** Tap-to-score pad: miss and bulls on top, then singles, doubles and trebles for every number. */
@Composable
fun Keypad(canUndo: Boolean, enabled: Boolean, onHit: (DartHit) -> Unit, onUndo: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.weight(1.25f)) {
            Key("MISS", null, Palette.KeyLight, enabled) { onHit(DartHit.MISS) }
            Key("BULL", "(25)", Palette.KeyLight, enabled, dots = 0) { onHit(DartHit.OUTER_BULL) }
            Key("BULL", "(50)", Palette.KeyMid, enabled, dots = 2) { onHit(DartHit.INNER_BULL) }
        }
        val tints = listOf(Palette.KeyLight, Palette.KeyMid, Palette.KeyDark)
        Multiplier.values().forEachIndexed { m, multiplier ->
            listOf(20 downTo 11, 10 downTo 1).forEach { numbers ->
                Row(Modifier.weight(1f)) {
                    numbers.forEach { n -> Key(n.toString(), null, tints[m], enabled, dots = if (m == 0) 0 else m + 1, big = false) { onHit(DartHit(n, multiplier)) } }
                }
            }
        }
        Row(Modifier.weight(1.2f)) {
            Box(Modifier.weight(0.3f).fillMaxSize().background(Palette.KeyLight).border(BorderStroke(0.5.dp, Palette.Ink.copy(alpha = 0.35f))).clickable(enabled = canUndo, onClick = onUndo), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Palette.Ink.copy(alpha = if (canUndo) 1f else 0.3f))
            }
            Box(Modifier.weight(0.7f).fillMaxSize().background(Palette.Amber).clickable(enabled = enabled, onClick = onNext), contentAlignment = Alignment.Center) {
                Text("NEXT PLAYER", color = Palette.Ink, fontFamily = Condensed, fontSize = 30.sp)
            }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, sub: String?, tint: Color, enabled: Boolean, dots: Int = 0, big: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxSize().background(tint).border(BorderStroke(0.5.dp, Palette.Ink.copy(alpha = 0.35f))).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Palette.Ink, fontFamily = Condensed, fontSize = if (big) 32.sp else 27.sp, lineHeight = if (big) 32.sp else 28.sp, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = Palette.Ink, fontFamily = Condensed, fontSize = 18.sp, lineHeight = 18.sp)
            if (dots > 0) Text("\u2022".repeat(dots), color = Palette.Ink, fontSize = 10.sp, lineHeight = 8.sp, letterSpacing = 1.sp)
        }
    }
}