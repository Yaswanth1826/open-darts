package com.opendarts.ui.camera

import android.content.Context
import com.opendarts.geometry.Point

/** Remembers where the four calibration targets were tapped, as fractions of the frame, per orientation. */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_calibration", Context.MODE_PRIVATE)

    private fun key(portrait: Boolean) = if (portrait) "portrait" else "landscape"

    fun load(portrait: Boolean): List<Point>? {
        val raw = prefs.getString(key(portrait), null) ?: return null
        val points = raw.split(';').mapNotNull {
            val parts = it.split(',')
            if (parts.size == 2) parts[0].toDoubleOrNull()?.let { x -> parts[1].toDoubleOrNull()?.let { y -> Point(x, y) } } else null
        }
        return points.takeIf { it.size == 4 }
    }

    fun save(portrait: Boolean, points: List<Point>) {
        require(points.size == 4)
        prefs.edit().putString(key(portrait), points.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun hasAny() = load(true) != null || load(false) != null
}