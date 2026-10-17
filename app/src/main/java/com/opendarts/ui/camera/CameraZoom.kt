package com.opendarts.ui.camera

import android.content.Context

/** The camera zoom the player chose, kept between games so the same view is used next time. */
object CameraZoom {
    private const val FILE = "camera"
    private const val KEY = "zoom"

    fun load(context: Context): Float = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY, 1f)

    fun save(context: Context, zoom: Float) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat(KEY, zoom).apply()
    }

    /** The zoom steps offered as buttons, limited to what the camera can do. */
    fun steps(min: Float, max: Float): List<Float> =
        (listOfNotNull(if (min < 0.95f) kotlin.math.ceil(min * 10f - 0.01f) / 10f else null) + listOf(1f, 2f, 3f, 5f)).filter { it >= min - 0.01f && it <= max + 0.01f }
}