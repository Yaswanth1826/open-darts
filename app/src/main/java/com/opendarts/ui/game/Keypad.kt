package com.opendarts.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
fun Keypad(canUndo: Boolean, enabled: Boolean, onHit: (DartHit) -> Unit, onUndo: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Rows share the height; when the screen is short (landscape) the type shrinks with them.
        val row = maxHeight / 8.45f
        val fit = (row.value * 0.6f).coerceIn(13f, 27f)
        val dots = row >= 40.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1.25f)) {
                Key("MISS", null, Palette.KeyLight, enabled, fit * 1.2f) { onHit(DartHit.MISS) }
                Key("BULL", "(25)", Palette.KeyLight, enabled, fit * 1.2f, dots = 0, showDots = dots) { onHit(DartHit.OUTER_BULL) }
                Key("BULL", "(50)", Palette.KeyMid, enabled, fit * 1.2f, dots = 2, showDots = dots) { onHit(DartHit.INNER_BULL) }
            }
            val tints = listOf(Palette.KeyLight, Palette.KeyMid, Palette.KeyDark)
            Multiplier.values().forEachIndexed { m, multiplier ->
                listOf(20 downTo 11, 10 downTo 1).forEach { numbers ->
                    Row(Modifier.weight(1f)) {
                        numbers.forEach { n -> Key(n.toString(), null, tints[m], enabled, fit, dots = if (m == 0) 0 else m + 1, showDots = dots) { onHit(DartHit(n, multiplier)) } }
                    }
                }
            }
            Row(Modifier.weight(1.2f)) {
                Box(Modifier.weight(0.2f).fillMaxSize().background(Palette.KeyLight).border(BorderStroke(0.5.dp, Palette.Ink.copy(alpha = 0.35f))).clickable(enabled = canUndo, onClick = onUndo), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Palette.Ink.copy(alpha = if (canUndo) 1f else 0.3f))
                }
                Box(Modifier.weight(0.35f).fillMaxSize().background(Palette.KeyMid).border(BorderStroke(0.5.dp, Palette.Ink.copy(alpha = 0.35f))).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
                    Text("\u2039 PREV", color = Palette.Ink, fontFamily = Condensed, fontSize = (fit * 0.9f).sp, maxLines = 1)
                }
                Box(Modifier.weight(0.45f).fillMaxSize().background(Palette.Amber).clickable(enabled = enabled, onClick = onNext), contentAlignment = Alignment.Center) {
                    Text("NEXT \u203A", color = Palette.Ink, fontFamily = Condensed, fontSize = fit.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, sub: String?, tint: Color, enabled: Boolean, size: Float, dots: Int = 0, showDots: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxSize().background(tint).border(BorderStroke(0.5.dp, Palette.Ink.copy(alpha = 0.35f))).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Palette.Ink, fontFamily = Condensed, fontSize = size.sp, lineHeight = (size * 1.03f).sp, textAlign = TextAlign.Center, maxLines = 1)
            if (sub != null) Text(sub, color = Palette.Ink, fontFamily = Condensed, fontSize = (size * 0.6f).sp, lineHeight = (size * 0.6f).sp)
            if (dots > 0 && showDots) Text("\u2022".repeat(dots), color = Palette.Ink, fontSize = 10.sp, lineHeight = 8.sp, letterSpacing = 1.sp)
        }
    }
}
