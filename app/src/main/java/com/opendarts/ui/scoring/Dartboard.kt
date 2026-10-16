package com.opendarts.ui.scoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import kotlin.math.atan2
import kotlin.math.sqrt

private val order = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

@Composable
fun Dartboard(modifier: Modifier = Modifier, onHit: (DartHit) -> Unit) {
    Canvas(modifier = modifier.aspectRatio(1f).pointerInput(Unit) {
        detectTapGestures { tap ->
            val center = Offset(size.width / 2f, size.height / 2f)
            val dx = tap.x - center.x
            val dy = tap.y - center.y
            val radius = sqrt(dx * dx + dy * dy) / (size.minDimension / 2f)
            if (radius > 0.97f) { onHit(DartHit.MISS); return@detectTapGestures }
            if (radius < 0.055f) { onHit(DartHit.INNER_BULL); return@detectTapGestures }
            if (radius < 0.115f) { onHit(DartHit.OUTER_BULL); return@detectTapGestures }
            val degrees = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
            val number = order[((degrees / 18.0).toInt()) % 20]
            val multiplier = when {
                radius in 0.46f..0.56f -> Multiplier.TRIPLE
                radius >= 0.86f -> Multiplier.DOUBLE
                else -> Multiplier.SINGLE
            }
            onHit(DartHit(number, multiplier))
        }
    }) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val rings = listOf(0.97f, 0.86f, 0.56f, 0.46f, 0.115f, 0.055f)
        val ringColors = listOf(Color(0xFFECE7D8), Color(0xFF171B19), Color(0xFFECE7D8), Color(0xFF171B19), Color(0xFF1B8A5A), Color(0xFFD64A43))
        for (i in rings.indices) {
            drawCircle(ringColors[i], radius * rings[i], center, style = if (i == 0) Stroke(2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
        }
        for (i in 0 until 20) {
            val angle = Math.toRadians(i * 18.0 - 9.0)
            val start = Offset(center.x + (radius * 0.115f * kotlin.math.sin(angle)).toFloat(), center.y - (radius * 0.115f * kotlin.math.cos(angle)).toFloat())
            val end = Offset(center.x + (radius * 0.97f * kotlin.math.sin(angle)).toFloat(), center.y - (radius * 0.97f * kotlin.math.cos(angle)).toFloat())
            drawLine(Color(0xFF101312), start, end, strokeWidth = 1.dp.toPx())
        }
        // Draw the outer double and treble bands as alternating red/green segments.
        for (i in 0 until 20) {
            val startAngle = i * 18f - 9f
            val bandColor = if (i % 2 == 0) Color(0xFFD64A43) else Color(0xFF1B8A5A)
            drawArc(bandColor, startAngle, 18f, false, Offset(center.x - radius * 0.97f, center.y - radius * 0.97f), androidx.compose.ui.geometry.Size(radius * 1.94f, radius * 1.94f), style = Stroke(radius * (0.97f - 0.86f)))
            drawArc(bandColor, startAngle, 18f, false, Offset(center.x - radius * 0.56f, center.y - radius * 0.56f), androidx.compose.ui.geometry.Size(radius * 1.12f, radius * 1.12f), style = Stroke(radius * (0.56f - 0.46f)))
        }
    }
}
