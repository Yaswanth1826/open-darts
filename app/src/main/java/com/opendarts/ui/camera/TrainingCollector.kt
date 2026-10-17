package com.opendarts.ui.camera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.opendarts.geometry.Point
import com.opendarts.vision.ml.ColorFrame
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Collects real photos from the player's own board and phone, to retrain the network on what it really sees.
 * Every sample is a full-colour JPEG plus a JSON file with the board's four calibration points and the darts the
 * network found, in pixels. Samples stay inside the app's own folder until the player shares them as one zip.
 * `training/from_captures.py` turns that zip into YOLO training data.
 */
class TrainingCollector(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val dir = (context.getExternalFilesDir(null) ?: context.filesDir).let { File(it, "training") }

    @Volatile private var released = false
    @Volatile private var latest: String? = null

    fun count(): Int = dir.listFiles { f -> f.extension == "json" }?.size ?: 0

    /** Saves [frame] with its labels; [onSaved] gets the number of samples now stored (or -1 on failure). */
    fun save(frame: ColorFrame, corners: List<Point>, darts: List<Point>, note: String, onSaved: (Int) -> Unit) {
        if (released || corners.size != 4) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        latest = stamp
        io.execute {
            val saved = runCatching {
                dir.mkdirs()
                File(dir, "$stamp.jpg").outputStream().use { toBitmap(frame).compress(Bitmap.CompressFormat.JPEG, 92, it) }
                val json = JSONObject()
                    .put("width", frame.width)
                    .put("height", frame.height)
                    .put("corners", points(corners))
                    .put("darts", points(darts))
                    .put("note", note)
                File(dir, "$stamp.json").writeText(json.toString())
            }.isSuccess
            onSaved(if (saved) count() else -1)
        }
    }

    /** The player corrected a dart, so the labels of the latest sample are probably wrong: the training script sets it aside for review. */
    fun flagLatest() {
        val stamp = latest ?: return
        io.execute { runCatching { File(dir, "$stamp.flag").writeText("corrected") } }
    }

    /** A chooser intent for a zip of everything collected, or null when nothing is stored. Call off the main thread. */
    fun shareIntent(): Intent? {
        val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return null
        val out = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, "open-darts-training.zip") }
        ZipOutputStream(out.outputStream()).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share training photos")
    }

    fun clear(onDone: () -> Unit) {
        io.execute {
            dir.listFiles()?.forEach { it.delete() }
            File(context.cacheDir, "share").deleteRecursively()
            onDone()
        }
    }

    fun release() {
        released = true
        io.shutdown()
    }

    private fun points(list: List<Point>) = JSONArray().apply { list.forEach { put(JSONArray().put(it.x).put(it.y)) } }

    private fun toBitmap(frame: ColorFrame): Bitmap {
        val pixels = IntArray(frame.width * frame.height) {
            Color.rgb(frame.rgb[it * 3].toInt() and 0xFF, frame.rgb[it * 3 + 1].toInt() and 0xFF, frame.rgb[it * 3 + 2].toInt() and 0xFF)
        }
        return Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
    }
}