package com.opendarts.ui.scoring

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.opendarts.game.model.DartHit
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Point
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val cream = Color(0xFFEDE3C8)
private val black = Color(0xFF15191A)
private val red = Color(0xFFC8372D)
private val green = Color(0xFF1F7A4D)
private val wire = Color(0xFFB8BDB9)

/** Fraction of half the canvas that the double ring's outer edge occupies; the rest holds the numbers. */
private const val BOARD_FRACTION = 0.84f

/**
 * Tappable dartboard drawn to regulation proportions. [marks] are drawn as dart markers
 * (board millimetres), so the same view shows what the camera detected.
 */
@Composable
fun Dartboard(modifier: Modifier = Modifier, marks: List<Point> = emptyList(), onHit: (DartHit) -> Unit) {
    Canvas(
        modifier = modifier.aspectRatio(1f).pointerInput(Unit) {
            detectTapGestures { tap ->
                val half = min(size.width, size.height) / 2f
                val pxPerMm = half * BOARD_FRACTION / BoardGeometry.DOUBLE_OUTER_RADIUS.toFloat()
                val x = (tap.x - size.width / 2f) / pxPerMm
                val y = -(tap.y - size.height / 2f) / pxPerMm
                onHit(BoardGeometry.hitAt(x.toDouble(), y.toDouble()))
            }
        }
    ) {
        val half = min(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val k = half * BOARD_FRACTION / BoardGeometry.DOUBLE_OUTER_RADIUS.toFloat()

        drawCircle(black, half, centre)
        for (i in 0 until 20) {
            val dark = i % 2 == 0
            val start = i * 18f - 9f - 90f
            ring(centre, k, 57.45, 83.1, start, if (dark) black else cream)
            ring(centre, k, 134.5, 55.0, start, if (dark) black else cream)
            ring(centre, k, 103.0, 8.0, start, if (dark) red else green)
            ring(centre, k, 166.0, 8.0, start, if (dark) red else green)
        }
        for (i in 0 until 20) {
            val a = Math.toRadians(i * 18.0 - 9.0)
            val from = Offset(centre.x + (BoardGeometry.OUTER_BULL_RADIUS * k * sin(a)).toFloat(), centre.y - (BoardGeometry.OUTER_BULL_RADIUS * k * cos(a)).toFloat())
            val to = Offset(centre.x + (BoardGeometry.DOUBLE_OUTER_RADIUS * k * sin(a)).toFloat(), centre.y - (BoardGeometry.DOUBLE_OUTER_RADIUS * k * cos(a)).toFloat())
            drawLine(wire, from, to, strokeWidth = 1f)
        }
        drawCircle(green, (BoardGeometry.OUTER_BULL_RADIUS * k).toFloat(), centre)
        drawCircle(red, (BoardGeometry.INNER_BULL_RADIUS * k).toFloat(), centre)

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = half * 0.09f
            isAntiAlias = true
            isFakeBoldText = true
        }
        for (i in 0 until 20) {
            val a = Math.toRadians(i * 18.0)
            val r = half * 0.92
            drawContext.canvas.nativeCanvas.drawText(
                BoardGeometry.SEGMENT_ORDER[i].toString(),
                centre.x + (r * sin(a)).toFloat(),
                centre.y - (r * cos(a)).toFloat() + paint.textSize * 0.35f,
                paint,
            )
        }
        for (mark in marks) {
            val p = Offset(centre.x + (mark.x * k).toFloat(), centre.y - (mark.y * k).toFloat())
            drawCircle(Color(0xFFFFC857), half * 0.025f, p)
            drawCircle(Color.Black, half * 0.025f, p, style = Stroke(2f))
        }
    }
}

private fun DrawScope.ring(centre: Offset, k: Float, midMm: Double, widthMm: Double, startDegrees: Float, color: Color) {
    val mid = (midMm * k).toFloat()
    drawArc(
        color = color,
        startAngle = startDegrees,
        sweepAngle = 18f,
        useCenter = false,
        topLeft = Offset(centre.x - mid, centre.y - mid),
        size = Size(mid * 2, mid * 2),
        style = Stroke((widthMm * k).toFloat()),
    )
}