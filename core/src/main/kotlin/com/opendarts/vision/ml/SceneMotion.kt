package com.opendarts.vision.ml

import kotlin.math.abs

/**
 * Tells a hand or arm in front of the board from a settled scene by comparing a coarse grid of block averages with the
 * previous look. A dart landing changes a few blocks; a hand changes a large part of the picture.
 *
 * Changes that affect the whole picture (the phone being held, exposure or screen flicker) are not a hand: the typical
 * brightness shift is removed, and if the picture keeps "moving" for more than [maxLooks] looks in a row it is treated as
 * noise until it settles, so the guard can never block scoring for good.
 *
 * Each look is compared with the one from about [gapMillis] ago, not just the previous one, so the guard works the same
 * whether the network is read every 150 ms or every 500 ms.
 */
class SceneMotion(
    private val changeShare: Double,
    private val maxLooks: Int = 12,
    private val block: Int = 8,
    private val threshold: Int = 32,
    private val gapMillis: Long = 350,
) {
    private class Snapshot(val at: Long, val blocks: IntArray)

    private val history = ArrayDeque<Snapshot>()
    private var ticks = 0L
    private var columns = 0
    private var rows = 0
    private var streak = 0

    /** True if more than [changeShare] of the picture differs from [gapMillis] ago (and it is not just noise). [nowMillis] is the time of the look. */
    fun moved(frame: ColorFrame, nowMillis: Long = ticks++ * 450): Boolean {
        val c = frame.width / block
        val r = frame.height / block
        val now = IntArray(c * r)
        val sample = block / 2
        for (by in 0 until r) for (bx in 0 until c) {
            var sum = 0
            for (dy in 0 until block step 2) for (dx in 0 until block step 2) {
                val i = ((by * block + dy) * frame.width + bx * block + dx) * 3
                sum += (frame.rgb[i].toInt() and 0xFF) + (frame.rgb[i + 1].toInt() and 0xFF) + (frame.rgb[i + 2].toInt() and 0xFF)
            }
            now[by * c + bx] = sum / (3 * sample * sample)
        }
        if (c != columns || r != rows) history.clear()
        columns = c
        rows = r
        val before = history.lastOrNull { nowMillis - it.at >= gapMillis }?.blocks ?: history.firstOrNull()?.blocks
        while (history.size > 1 && nowMillis - history[1].at >= gapMillis) history.removeFirst()
        history.addLast(Snapshot(nowMillis, now))
        if (before == null || now.isEmpty()) return false

        val shift = median(IntArray(now.size) { now[it] - before[it] })
        var changed = 0
        for (i in now.indices) if (abs(now[i] - before[i] - shift) > threshold) changed++
        val raw = changed.toDouble() / now.size > changeShare
        streak = if (raw) streak + 1 else 0
        return raw && streak <= maxLooks
    }

    private fun median(values: IntArray): Int {
        values.sort()
        return values[values.size / 2]
    }
}