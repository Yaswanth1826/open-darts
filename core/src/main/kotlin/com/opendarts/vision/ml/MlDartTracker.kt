package com.opendarts.vision.ml

import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState
import kotlin.math.hypot

data class MlTrackerConfig(
    /** A new dart must be seen in this many consecutive looks before it counts. */
    val confirmLooks: Int = 2,
    /** A known dart that is not seen for this many consecutive looks has been pulled out. */
    val removeAfterMisses: Int = 3,
    /** Detections closer than this (mm on the board) are the same dart. */
    val sameDartMm: Double = 12.0,
    /** Detections further than this from the bull are not darts in the board (the bezel, the logo, a hand), so they never count as a miss. */
    val ignoreBeyondMm: Double = 176.0,
    val maxDartsOnBoard: Int = 3,
    /** Corner positions are blended with the previous ones unless they jump by more than this share of the frame diagonal. */
    val smoothing: Double = 0.4,
    val jumpFraction: Double = 0.04,
    val jumpLooksToReset: Int = 3,
    /** When the board cannot be found (a hand in front of it, a blurred frame) the last fit is kept for this many looks. */
    val holdLooks: Int = 10,
    /** A look where more than this share of the picture changed since the last one has a hand or arm in it (a landing dart changes far less). */
    val handChangeShare: Double = 0.05,
    /** Darts are only taken as pulled out after the scene has been still for this many looks, so a hand reaching in cannot pass the turn. */
    val calmLooksToRemove: Int = 2,
)

/**
 * Turns network detections into dart events. Every call to [process] is one "look" at the board: darts are
 * confirmed after being seen twice in a row and removed after being missing several looks in a row, which
 * absorbs hands, flickering detections and single bad frames.
 */
class MlDartTracker(
    private val detect: (ColorFrame, List<Point>?) -> FrameDetections,
    private val config: MlTrackerConfig = MlTrackerConfig(),
) {
    private class Known(val board: Point, val pixel: Point, var misses: Int = 0)
    private class Candidate(var board: Point, var pixel: Point, var seen: Int = 1)

    private val known = ArrayList<Known>()
    private var candidates = ArrayList<Candidate>()
    private var corners: List<Point>? = null
    private var jumps = 0
    private var lost = 0
    private val scene = SceneMotion(config.handChangeShare)
    private var calmLooks = 0

    var state = TrackerState.WAITING_FOR_BOARD
        private set
    var boardToPixel: Homography? = null
        private set
    var view: BoardView? = null
        private set
    val darts: Int get() = known.size

    /** True while the last look had a hand or arm moving in front of the board. */
    var handInWay = false
        private set

    /** The four calibration points (cal1..cal4) as last found, in frame pixels. */
    val cornerPixels: List<Point>? get() = corners

    /** The darts counted on the board, in frame pixels. */
    val dartPixels: List<Point> get() = known.map { it.pixel }

    /** Forgets the darts and takes the board as empty. */
    fun markBoardEmpty() {
        known.clear()
        candidates.clear()
    }

    /**
     * The player says another dart is hiding behind the one at [board]: forget that dart, so whatever is still seen
     * at the spot is scored as a new dart once it has been seen twice. Returns false if no dart was known there.
     */
    fun releaseAt(board: Point): Boolean {
        val dart = known.minByOrNull { dist(it.board, board) }?.takeIf { dist(it.board, board) <= config.sameDartMm * 2 } ?: return false
        known.remove(dart)
        candidates.clear()
        return true
    }

    fun process(frame: ColorFrame): List<TrackerEvent> {
        handInWay = scene.moved(frame)
        calmLooks = if (handInWay) 0 else calmLooks + 1
        val found = detect(frame, corners)
        val cal = completeCorners(found.corners, boardToPixel) ?: return lostBoard()
        val fit = fit(cal, frame) ?: return lostBoard()
        lost = 0
        state = TrackerState.READY
        val toBoard = fit.inverse()
        boardToPixel = fit
        view = PlacementAdvisor.assess(fit, frame.width, frame.height)

        val seenNow = found.darts.mapNotNull { pixel ->
            val board = toBoard.apply(pixel)
            if (hypot(board.x, board.y) <= config.ignoreBeyondMm) board to pixel else null
        }.toMutableList()

        val events = ArrayList<TrackerEvent>()
        val unmatched = seenNow.toMutableList()

        var removed = 0
        for (dart in known.toList()) {
            val near = unmatched.minByOrNull { dist(it.first, dart.board) }
            if (near != null && dist(near.first, dart.board) <= config.sameDartMm) {
                unmatched.remove(near)
                dart.misses = 0
            } else {
                if (!handInWay) dart.misses++
            }
        }
        if (calmLooks >= config.calmLooksToRemove) {
            known.removeAll { (it.misses >= config.removeAfterMisses).also { gone -> if (gone) removed++ } }
        }

        val next = ArrayList<Candidate>()
        if (handInWay) {
            next += candidates
        } else for ((board, pixel) in unmatched) {
            val candidate = candidates.firstOrNull { dist(it.board, board) <= config.sameDartMm }
            if (candidate != null) {
                candidates.remove(candidate)
                candidate.board = board
                candidate.pixel = pixel
                candidate.seen++
                if (candidate.seen >= config.confirmLooks && known.size < config.maxDartsOnBoard) {
                    known += Known(board, pixel)
                    events += TrackerEvent.DartLanded(BoardGeometry.hitAt(board), board, pixel)
                } else if (candidate.seen < config.confirmLooks) {
                    next += candidate
                }
            } else {
                next += Candidate(board, pixel)
            }
        }
        candidates = next

        if (removed > 0) events += TrackerEvent.DartsRemoved(known.size)
        return events
    }

    /** A look without a usable board: keep the last fit for a while so a passing hand does not lose the game. */
    private fun lostBoard(): List<TrackerEvent> {
        if (boardToPixel == null || ++lost > config.holdLooks) state = TrackerState.WAITING_FOR_BOARD
        return emptyList()
    }

    /** Returns the board-to-pixel fit for the corners, smoothed against previous looks, or null if they make no sense. */
    private fun fit(cal: List<Point>, frame: ColorFrame): Homography? {
        val diagonal = hypot(frame.width.toDouble(), frame.height.toDouble())
        val previous = corners
        val used = if (previous == null) {
            cal
        } else {
            val jump = cal.indices.maxOf { dist(cal[it], previous[it]) } / diagonal
            if (jump > config.jumpFraction) {
                if (++jumps >= config.jumpLooksToReset) {
                    jumps = 0
                    markBoardEmpty()
                    cal
                } else previous
            } else {
                jumps = 0
                cal.indices.map {
                    Point(previous[it].x + (cal[it].x - previous[it].x) * config.smoothing, previous[it].y + (cal[it].y - previous[it].y) * config.smoothing)
                }
            }
        }
        if (!isPlausible(used)) return null
        val homography = runCatching { Homography.fromFourPoints(BoardGeometry.MODEL_CALIBRATION_POINTS, used) }.getOrNull() ?: return null
        corners = used
        return homography
    }

    /** The four corners, taken around the board, must form a convex quadrilateral that is not wildly squashed. */
    private fun isPlausible(c: List<Point>): Boolean {
        // cal_1, cal_4, cal_2, cal_3 are in order around the board.
        val ring = listOf(c[0], c[3], c[1], c[2])
        val signs = ring.indices.map {
            val a = ring[it]; val b = ring[(it + 1) % 4]; val d = ring[(it + 2) % 4]
            (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
        }
        if (!(signs.all { it > 0 } || signs.all { it < 0 })) return false
        val diagonalA = dist(c[0], c[1])
        val diagonalB = dist(c[2], c[3])
        return diagonalA > 1.0 && diagonalB > 1.0 && diagonalA / diagonalB in 0.25..4.0
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
