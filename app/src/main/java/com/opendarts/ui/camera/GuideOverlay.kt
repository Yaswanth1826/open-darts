package com.opendarts.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.opendarts.geometry.BoardGeometry

/** Where the player placed the board circle, as shares of the frame (radius is a share of the shorter side). */
data class Guide(val x: Double = 0.5, val y: Double = 0.5, val radius: Double = 0.45)

/** A draggable, pinchable circle the player lines up with the outside of the doubles ring. */
@Composable
fun GuideOverlay(guide: Guide, onChange: (Guide) -> Unit, modifier: Modifier = Modifier) {
    val current = rememberUpdatedState(guide)
    val change = rememberUpdatedState(onChange)
    Canvas(
        modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                val g = current.value
                change.value(
                    g.copy(
                        x = (g.x + pan.x / size.width).coerceIn(0.0, 1.0),
                        y = (g.y + pan.y / size.height).coerceIn(0.0, 1.0),
                        radius = (g.radius * zoom).coerceIn(0.1, 0.8),
                    ),
                )
            }
        },
    ) {
        val r = (guide.radius * minOf(size.width, size.height)).toFloat()
        val centre = Offset((guide.x * size.width).toFloat(), (guide.y * size.height).toFloat())
        val green = Color(0xFF00E676)
        val scale = r / BoardGeometry.DOUBLE_OUTER_RADIUS.toFloat()
        listOf(
            BoardGeometry.DOUBLE_OUTER_RADIUS, BoardGeometry.DOUBLE_INNER_RADIUS,
            BoardGeometry.TREBLE_OUTER_RADIUS, BoardGeometry.TREBLE_INNER_RADIUS,
            BoardGeometry.OUTER_BULL_RADIUS,
        ).forEachIndexed { i, mm ->
            drawCircle(
                green.copy(alpha = if (i == 0) 1f else 0.55f), mm.toFloat() * scale, centre,
                style = Stroke(width = if (i == 0) 4f else 2f, pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
        }
        drawLine(Color.Red, centre, Offset(centre.x, centre.y - r), strokeWidth = 4f)
        drawLine(green, Offset(centre.x - 14f, centre.y), Offset(centre.x + 14f, centre.y), strokeWidth = 3f)
        drawLine(green, Offset(centre.x, centre.y - 14f), Offset(centre.x, centre.y + 14f), strokeWidth = 3f)
    }
}
