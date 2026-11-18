package com.opendarts.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/**
 * Tap-to-score pad. Pick Single, Double or Treble, then the number: the pad goes back to Single after every dart.
 * Miss and the bulls sit under the numbers, with undo and the player buttons at the bottom.
 */
@Composable
fun Keypad(canUndo: Boolean, enabled: Boolean, onHit: (DartHit) -> Unit, onUndo: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    val throwDart: (DartHit) -> Unit = { onHit(it); multiplier = Multiplier.SINGLE }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        // Rows share the height; when the screen is short (landscape) the type shrinks with them.
        val unit = (maxHeight - 40.dp) / 6.9f
        val fit = (unit.value * 0.5f).coerceIn(14f, 26f)
        val numberTint = when (multiplier) { Multiplier.SINGLE -> Pal.Text; Multiplier.DOUBLE -> Pal.Sky; Multiplier.TRIPLE -> Pal.Amber }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(0.9f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Multiplier.values().forEach { m ->
                    val on = m == multiplier
                    Key(m.name.lowercase().replaceFirstChar { it.uppercase() }, if (on) Pal.Lime else Pal.Surface, if (on) Pal.OnLime else Pal.Muted, fit * 0.8f, true, border = !on) { multiplier = m }
                }
            }
            (1..20).chunked(5).forEach { numbers ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    numbers.forEach { n -> Key(n.toString(), Pal.Raised, numberTint, fit, enabled) { throwDart(DartHit(n, multiplier)) } }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("MISS", Pal.Surface, Pal.Muted, fit * 0.8f, enabled, border = true) { throwDart(DartHit.MISS) }
                Key("25", Pal.Surface, Pal.Text, fit * 0.9f, enabled, border = true) { throwDart(DartHit.OUTER_BULL) }
                Key("BULL", Pal.Surface, Pal.Coral, fit * 0.8f, enabled, border = true) { throwDart(DartHit.INNER_BULL) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.weight(0.7f).fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Pal.Surface).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(14.dp)).clickable(enabled = canUndo, onClick = onUndo),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Pal.Text.copy(alpha = if (canUndo) 1f else 0.3f)) }
                Key("\u2039 Prev", Pal.Surface, Pal.Text, fit * 0.8f, true, weight = 1f, border = true, onClick = onPrevious)
                Key("Next \u203A", Pal.Lime, Pal.OnLime, fit * 0.9f, enabled, weight = 1.5f, onClick = onNext)
            }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, fill: Color, ink: Color, size: Float, enabled: Boolean, weight: Float = 1f, border: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.weight(weight).fillMaxSize().clip(shape).background(fill).then(if (border) Modifier.border(BorderStroke(1.dp, Pal.Line), shape) else Modifier).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink.copy(alpha = if (enabled) 1f else 0.4f), fontFamily = Display, fontSize = size.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
