package com.opendarts.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import kotlin.math.min

/**
 * The user taps four reference points on the live preview: where the outer edge of the double ring meets
 * the wires between 20|1, 6|10, 3|19 and 11|14. A grid is then drawn from those taps so they can check the fit.
 */
@Composable
fun CalibrationScreen(onDone: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val taps = remember { mutableStateListOf<Point>() }
    var frameAspect by remember { mutableStateOf(0.0) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calibrate camera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val step = taps.size
        Text(
            if (step < 4) "Tap ${step + 1} of 4: the outer edge of the double ring on the wire between ${BoardGeometry.CALIBRATION_LABELS[step]}."
            else "Check that the rings line up with the board. Tap Save, or Undo to adjust.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CameraPermissionGate {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CameraView(Modifier.fillMaxSize(), zoom = remember { CameraZoom.load(context) }, onFrame = { frame -> frameAspect = frame.gray.width.toDouble() / frame.gray.height })
                if (frameAspect > 0) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(frameAspect) {
                            detectTapGestures { tap ->
                                val r = fitRect(size.width.toFloat(), size.height.toFloat(), frameAspect)
                                val nx = (tap.x - r.left) / r.width
                                val ny = (tap.y - r.top) / r.height
                                if (taps.size < 4 && nx in 0f..1f && ny in 0f..1f) taps.add(Point(nx.toDouble(), ny.toDouble()))
                            }
                        }
                    ) {
                        val r = fitRect(size.width, size.height, frameAspect)
                        fun toView(p: Point) = Offset(r.left + p.x.toFloat() * r.width, r.top + p.y.toFloat() * r.height)
                        taps.forEachIndexed { i, p ->
                            drawCircle(Color(0xFFFFC857), 10f, toView(p), style = Stroke(3f))
                            drawCircle(Color(0xFFFFC857), 3f, toView(p))
                        }
                        if (taps.size == 4) {
                            // Board plane -> frame fractions, drawn as rings and the 20 axis.
                            val h = Homography.fromFourPoints(BoardGeometry.CALIBRATION_POINTS, taps.toList())
                            listOf(BoardGeometry.INNER_BULL_RADIUS, BoardGeometry.OUTER_BULL_RADIUS, BoardGeometry.TREBLE_INNER_RADIUS,
                                BoardGeometry.TREBLE_OUTER_RADIUS, BoardGeometry.DOUBLE_INNER_RADIUS, BoardGeometry.DOUBLE_OUTER_RADIUS).forEach { radius ->
                                val pts = BoardGeometry.outline(radius, 90).map { toView(h.apply(it)) }
                                for (i in pts.indices) drawLine(Color(0xFF63E6BE), pts[i], pts[(i + 1) % pts.size], strokeWidth = 2f)
                            }
                            drawLine(Color(0xFFFFC857), toView(h.apply(Point(0.0, 0.0))), toView(h.apply(Point(0.0, 170.0))), strokeWidth = 3f)
                        }
                    }
                }
            }
        }
        Text("Tip: mount the phone still, pointing at the board from the side or below, so the whole board and your darts' flights are visible. Good, even light helps.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            OutlinedButton(onClick = { if (taps.isNotEmpty()) taps.removeAt(taps.lastIndex) }, modifier = Modifier.weight(1f)) { Text("Undo") }
            Button(enabled = taps.size == 4 && frameAspect > 0, onClick = {
                store.save(frameAspect < 1.0, taps.toList())
                onSaved()
                onDone()
            }, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}

private class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitRect(viewW: Float, viewH: Float, aspect: Double): FitRect {
    val w = min(viewW, viewH * aspect.toFloat())
    val h = w / aspect.toFloat()
    return FitRect((viewW - w) / 2f, (viewH - h) / 2f, w, h)
}