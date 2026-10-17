package com.opendarts.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.opendarts.geometry.Point
import com.opendarts.vision.ml.ColorFrame
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Collects real photos from the player's own board and phone, to retrain the network on what it really sees.
 * Photos land in Pictures/OpenDarts/Training (visible in the Gallery and the Files app) as plain JPEGs. The board's
 * four calibration points and the darts the network found, in pixels, travel inside each JPEG as a comment, so one
 * file is all there is to copy. `training/label_photos.py` and `training/from_captures.py` turn them into YOLO data.
 */
class TrainingCollector(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("training", Context.MODE_PRIVATE)

    @Volatile private var released = false
    @Volatile var enabled = prefs.getBoolean(ENABLED, true)
        set(value) { field = value; prefs.edit().putBoolean(ENABLED, value).apply() }

    fun count(): Int = prefs.getInt(COUNT, 0)

    fun full(): Boolean = count() >= LIMIT

    /** Saves [frame] with its labels; [onSaved] gets the number of photos now stored (or -1 on failure). */
    fun save(frame: ColorFrame, corners: List<Point>, darts: List<Point>, note: String, onSaved: (Int) -> Unit) {
        if (released || corners.size != 4 || full()) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        io.execute {
            val saved = runCatching {
                val json = JSONObject()
                    .put("width", frame.width)
                    .put("height", frame.height)
                    .put("corners", points(corners))
                    .put("darts", points(darts))
                    .put("note", note)
                write("$stamp.jpg", withComment(jpeg(frame), json.toString()))
                prefs.edit().putInt(COUNT, count() + 1).apply()
            }.isSuccess
            onSaved(if (saved) count() else -1)
        }
    }

    fun clear(onDone: () -> Unit) {
        io.execute {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("${Environment.DIRECTORY_PICTURES}/$FOLDER%"),
                    )
                } else {
                    folder().listFiles()?.forEach { it.delete() }
                }
            }
            prefs.edit().putInt(COUNT, 0).apply()
            onDone()
        }
    }

    fun release() {
        released = true
        io.shutdown()
    }

    private fun write(name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        } else {
            File(folder().apply { mkdirs() }, name).writeBytes(bytes)
        }
    }

    private fun folder() = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), FOLDER)

    private fun points(list: List<Point>) = JSONArray().apply { list.forEach { put(JSONArray().put(it.x).put(it.y)) } }

    private fun jpeg(frame: ColorFrame): ByteArray {
        val pixels = IntArray(frame.width * frame.height) {
            Color.rgb(frame.rgb[it * 3].toInt() and 0xFF, frame.rgb[it * 3 + 1].toInt() and 0xFF, frame.rgb[it * 3 + 2].toInt() and 0xFF)
        }
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 93, out)
        return out.toByteArray()
    }

    companion object {
        const val FOLDER = "OpenDarts/Training"
        const val LIMIT = 700
        private const val ENABLED = "enabled"
        private const val COUNT = "count"

        /** Puts [text] into a JPEG comment segment right after the start-of-image marker. */
        fun withComment(jpeg: ByteArray, text: String): ByteArray {
            val body = text.toByteArray(Charsets.UTF_8)
            require(body.size <= 65533) { "comment too long" }
            val length = body.size + 2
            val out = ByteArrayOutputStream(jpeg.size + body.size + 4)
            out.write(jpeg, 0, 2)
            out.write(0xFF); out.write(0xFE); out.write(length shr 8); out.write(length and 0xFF)
            out.write(body)
            out.write(jpeg, 2, jpeg.size - 2)
            return out.toByteArray()
        }
    }
}