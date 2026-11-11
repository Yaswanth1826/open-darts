package com.opendarts.ui.camera

import android.content.Context
import com.opendarts.vision.ml.ColorFrame
import com.opendarts.vision.ml.DartNet
import com.opendarts.vision.ml.Detection
import com.opendarts.vision.ml.YoloDecoder
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The trained dart detector, run with TensorFlow Lite. The model file is `assets/dartnet.tflite`
 * (see the `training` folder); without it the app falls back to the classic detection.
 */
class TfliteDartNet private constructor(private val interpreter: Interpreter) : DartNet, AutoCloseable {
    private val shape = interpreter.getInputTensor(0).shape()
    /** Models are exported either as [1, H, W, 3] or, less often, as [1, 3, H, W]. */
    private val channelsFirst = shape[1] == 3 && shape[3] != 3
    override val inputSize: Int = if (channelsFirst) shape[2] else shape[1]
    private val outputShape = interpreter.getOutputTensor(0).shape()
    private val input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outputShape.fold(1) { a, b -> a * b } * 4).order(ByteOrder.nativeOrder())

    private val scratch = FloatArray(inputSize * inputSize * 3)

    @Volatile private var closed = false

    @Synchronized
    override fun run(image: ColorFrame): List<Detection> {
        if (closed) return emptyList()
        require(image.width == inputSize && image.height == inputSize)
        input.rewind()
        val rgb = image.rgb
        val values = scratch
        if (channelsFirst) {
            val plane = inputSize * inputSize
            for (c in 0 until 3) for (i in 0 until plane) values[c * plane + i] = LUT[rgb[i * 3 + c].toInt() and 0xFF]
        } else {
            for (i in rgb.indices) values[i] = LUT[rgb[i].toInt() and 0xFF]
        }
        input.asFloatBuffer().put(values)
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val result = FloatArray(output.capacity() / 4)
        output.asFloatBuffer().get(result)
        val dims = outputShape.filter { it > 1 }
        return YoloDecoder.decode(result, dims[0], dims[1], inputSize)
    }

    /** Waits for a running look to finish, so the interpreter is never freed while it is in use. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    companion object {
        const val ASSET = "dartnet.tflite"
        private val LUT = FloatArray(256) { it / 255f }

        /** The loaded model, or null when the model file is not in the app or cannot be loaded. */
        fun loadOrNull(context: Context): TfliteDartNet? = runCatching {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
            TfliteDartNet(Interpreter(model, Interpreter.Options().setNumThreads(4)))
        }.getOrNull()
    }
}
