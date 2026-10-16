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
    override val inputSize: Int = shape[1]
    private val outputShape = interpreter.getOutputTensor(0).shape()
    private val input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outputShape.fold(1) { a, b -> a * b } * 4).order(ByteOrder.nativeOrder())

    @Synchronized
    override fun run(image: ColorFrame): List<Detection> {
        require(image.width == inputSize && image.height == inputSize)
        input.rewind()
        val floats = input.asFloatBuffer()
        val rgb = image.rgb
        for (i in rgb.indices) floats.put((rgb[i].toInt() and 0xFF) / 255f)
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val values = FloatArray(output.capacity() / 4)
        output.asFloatBuffer().get(values)
        val dims = outputShape.filter { it > 1 }
        return YoloDecoder.decode(values, dims[0], dims[1], inputSize)
    }

    override fun close() = interpreter.close()

    companion object {
        const val ASSET = "dartnet.tflite"

        /** The loaded model, or null when the model file is not in the app or cannot be loaded. */
        fun loadOrNull(context: Context): TfliteDartNet? = runCatching {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
            TfliteDartNet(Interpreter(model, Interpreter.Options().setNumThreads(4)))
        }.getOrNull()
    }
}
