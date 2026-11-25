package com.opendarts.ui.common

import com.opendarts.session.SoundEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** The effects for bust and game shot, drawn as samples so the app needs no sound files. */
object SoundFx {
    const val RATE = 22050

    private val bust: ShortArray by lazy { buzzer() }
    private val gameShot: ShortArray by lazy { fanfare() }

    fun pcm(effect: SoundEffect): ShortArray = when (effect) {
        SoundEffect.BUST -> bust
        SoundEffect.GAME_SHOT -> gameShot
    }

    /** A low two-note "wah wah" that falls away. */
    private fun buzzer(): ShortArray {
        val out = FloatArray((0.85 * RATE).toInt())
        note(out, 0.0, 233.0, 0.38, 1.0, square = true)
        note(out, 0.34, 165.0, 0.5, 1.0, square = true)
        return finish(out)
    }

    /** A burst like a party popper, then a rising run of notes that rings out. */
    private fun fanfare(): ShortArray {
        val out = FloatArray((1.6 * RATE).toInt())
        blast(out, 0.35, 2.2f)
        val run = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        run.forEachIndexed { i, hz -> note(out, 0.12 + i * 0.11, hz, if (i == run.lastIndex) 1.1 else 0.3, 0.7, square = false) }
        return finish(out)
    }

    private fun note(out: FloatArray, start: Double, hz: Double, length: Double, gain: Double, square: Boolean) {
        val from = (start * RATE).toInt()
        val n = (length * RATE).toInt()
        for (i in 0 until n) {
            val at = from + i
            if (at >= out.size) break
            val phase = 2 * PI * hz * i / RATE
            val wave = if (square) 0.6 * sign(sin(phase)) + 0.4 * sin(phase) else sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase)
            val envelope = min(1.0, i / (0.006 * RATE)) * exp(-3.2 * i / n)
            out[at] += (wave * envelope * gain).toFloat()
        }
    }

    private fun blast(out: FloatArray, length: Double, gain: Float) {
        val random = Random(7)
        val n = (length * RATE).toInt()
        var smooth = 0f
        for (i in 0 until n) {
            smooth += 0.25f * ((random.nextFloat() * 2 - 1) - smooth)
            out[i] += smooth * gain * exp(-7.0 * i / n).toFloat()
        }
    }

    private fun finish(out: FloatArray): ShortArray {
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        return ShortArray(out.size) { (out[it] / peak * 0.85f * Short.MAX_VALUE).toInt().toShort() }
    }
}
