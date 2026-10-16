package com.opendarts.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import com.opendarts.vision.GrayFrame
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Saves camera frames so misread darts can be studied and used to tune or train detection.
 * Each capture writes a raw greyscale PNG (exactly what the detector saw) and an annotated copy showing the board
 * fit and the detected dart. They land in Pictures/OpenDarts (Android 10+) or the app's own files folder.
 */
class DebugCapture(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor()

    fun save(frame: GrayFrame, label: String, fit: Homography?, darts: List<Point>, onSaved: (String) -> Unit = {}) {
        io.execute {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val base = "$stamp-${label.filter { it.isLetterOrDigit() || it == '-' }.take(40)}"
            val raw = toBitmap(frame)
            val place = runCatching {
                write("$base-raw.png", raw)
                write("$base-marked.png", annotate(raw, fit, darts))
            }.getOrNull()
            onSaved(if (place != null) base else "")
        }
    }

    fun release() = io.shutdown()

    private fun toBitmap(frame: GrayFrame): Bitmap {
        val pixels = IntArray(frame.width * frame.height) {
            val v = frame.pixels[it].toInt() and 0xFF
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
    }

    private fun annotate(raw: Bitmap, fit: Homography?, darts: List<Point>): Bitmap {
        val copy = raw.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 230, 118); style = Paint.Style.STROKE; strokeWidth = 2f }
        if (fit != null) {
            for (radius in RINGS) {
                val path = android.graphics.Path()
                for (i in 0..120) {
                    val a = Math.toRadians(i * 3.0)
                    val p = fit.apply(radius * Math.sin(a), -radius * Math.cos(a))
                    if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                canvas.drawPath(path, line)
            }
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
        darts.forEach { canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), 10f, mark) }
        return copy
    }

    private fun write(name: String, bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenDarts")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            context.contentResolver.openOutputStream(uri)?.use { compress(bitmap, it) }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OpenDarts").apply { mkdirs() }
            File(dir, name).outputStream().use { compress(bitmap, it) }
        }
    }

    private fun compress(bitmap: Bitmap, out: OutputStream) = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)

    private companion object {
        /** Board radii in mm: bull, outer bull, treble inner/outer, double inner/outer. */
        val RINGS = listOf(6.35, 15.9, 99.0, 107.0, 162.0, 170.0)
    }
}
