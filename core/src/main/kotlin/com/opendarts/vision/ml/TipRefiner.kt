package com.opendarts.vision.ml

import com.opendarts.geometry.Point

/** What the tip network says about one patch: where the tip is from the patch centre (patch pixels) and how likely a tip is there. */
class TipReading(val dx: Double, val dy: Double, val tip: Double)

/** A small network that is shown a patch of the flattened board and says where the dart tip in it is. */
interface TipNet {
    val patchSize: Int
    fun run(patch: ColorFrame): TipReading
}

/**
 * Second stage: moves each detected dart mark onto the real tip. The detector often lands a few millimetres up the
 * barrel; the patch network, trained on tips only, looks at a close-up and says how far off the mark is. Marks it
 * does not believe in are dropped, and marks that end up on the same tip are merged.
 *
 * Works on the flattened board, whose size the network was trained at ([TRAINED_SIZE] pixels square).
 */
class TipRefiner(
    private val net: TipNet,
    private val minTip: Double = 0.4,
    private val rounds: Int = 2,
    private val mergeWithin: Double = 5.0,
) {
    /** [marks] in flat-board pixels, best first. Returns the corrected marks, still best first. */
    fun refine(flat: ColorFrame, marks: List<Point>): List<Point> {
        val scale = flat.width / TRAINED_SIZE
        val side = net.patchSize * scale
        val kept = ArrayList<Point>()
        for (mark in marks) {
            var at = mark
            var believed = false
            for (round in 0 until rounds) {
                val reading = net.run(flat.window(at.x, at.y, side, net.patchSize))
                if (reading.tip < minTip) break
                believed = true
                val moveX = reading.dx * scale
                val moveY = reading.dy * scale
                at = Point(at.x + moveX, at.y + moveY)
                if (Math.hypot(moveX, moveY) < SETTLED * scale) break
            }
            if (!believed) continue
            if (kept.none { Math.hypot(it.x - at.x, it.y - at.y) < mergeWithin * scale }) kept += at
        }
        return kept
    }

    companion object {
        const val TRAINED_SIZE = 640.0
        private const val SETTLED = 1.5
    }
}
