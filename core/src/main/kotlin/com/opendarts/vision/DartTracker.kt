package com.opendarts.vision

import com.opendarts.game.model.DartHit
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import kotlin.math.abs
import kotlin.math.hypot

data class TrackerConfig(
    /** Grey-level change that counts as "different" (after global brightness compensation). */
    val diffThreshold: Int = 28,
    /** Fraction of the region of interest that may change between frames before the scene counts as moving. */
    val motionFraction: Double = 0.0008,
    /** Consecutive calm frames required before the scene is evaluated. */
    val stableFrames: Int = 5,
    val minBlobArea: Int = 60,
    val maxBlobFraction: Double = 0.03,
    val minBlobLength: Double = 12.0,
    /** Region of interest: how far from the bull (in mm on the board plane) pixels are watched. */
    val roiRadiusMm: Double = 420.0,
    /** Darts further than this from the bull are not on the board and are ignored. */
    val ignoreBeyondMm: Double = 215.0,
    /** A change that covers this share of the ROI is a hand or a lighting change, never a dart. */
    val emptyBoardFraction: Double = 0.0015,
)

sealed interface TrackerEvent {
    data class DartLanded(val hit: DartHit, val board: Point, val pixel: Point) : TrackerEvent
    /** [remaining] darts are still in the board; 0 means the board is clear and the next turn can start. */
    data class DartsRemoved(val remaining: Int) : TrackerEvent

    /** The dart that was at [from] (board mm) has been moved by a hand and now sits at [board]: the same dart, not a new one. */
    data class DartMoved(val from: Point, val fromPixel: Point, val hit: DartHit, val board: Point, val pixel: Point) : TrackerEvent
}

enum class TrackerState { WAITING_FOR_BOARD, MOVING, SETTLING, READY }

/**
 * Turns a stream of camera frames into dart events. A fixed camera is assumed; [pixelToBoard] maps
 * image pixels to board millimetres (see [com.opendarts.geometry.BoardGeometry.CALIBRATION_POINTS]).
 *
 * The first calm frame after [markBoardEmpty] becomes the reference. Whenever the scene has been calm
 * for [TrackerConfig.stableFrames] frames it is compared with the previous calm scene: a new dart-sized
 * blob is a landed dart; a blob where a known dart was is a removed dart.
 */
class DartTracker(
    private val pixelToBoard: Homography,
    private val width: Int,
    private val height: Int,
    private val config: TrackerConfig = TrackerConfig(),
) {
    private val roi = BooleanArray(width * height)
    private val roiCount: Int
    private var previous: IntArray? = null
    private var background: IntArray? = null
    private var baseline: IntArray? = null
    private var calm = 0
    private var evaluated = false
    private var awaitingBoard = true
    private var waitingFrames = 0
    private var forceBaseline = false
    private val known = ArrayList<DartBlob>()

    var state: TrackerState = TrackerState.WAITING_FOR_BOARD
        private set

    val darts: Int get() = known.size

    private companion object { const val NOISE_MARGIN = 4.5 }

    init {
        require(width > 0 && height > 0) { "frame size must be positive" }
        var count = 0
        val inverseLimit = config.roiRadiusMm
        for (y in 0 until height) for (x in 0 until width) {
            val p = pixelToBoard.apply(x + 0.5, y + 0.5)
            if (p.x.isFinite() && p.y.isFinite() && hypot(p.x, p.y) <= inverseLimit) {
                roi[y * width + x] = true
                count++
            }
        }
        require(count > 0) { "calibration maps no pixel of the frame onto the board" }
        roiCount = count
    }

    /** The next calm frame becomes the empty-board reference; any darts currently tracked are forgotten. */
    fun markBoardEmpty() {
        awaitingBoard = true
        known.clear()
        state = TrackerState.WAITING_FOR_BOARD
        calm = 0
        evaluated = false
        waitingFrames = 0
        forceBaseline = false
    }

    /** Takes the very next frame as the empty-board reference, however busy the scene looks. */
    fun startNow() {
        markBoardEmpty()
        forceBaseline = true
    }

    fun process(frame: GrayFrame): List<TrackerEvent> {
        require(frame.width == width && frame.height == height) { "frame size changed" }
        val current = smooth(frame)
        val prev = previous
        previous = current
        if (prev == null) return emptyList()

        if (awaitingBoard && forceBaseline) {
            forceBaseline = false
            baseline = current
            background = current
            awaitingBoard = false
            evaluated = true
            calm = config.stableFrames
            state = TrackerState.READY
            return emptyList()
        }

        // While waiting for the first reference, tolerate more and more flicker so a slightly noisy scene still starts.
        val allowance = if (awaitingBoard) config.motionFraction * (1 shl minOf(waitingFrames / 20, 5)) else config.motionFraction
        threshold = adaptiveThreshold(current, prev)
        if (changed(current, prev, 0) > allowance * roiCount) {
            calm = 0
            evaluated = false
            if (awaitingBoard) waitingFrames++
            state = if (awaitingBoard) TrackerState.WAITING_FOR_BOARD else TrackerState.MOVING
            return emptyList()
        }
        calm++
        if (calm < config.stableFrames) {
            if (!awaitingBoard) state = TrackerState.SETTLING
            return emptyList()
        }
        if (evaluated) return emptyList()
        evaluated = true

        if (awaitingBoard) {
            baseline = current
            background = current
            awaitingBoard = false
            state = TrackerState.READY
            return emptyList()
        }
        return evaluate(current)
    }

    private fun evaluate(current: IntArray): List<TrackerEvent> {
        state = TrackerState.READY
        val bg = background ?: return emptyList()
        val offset = meanOffset(current, bg)
        val mask = BooleanArray(width * height)
        var changedPixels = 0
        for (i in current.indices) {
            if (roi[i] && abs(current[i] - bg[i] - offset) > threshold) {
                mask[i] = true
                changedPixels++
            }
        }

        val events = ArrayList<TrackerEvent>()
        val maxArea = (config.maxBlobFraction * roiCount).toInt()
        val blobs = BlobFinder.find(mask, width, height, config.minBlobArea, maxArea)
            .filter { it.length >= config.minBlobLength }

        if (blobs.isEmpty()) {
            val base = baseline
            if (known.isNotEmpty() && base != null && changed(current, base, meanOffset(current, base)) < config.emptyBoardFraction * roiCount) {
                known.clear()
                events += TrackerEvent.DartsRemoved(0)
            }
            if (changedPixels < config.minBlobArea) {
                background = current
                if (known.isEmpty()) baseline = current
            }
            return events
        }

        for (blob in blobs.sortedByDescending { it.area }) {
            val knownMatch = known.indexOfFirst { it.overlapsBox(blob) }
            if (knownMatch >= 0) {
                known.removeAt(knownMatch)
                events += TrackerEvent.DartsRemoved(known.size)
                continue
            }
            val board = pixelToBoard.apply(blob.tip)
            if (!board.x.isFinite() || !board.y.isFinite() || hypot(board.x, board.y) > config.ignoreBeyondMm) continue
            known += blob
            events += TrackerEvent.DartLanded(BoardGeometry.hitAt(board), board, blob.tip)
        }
        background = current
        return events
    }

    /** Grey-level change counted as real: the configured minimum, or several times the camera noise if that is larger. */
    private var threshold = config.diffThreshold

    private fun adaptiveThreshold(a: IntArray, b: IntArray): Int {
        val histogram = IntArray(256)
        var n = 0
        var i = 0
        while (i < a.size) {
            if (roi[i]) { histogram[minOf(255, abs(a[i] - b[i]))]++; n++ }
            i += 11
        }
        if (n == 0) return config.diffThreshold
        var acc = 0
        var median = 0
        while (median < 255) { acc += histogram[median]; if (acc * 2 >= n) break; median++ }
        val sigma = median / 0.6745
        return maxOf(config.diffThreshold, (NOISE_MARGIN * sigma).toInt())
    }

    /** 3x3 box blur: averages away sensor noise while keeping a dart barrel visible. */
    private fun smooth(frame: GrayFrame): IntArray {
        val src = frame.pixels
        val horizontal = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val l = if (x > 0) src[row + x - 1].toInt() and 0xFF else src[row + x].toInt() and 0xFF
                val r = if (x < width - 1) src[row + x + 1].toInt() and 0xFF else src[row + x].toInt() and 0xFF
                horizontal[row + x] = l + (src[row + x].toInt() and 0xFF) + r
            }
        }
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val up = (if (y > 0) y - 1 else y) * width
            val down = (if (y < height - 1) y + 1 else y) * width
            val row = y * width
            for (x in 0 until width) out[row + x] = (horizontal[up + x] + horizontal[row + x] + horizontal[down + x]) / 9
        }
        return out
    }

    private fun changed(a: IntArray, b: IntArray, offset: Int): Int {
        var n = 0
        for (i in a.indices) if (roi[i] && abs(a[i] - b[i] - offset) > threshold) n++
        return n
    }

    /** Median-ish brightness shift between two frames (auto-exposure compensation). */
    private fun meanOffset(a: IntArray, b: IntArray): Int {
        val histogram = IntArray(511)
        var n = 0
        var i = 0
        while (i < a.size) {
            if (roi[i]) { histogram[a[i] - b[i] + 255]++; n++ }
            i += 7
        }
        if (n == 0) return 0
        var acc = 0
        for (bin in histogram.indices) {
            acc += histogram[bin]
            if (acc * 2 >= n) return bin - 255
        }
        return 0
    }
}