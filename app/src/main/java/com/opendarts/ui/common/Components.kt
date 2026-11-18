package com.opendarts.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** Full-screen page on the app background that stays clear of the system bars. */
@Composable
fun ScreenBox(modifier: Modifier = Modifier, background: Color = Pal.Bg, content: @Composable () -> Unit) {
    Box(modifier.background(background).systemBarsPadding()) { content() }
}

/** A rounded surface with a hairline edge: the basic building block of every screen. */
fun Modifier.panel(radius: Dp = 22.dp, colour: Color = Pal.Surface, edge: Color = Pal.Line): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(colour).border(BorderStroke(1.dp, edge), shape)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 56.dp) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(if (enabled) Pal.Lime else Pal.Line).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Pal.OnLime else Pal.Muted, fontFamily = Display, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Text, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon button used in top bars. */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size).clip(CircleShape).background(Pal.Raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** Title bar with a back button and an optional trailing slot. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) RoundIconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.Text) }
        Text(title, color = Pal.Text, fontFamily = Display, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = modifier)
}

/** A selectable pill: lime when chosen, quiet otherwise. */
@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).background(if (selected) Pal.Lime else Pal.Raised)
            .border(BorderStroke(1.dp, if (selected) Pal.Lime else Pal.Line), shape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Pal.OnLime else Pal.Text.copy(alpha = if (enabled) 1f else 0.4f), fontFamily = Display, fontSize = 16.sp,
            maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

/** A row of mutually exclusive options; the chosen one is lit in the accent colour. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(Pal.Surface).border(BorderStroke(1.dp, Pal.Line), shape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Pal.Lime else Color.Transparent).clickable { onSelect(option) }.padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = if (on) Pal.OnLime else Pal.Muted, fontFamily = Display, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp)
            }
        }
    }
}

private val avatarColours = listOf(
    Color(0xFFC6F24E), Color(0xFF4CC9F0), Color(0xFF8E7CFF), Color(0xFFFF6B6B),
    Color(0xFFFFC857), Color(0xFF5BE5B0), Color(0xFFFF8AC0), Color(0xFFFF9F43),
)

fun avatarColour(index: Int): Color = avatarColours[index.mod(avatarColours.size)]

/** Round player badge: the initial on the player's colour. [order] adds a small throwing-order number when the player is chosen. */
@Composable
fun Avatar(name: String, index: Int, size: Dp, order: Int? = null, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        Box(Modifier.size(size).clip(CircleShape).background(avatarColour(index)), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Pal.OnLime, fontFamily = Display, fontSize = (size.value * 0.46f).sp)
        }
        if (order != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.5f).clip(CircleShape).background(Pal.Bg).border(BorderStroke(1.5.dp, avatarColour(index)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(order.toString(), color = Pal.Text, fontFamily = Display, fontSize = (size.value * 0.27f).sp, lineHeight = (size.value * 0.27f).sp)
            }
        }
    }
}

/** A dart seen from the side, tip to the left. */
@Composable
fun DartIcon(color: Color, modifier: Modifier = Modifier, width: Dp = 92.dp, steel: Color = Color(0xFF6C7787)) {
    Canvas(modifier.size(width = width, height = width * (22f / 92f))) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
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
