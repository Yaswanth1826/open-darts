package com.opendarts.vision

/** 8-bit luminance image, row-major with no padding. */
class GrayFrame(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height) { "pixel buffer does not match ${width}x$height" } }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF
}