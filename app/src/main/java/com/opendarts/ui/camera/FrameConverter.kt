package com.opendarts.ui.camera

import androidx.camera.core.ImageProxy
import com.opendarts.vision.GrayFrame
import com.opendarts.vision.ml.ColorFrame
import com.opendarts.vision.ml.YuvConvert

/** Copies the luminance plane into a tightly packed, upright (rotation applied) greyscale frame. */
fun ImageProxy.toUprightGray(): GrayFrame {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val w = width
    val h = height
    val src = ByteArray(w * h)
    for (y in 0 until h) {
        buffer.position(y * rowStride)
        buffer.get(src, y * w, w)
    }
    return when (imageInfo.rotationDegrees) {
        90 -> {
            val out = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[y * w + x]
            GrayFrame(h, w, out)
        }
        180 -> {
            val out = ByteArray(w * h)
            for (i in src.indices) out[src.size - 1 - i] = src[i]
            GrayFrame(w, h, out)
        }
        270 -> {
            val out = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = src[y * w + x]
            GrayFrame(h, w, out)
        }
        else -> GrayFrame(w, h, src)
    }
}
/** One camera frame. The grey and colour images are only built when asked for, and only while the camera still holds the frame. */
class CameraFrame(grey: () -> GrayFrame, private val colour: () -> ColorFrame) {
    val gray: GrayFrame by lazy(grey)
    fun color(): ColorFrame = colour()
}

private fun java.nio.ByteBuffer.toBytes(): ByteArray {
    val bytes = ByteArray(remaining())
    duplicate().get(bytes)
    return bytes
}

/** Converts the YUV frame to an upright RGB image (rotation applied). */
fun ImageProxy.toUprightColor(): ColorFrame {
    val y = planes[0]; val u = planes[1]; val v = planes[2]
    return YuvConvert.toUprightRgb(
        y.buffer.toBytes(), y.rowStride, y.pixelStride,
        u.buffer.toBytes(), u.rowStride, u.pixelStride,
        v.buffer.toBytes(), v.rowStride, v.pixelStride,
        width, height, imageInfo.rotationDegrees,
    )
}
