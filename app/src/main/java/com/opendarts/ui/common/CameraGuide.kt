package com.opendarts.ui.common

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** Remembers that the first-time camera guide was shown, so it opens by itself only once. */
object CameraGuide {
    private const val FILE = "camera_guide"

    fun seen(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("seen", false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
    }
}

private class Step(val title: String, val text: String, val draw: DrawScope.() -> Unit)

private val steps = listOf(
    Step("Phone beside the board", "Stand the phone on a shelf or stand a little to one side of the board, facing it. Any angle from straight-on to about 70\u00B0 to the side works. Keep the thrower out of the line between phone and board.") { sideView() },
    Step("The whole board in view", "Put the phone 1\u20132 m from the board so the whole board is in the picture with a little space round it. Use the zoom buttons or pinch if it is cut off or too small.") { frameView() },
    Step("Clear board, still phone", "Start with no darts on the board and keep your hands away while it scans. Then leave the phone where it is for the whole game. Even light with no glare is best.") { clearView() },
    Step("A dart read wrongly?", "Tap the dart in the score panel to correct it, or use Undo. Darts you correct also help the app learn your board. The menu has Rescan board and Align by hand if the board was not found.") { fixView() },
)

/** A short illustrated walk-through of how to place the phone; opened once before the first camera game and from "Setup tips". */
@Composable
fun CameraGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { CameraGuideCard(onDismiss, Modifier.padding(16.dp).widthIn(max = 640.dp)) }
}

@Composable
fun CameraGuideCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(0) }
    val current = steps[step]
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val picture = @Composable { m: Modifier -> Canvas(m.clip(RoundedCornerShape(16.dp)).background(Pal.Bg)) { current.draw(this) } }
    val words = @Composable { m: Modifier ->
        Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(current.title, color = Pal.Text, fontFamily = Display, fontSize = 20.sp)
            Text(current.text, color = Pal.Muted, fontFamily = Body, fontSize = 15.sp)
        }
    }
    val controls = @Composable {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            steps.indices.forEach { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(8.dp).clip(CircleShape).background(if (i == step) Pal.Lime else Pal.Line))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) OutlineButton("Back", { step-- }, Modifier.weight(1f), height = 48.dp)
            else OutlineButton("Skip", onDismiss, Modifier.weight(1f), height = 48.dp)
            if (step < steps.lastIndex) PrimaryButton("Next", { step++ }, Modifier.weight(1f), height = 48.dp)
            else PrimaryButton("Got it", onDismiss, Modifier.weight(1f), height = 48.dp)
        }
    }
    val heading = "CAMERA SETUP  \u00B7  ${step + 1} OF ${steps.size}"
    if (landscape) {
        Column(modifier.fillMaxWidth().panel(24.dp, Pal.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                picture(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(heading, color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, letterSpacing = 1.4.sp)
                    words(Modifier.weight(1f))
                }
            }
            controls()
        }
    } else {
        Column(modifier.fillMaxWidth().panel(24.dp, Pal.Surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(heading, color = Pal.Muted, fontFamily = Body, fontSize = 12.sp, letterSpacing = 1.4.sp)
            picture(Modifier.fillMaxWidth().height(190.dp))
            words(Modifier.heightIn(max = 190.dp))
            controls()
        }
    }
}

private val boardRing = Color(0xFF2A323D)
private val boardFace = Color(0xFF1B212A)

private fun DrawScope.board(centre: Offset, radius: Float) {
    drawCircle(boardRing, radius, centre)
    drawCircle(boardFace, radius * 0.86f, centre)
    drawCircle(Pal.Lime.copy(alpha = 0.55f), radius * 0.86f, centre, style = Stroke(radius * 0.05f))
    drawCircle(Pal.Lime.copy(alpha = 0.55f), radius * 0.5f, centre, style = Stroke(radius * 0.05f))
    drawCircle(Pal.Lime, radius * 0.1f, centre)
}

private fun DrawScope.phone(centre: Offset, width: Float, height: Float, degrees: Float) {
    rotate(degrees, centre) {
        drawRoundRect(Color(0xFFF2F5F9), Offset(centre.x - width / 2, centre.y - height / 2), Size(width, height), CornerRadius(width * 0.18f))
        drawRoundRect(Pal.Bg, Offset(centre.x - width * 0.4f, centre.y - height * 0.42f), Size(width * 0.8f, height * 0.84f), CornerRadius(width * 0.1f))
        drawCircle(Pal.Lime, width * 0.08f, Offset(centre.x, centre.y - height * 0.36f))
    }
}

/** From above: the board on the wall, the phone to one side looking at it. */
private fun DrawScope.sideView() {
    val w = size.width
    val h = size.height
    val boardAt = Offset(w * 0.5f, h * 0.3f)
    drawLine(boardRing, Offset(w * 0.08f, h * 0.12f), Offset(w * 0.92f, h * 0.12f), strokeWidth = 6f)
    board(boardAt, h * 0.2f)
    val phoneAt = Offset(w * 0.8f, h * 0.78f)
    val cone = Path().apply {
        moveTo(phoneAt.x, phoneAt.y); lineTo(boardAt.x + h * 0.24f, boardAt.y + h * 0.02f); lineTo(boardAt.x - h * 0.05f, boardAt.y + h * 0.22f); close()
    }
    drawPath(cone, Pal.Lime.copy(alpha = 0.16f))
    phone(phoneAt, h * 0.15f, h * 0.27f, -28f)
    drawLine(Pal.Muted, Offset(w * 0.12f, h * 0.9f), Offset(w * 0.6f, h * 0.9f), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
    drawCircle(Pal.Muted, h * 0.06f, Offset(w * 0.36f, h * 0.78f))
}

/** The phone's picture with the whole board inside and some space round it. */
private fun DrawScope.frameView() {
    val w = size.width
    val h = size.height
    val frameW = w * 0.62f
    val frameH = h * 0.86f
    val left = (w - frameW) / 2
    val top = (h - frameH) / 2
    drawRoundRect(Color(0xFFF2F5F9), Offset(left - 8f, top - 8f), Size(frameW + 16f, frameH + 16f), CornerRadius(22f), style = Stroke(5f))
    board(Offset(w / 2, h / 2), frameH * 0.36f)
    val corner = 26f
    listOf(Offset(left, top) to Offset(1f, 1f), Offset(left + frameW, top) to Offset(-1f, 1f), Offset(left, top + frameH) to Offset(1f, -1f), Offset(left + frameW, top + frameH) to Offset(-1f, -1f)).forEach { (at, dir) ->
        drawLine(Pal.Lime, at, Offset(at.x + corner * dir.x, at.y), strokeWidth = 6f)
        drawLine(Pal.Lime, at, Offset(at.x, at.y + corner * dir.y), strokeWidth = 6f)
    }
}

/** An empty board with a tick: nothing on it, nobody in front of it. */
private fun DrawScope.clearView() {
    val centre = Offset(size.width / 2, size.height / 2)
    board(centre, size.height * 0.38f)
    val r = size.height * 0.17f
    val tick = Path().apply {
        moveTo(centre.x - r * 0.9f, centre.y); lineTo(centre.x - r * 0.25f, centre.y + r * 0.6f); lineTo(centre.x + r * 0.9f, centre.y - r * 0.6f)
    }
    drawCircle(Color(0xCC090B0F), r * 1.5f, centre)
    drawPath(tick, Pal.Lime, style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A board with a dart on it, linked to the dart slots in the score panel where it can be tapped to correct it. */
private fun DrawScope.fixView() {
    val centre = Offset(size.width * 0.3f, size.height / 2)
    val radius = size.height * 0.38f
    board(centre, radius)
    val dart = Offset(centre.x + radius * 0.3f, centre.y - radius * 0.45f)
    drawCircle(Pal.Amber, 11f, dart)
    drawCircle(Pal.Amber, 22f, dart, style = Stroke(3f))
    val label = Offset(size.width * 0.74f, size.height * 0.5f)
    drawRoundRect(Pal.Raised, Offset(label.x - size.width * 0.17f, label.y - size.height * 0.2f), Size(size.width * 0.34f, size.height * 0.4f), CornerRadius(20f))
    repeat(3) { i ->
        drawRoundRect(if (i == 1) Pal.Amber else Pal.Line, Offset(label.x - size.width * 0.13f + i * size.width * 0.09f, label.y - size.height * 0.08f), Size(size.width * 0.07f, size.height * 0.16f), CornerRadius(8f))
    }
    drawLine(Pal.Amber.copy(alpha = 0.7f), dart, Offset(label.x - size.width * 0.04f, label.y - size.height * 0.08f), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
}
