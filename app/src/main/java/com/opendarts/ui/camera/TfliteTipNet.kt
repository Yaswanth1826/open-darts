package com.opendarts.ui.camera

import android.content.Context
import com.opendarts.vision.ml.ColorFrame
import com.opendarts.vision.ml.TipNet
import com.opendarts.vision.ml.TipReading
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The optional second-stage tip network (`assets/tiprefine.tflite`, see the `training` folder). It takes a
 * square patch and gives [dx, dy, tip] with the offsets in patch pixels. Without the file the app works as before.
 */
class TfliteTipNet private constructor(private val interpreter: Interpreter) : TipNet, AutoCloseable {
    override val patchSize: Int = interpreter.getInputTensor(0).shape()[1]
    private val input = ByteBuffer.allocateDirect(patchSize * patchSize * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(3 * 4).order(ByteOrder.nativeOrder())

    @Volatile private var closed = false

    @Synchronized
    override fun run(patch: ColorFrame): TipReading {
        if (closed) return TipReading(0.0, 0.0, 0.0)
        require(patch.width == patchSize && patch.height == patchSize)
        input.rewind()
        val floats = input.asFloatBuffer()
        val rgb = patch.rgb
        for (i in rgb.indices) floats.put((rgb[i].toInt() and 0xFF) / 255f)
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val out = FloatArray(3).also { output.asFloatBuffer().get(it) }
        return TipReading(out[0].toDouble(), out[1].toDouble(), out[2].toDouble())
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    companion object {
        const val ASSET = "tiprefine.tflite"

        /** The loaded model, or null when the file is not in the app or cannot be loaded. */
        fun loadOrNull(context: Context): TfliteTipNet? = runCatching {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
            TfliteTipNet(Interpreter(model, Interpreter.Options().setNumThreads(2)))
        }.getOrNull()
    }
}
