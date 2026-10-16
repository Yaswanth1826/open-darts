package com.opendarts.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Point
import com.opendarts.vision.TrackerState

/**
 * Draws the board the camera found (rings and the line towards the 20) and the darts it has seen.
 * The overlay must have the same aspect ratio as the camera frame so frame pixels scale linearly.
 */
@Composable
fun BoardOverlay(status: CameraStatus, darts: List<Point>, modifier: Modifier = Modifier, showBoard: Boolean = true) {
    Canvas(modifier) {
        val h = status.boardToPixel
        if (h == null || status.frameWidth == 0) return@Canvas
        val sx = size.width / status.frameWidth
        val sy = size.height / status.frameHeight
        fun toView(p: Point): Offset {
            val q = h.apply(p)
            return Offset(q.x.toFloat() * sx, q.y.toFloat() * sy)
        }
        if (showBoard) drawBoard(status.tracker == TrackerState.READY) { toView(it) }
        darts.forEach { p ->
            val c = Offset(p.x.toFloat() * sx, p.y.toFloat() * sy)
            drawCircle(Color.White, 12f, c, style = Stroke(3f))
            drawCircle(Color(0xFFFF6B6B), 4f, c)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoard(ready: Boolean, toView: (Point) -> Offset) {
    run {        val ring = if (ready) Color(0xFF63E6BE) else Color(0xFFFFC857)
        listOf(
            BoardGeometry.DOUBLE_OUTER_RADIUS to 3f,
            BoardGeometry.DOUBLE_INNER_RADIUS to 1.5f,
            BoardGeometry.TREBLE_OUTER_RADIUS to 1.5f,
            BoardGeometry.TREBLE_INNER_RADIUS to 1.5f,
            BoardGeometry.OUTER_BULL_RADIUS to 1.5f,
        ).forEach { (radius, width) ->
            val pts = BoardGeometry.outline(radius, 96).map(toView)
            for (i in pts.indices) drawLine(ring.copy(alpha = 0.85f), pts[i], pts[(i + 1) % pts.size], strokeWidth = width)
        }
        // Wires between segments, so a wrongly turned board is obvious at a glance.
        for (i in 0 until 20) {
            val a = Math.toRadians(i * 18.0 + 9.0)
            val from = Point(BoardGeometry.OUTER_BULL_RADIUS * Math.sin(a), BoardGeometry.OUTER_BULL_RADIUS * Math.cos(a))
            val to = Point(BoardGeometry.DOUBLE_OUTER_RADIUS * Math.sin(a), BoardGeometry.DOUBLE_OUTER_RADIUS * Math.cos(a))
            drawLine(ring.copy(alpha = 0.35f), toView(from), toView(to), strokeWidth = 1f)
        }
        drawLine(Color(0xFFFF6B6B), toView(Point(0.0, BoardGeometry.OUTER_BULL_RADIUS)), toView(Point(0.0, BoardGeometry.DOUBLE_OUTER_RADIUS + 12.0)), strokeWidth = 4f)
    }
}