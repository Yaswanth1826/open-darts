package com.opendarts.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.opendarts.ui.theme.Condensed
import com.opendarts.ui.theme.Palette

/** Full-screen coloured page that stays clear of the system bars. */
@Composable
fun ScreenBox(background: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(background).systemBarsPadding()) { content() }
}

/** A dart seen from the side, tip to the left. */
@Composable
fun DartIcon(color: Color, modifier: Modifier = Modifier, width: Dp = 92.dp) {
    Canvas(modifier.size(width = width, height = width * (22f / 92f))) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val steel = Color(0xFF4A4A4A)
        val tip = Path().apply { moveTo(0f, mid); lineTo(w * 0.28f, mid - h * 0.14f); lineTo(w * 0.28f, mid + h * 0.14f); close() }
        drawPath(tip, steel)
        val barrel = Path().apply {
            moveTo(w * 0.26f, mid - h * 0.15f); lineTo(w * 0.56f, mid - h * 0.2f)
            lineTo(w * 0.56f, mid + h * 0.2f); lineTo(w * 0.26f, mid + h * 0.15f); close()
        }
        drawPath(barrel, steel)
        drawLine(color, Offset(w * 0.55f, mid), Offset(w * 0.78f, mid), strokeWidth = h * 0.12f, cap = StrokeCap.Round)
        val flight = Path().apply {
            moveTo(w * 0.72f, mid - h * 0.08f); lineTo(w * 0.86f, mid - h * 0.48f); lineTo(w, mid - h * 0.48f)
            lineTo(w * 0.96f, mid); lineTo(w, mid + h * 0.48f); lineTo(w * 0.86f, mid + h * 0.48f)
            lineTo(w * 0.72f, mid + h * 0.08f); close()
        }
        drawPath(flight, color)
    }
}

@Composable
fun DashedDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

private val avatarColours = listOf(
    Color(0xFF2E8BC0), Color(0xFFE07A5F), Color(0xFF3D9970), Color(0xFF9B5DE5),
    Color(0xFFE9B44C), Color(0xFFD1495B), Color(0xFF00A6A6), Color(0xFF6C757D),
)

fun avatarColour(index: Int): Color = avatarColours[index.mod(avatarColours.size)]

/** Round player badge: the initial on a colour, with an optional tick when the player is chosen. */
@Composable
fun Avatar(name: String, index: Int, size: Dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(avatarColour(index)).border(BorderStroke(3.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontFamily = Condensed, fontSize = (size.value * 0.5f).sp)
        }
        if (selected) {
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(Palette.Teal).border(BorderStroke(2.dp, Color.White), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.24f))
            }
        }
    }
}

/** A row of mutually exclusive options; the chosen one is lit in the accent colour. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Brown).padding(3.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Palette.Teal else Color.Transparent).clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option).uppercase(), color = if (on) Color.White else Color.White.copy(alpha = 0.7f), fontFamily = Condensed, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}