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
    /** While a hand is moving in front of the board a new dart has to be seen this many looks in a row, because a hand can fool the network. */
    val confirmLooksWithHand: Int = 3,
    /** A known dart that is not seen for this many consecutive looks, after a hand has been in front of the board, has been pulled out. */
    val removeAfterMisses: Int = 4,
    /** Nobody pulls a dart without a hand reaching in. A dart that vanishes with no hand about is the network losing it, so it is only given up after this many looks. */
    val removeAfterMissesNoHand: Int = 16,
    /** A dart that is no longer seen where it was, with no hand about, and a detection this close to it (mm): the tip is read a little differently, it is still the same dart. */
    val driftMm: Double = 16.0,
    /** Detections closer than this (mm on the board) are the same dart. Two real tips cannot be closer than a barrel width (about 7 mm), so a hidden dart that shows up beside another one is a new dart. */
    val sameDartMm: Double = 8.0,
    /** Detections further than this from the bull are not darts in the board (the bezel, the logo, a hand), so they never count as a miss. */
    val ignoreBeyondMm: Double = 176.0,
    val maxDartsOnBoard: Int = 3,
    /** Corner positions are blended with the previous ones unless they jump by more than this share of the frame diagonal. */
    val smoothing: Double = 0.4,
    val jumpFraction: Double = 0.04,
    val jumpLooksToReset: Int = 4,
    /** When the board cannot be found (a hand in front of it, a blurred frame) the last fit is kept for this many looks. */
    val holdLooks: Int = 16,
    /** After the camera moved, darts cannot be taken as pulled out for this many looks (they are matched again at their board positions first). */
    val graceLooks: Int = 8,
    /** While the picture is catching up with a small camera shift, darts are placed with the exact corners of the look when they differ by more than this share of the frame diagonal. */
    val trustRawFraction: Double = 0.008,
    /** A look where more than this share of the picture changed since the last one has a hand or arm in it (a landing dart changes far less). */
    val handChangeShare: Double = 0.05,
    /** Darts are only taken as pulled out after the scene has been still for this many looks, so a hand reaching in cannot pass the turn. */
    val calmLooksToRemove: Int = 3,
    /** A new dart that has visibly changed the picture around its tip (compared with the calm picture from before) counts after this many looks. */
    val confirmLooksWithChange: Int = 1,
    /** Darts the game already has but a new tracker never saw land are taken from the board within this many calm looks, without being scored again. */
    val adoptLooks: Int = 15,
    /** After the camera moved, a known dart that is not back at its board position is the same dart if one is seen this close (mm) to where it was. */
    val reanchorMm: Double = 60.0,
    /** A hand that rests on the board no longer moves, but it still covers the darts; they are kept for at most this many looks of that. */
    val maxCoveredLooks: Int = 100,
)

/** A dart the tracker knew about, kept so a new tracker (the screen was rebuilt, the phone turned) carries on where the old one stopped. */
data class RememberedDart(val board: Point, val ignored: Boolean)

/**
 * Turns network detections into dart events. Every call to [process] is one "look" at the board: darts are
 * confirmed after being seen twice in a row and removed after being missing several looks in a row, which
 * absorbs hands, flickering detections and single bad frames.
 */
class MlDartTracker(
    private val detect: (ColorFrame, List<Point>?) -> FrameDetections,
    private val config: MlTrackerConfig = MlTrackerConfig(),
) {
    /** An [ignored] mark is still matched, so it is not scored again, but it is not a dart: it takes no place of the three and is never reported. */
    private class Known(var board: Point, var pixel: Point, var seenAt: Long, var misses: Int = 0, var ignored: Boolean = false, var rematched: Boolean = true)
    /** [born] is the look where the dart was first seen, so darts confirmed together are scored in the order they landed. */
    private class Candidate(var board: Point, var pixel: Point, val born: Long, var seen: Int = 1)

    private val known = ArrayList<Known>()
    private var candidates = ArrayList<Candidate>()
    private var corners: List<Point>? = null
    private var jumps = 0
    private var lost = 0
    private var settling = false
    private var graceLooks = 0
    private val scene = SceneMotion(config.handChangeShare)
    private var calmLooks = 0
    private var looks = 0L
    private var lastHandLook = -1L
    private var adoptNext = 0
    private var adoptLooksLeft = 0
    private var movedLooks = 0
    private val reference = ChangeReference()
    private var coveredLooks = 0

    var state = TrackerState.WAITING_FOR_BOARD
        private set
    var boardToPixel: Homography? = null
        private set
    var view: BoardView? = null
        private set
    val darts: Int get() = known.count { !it.ignored }

    /** True from the moment the board jumped in the picture (the phone was knocked) until the new position has been checked. */
    val cameraMoved: Boolean get() = settling || movedLooks > 0

    /** True while the last look had a hand or arm moving in front of the board. */
    var handInWay = false
        private set

    /** The four calibration points (cal1..cal4) as last found, in frame pixels. */
    val cornerPixels: List<Point>? get() = corners

    /** The darts counted on the board, in frame pixels. */
    val dartPixels: List<Point> get() = known.filter { !it.ignored }.map { boardToPixel?.apply(it.board) ?: it.pixel }

    /** Where the darts known now are, for [remember] on a later tracker. */
    fun remembered(): List<RememberedDart> = known.map { RememberedDart(it.board, it.ignored) }

    /** Carries on where an earlier tracker stopped: these darts are on the board and already scored, so they are matched, not scored again. */
    fun remember(darts: List<RememberedDart>) {
        known.clear()
        candidates.clear()
        darts.forEach { known += Known(it.board, Point(0.0, 0.0), looks, ignored = it.ignored) }
        graceLooks = config.graceLooks
    }

    /**
     * The game has [count] darts of this turn that this tracker never saw land (the game was reopened, or switched over from the
     * keypad). The next [count] darts it confirms in the first calm looks are those darts: counted as on the board, not scored.
     */
    fun adopt(count: Int) {
        adoptNext = count
        adoptLooksLeft = if (count > 0) config.adoptLooks else 0
    }

    /** Forgets the darts and takes the board as empty. */
    fun markBoardEmpty() {
        adoptNext = 0
        known.clear()
        candidates.clear()
        reference.clear()
    }

    /**
     * The player says the mark at [board] is no dart (a second mark on the barrel, a bright spot): it stays known, so it is not
     * scored again, but it no longer counts towards the three darts on the board. Returns false if no mark was known there.
     */
    fun ignoreAt(board: Point): Boolean {
        val mark = known.filter { !it.ignored }.minByOrNull { dist(it.board, board) }?.takeIf { dist(it.board, board) <= config.sameDartMm * 2 } ?: return false
        mark.ignored = true
        return true
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

    /** One look at the board. [nowMillis] is the time of the look, used to tell a moving hand from a settled scene. */
    fun process(frame: ColorFrame, nowMillis: Long = looks * 450): List<TrackerEvent> {
        looks++
        handInWay = scene.moved(frame, nowMillis) || covered(frame)
        if (handInWay) lastHandLook = looks
        calmLooks = if (handInWay) 0 else calmLooks + 1
        val found = detect(frame, corners)
        val cal = completeCorners(found.corners, boardToPixel) ?: return lostBoard()
        val smoothed = fit(cal, frame) ?: return lostBoard()
        lost = 0
        if (settling) return emptyList()
        if (graceLooks > 0) graceLooks--
        var closing = false
        if (movedLooks > 0) { movedLooks--; closing = movedLooks == 0 }
        val holding = movedLooks > 0
        if (adoptLooksLeft > 0 && !handInWay && --adoptLooksLeft == 0) adoptNext = 0
        val diagonal = hypot(frame.width.toDouble(), frame.height.toDouble())
        val off = cal.indices.maxOf { dist(cal[it], corners!![it]) } / diagonal
        val fit = if (off > config.trustRawFraction) runCatching { Homography.fromFourPoints(BoardGeometry.MODEL_CALIBRATION_POINTS, cal) }.getOrNull() ?: smoothed else smoothed
        state = TrackerState.READY
        val toBoard = fit.inverse()
        boardToPixel = fit
        view = PlacementAdvisor.assess(fit, frame.width, frame.height)

        val seenNow = DartAxis.tips(found.darts, fit, frame.width, frame.height).mapNotNull { pixel ->
            val board = toBoard.apply(pixel)
            if (hypot(board.x, board.y) <= config.ignoreBeyondMm) board to pixel else null
        }.toMutableList()

        val events = ArrayList<TrackerEvent>()
        val unmatched = seenNow.toMutableList()

        var removed = 0
        val unseen = ArrayList<Known>()
        for (dart in known.toList()) {
            val near = unmatched.minByOrNull { dist(it.first, dart.board) }
            if (near != null && dist(near.first, dart.board) <= config.sameDartMm) {
                unmatched.remove(near)
                dart.misses = 0
                dart.seenAt = looks
                dart.rematched = true
            } else {
                if (!handInWay) dart.misses++
                unseen += dart
            }
        }
        // No hand has been near a dart that is suddenly read somewhere else a little further off: the same dart, not a new one.
        if (!handInWay) for (dart in unseen) {
            if (lastHandLook > dart.seenAt) continue
            val near = unmatched.minByOrNull { dist(it.first, dart.board) } ?: continue
            if (dist(near.first, dart.board) > config.driftMm) continue
            unmatched.remove(near)
            dart.board = near.first
            dart.pixel = near.second
            dart.misses = 0
            dart.seenAt = looks
            dart.rematched = true
        }

        val needed = if (handInWay) config.confirmLooksWithHand else config.confirmLooks
        val steady = !handInWay && !settling && graceLooks == 0 && off <= config.trustRawFraction
        val change = if (steady && unmatched.isNotEmpty()) reference.compare(frame) else null
        fun fresh(pixel: Point) = change != null && change.around(pixel)
        val next = ArrayList<Candidate>()
        val confirmed = ArrayList<Candidate>()
        for ((board, pixel) in unmatched) {
            val candidate = candidates.firstOrNull { dist(it.board, board) <= config.sameDartMm }
            if (candidate != null) {
                candidates.remove(candidate)
                candidate.board = board
                candidate.pixel = pixel
                candidate.seen++
                if (!holding && (candidate.seen >= needed || (candidate.seen >= config.confirmLooksWithChange && fresh(pixel)))) confirmed += candidate else next += candidate
            } else {
                val dart = Candidate(board, pixel, looks)
                if (config.confirmLooksWithChange <= 1 && fresh(pixel)) confirmed += dart else next += dart
            }
        }
        // A hand can hide a dart for a look; do not forget it was seen.
        if (handInWay) next += candidates
        candidates = next
        val arrived = confirmed.sortedBy { it.born }.toMutableList()
        if (closing) reanchor(arrived)
        if (calmLooks >= config.calmLooksToRemove && graceLooks == 0) {
            known.removeAll {
                val limit = if (lastHandLook > it.seenAt) config.removeAfterMisses else config.removeAfterMissesNoHand
                (it.misses >= limit).also { gone -> if (gone && !it.ignored) removed++ }
            }
        }
        for (candidate in arrived) {
            if (darts >= config.maxDartsOnBoard) {
                // Three darts are on the board, so a new one cannot be: a hand moved one of them.
                val moved = unseen.filter { it in known && !it.ignored && lastHandLook > it.seenAt }.minByOrNull { dist(it.board, candidate.board) }
                if (moved != null && dist(moved.board, candidate.board) <= config.reanchorMm) {
                    unseen.remove(moved)
                    events += TrackerEvent.DartMoved(moved.board, boardToPixel?.apply(moved.board) ?: moved.pixel, BoardGeometry.hitAt(candidate.board), candidate.board, candidate.pixel)
                    moved.board = candidate.board
                    moved.pixel = candidate.pixel
                    moved.misses = 0
                    moved.seenAt = looks
                    moved.rematched = true
                }
                continue
            }
            known += Known(candidate.board, candidate.pixel, looks)
            if (adoptNext > 0) { adoptNext--; continue }
            events += TrackerEvent.DartLanded(BoardGeometry.hitAt(candidate.board), candidate.board, candidate.pixel)
        }

        if (removed > 0) events += TrackerEvent.DartsRemoved(darts)
        if (steady && candidates.isEmpty() && calmLooks >= 2) reference.capture(frame)
        return events
    }

    /**
     * The camera moved and the window for checking the new position is over. Darts that are not back at their board position,
     * but have a newly seen dart close to where they were, are the same darts seen through a slightly different fit: they move
     * there and the new sightings are not scored again. Darts with nothing near them stay and are judged by the usual rules.
     */
    private fun reanchor(arrived: MutableList<Candidate>) {
        val lost = known.filter { !it.ignored && !it.rematched }.toMutableList()
        while (lost.isNotEmpty() && arrived.isNotEmpty()) {
            val pair = lost.flatMap { dart -> arrived.map { Triple(dart, it, dist(dart.board, it.board)) } }.minByOrNull { it.third }!!
            if (pair.third > config.reanchorMm) break
            val (dart, candidate) = pair
            lost.remove(dart)
            arrived.remove(candidate)
            dart.board = candidate.board
            dart.pixel = candidate.pixel
            dart.seenAt = looks
            dart.rematched = true
        }
        lost.forEach { it.misses = 0 }
    }

    /**
     * A hand that rests on the board is not "moving" any more, but it hides the darts under it, and the picture still differs a lot
     * from the calm one from before. Darts that are not seen then have not been pulled out.
     */
    private fun covered(frame: ColorFrame): Boolean {
        if (known.none { it.misses > 0 }) { coveredLooks = 0; return false }
        val share = reference.compare(frame)?.share ?: 0.0
        coveredLooks = if (share > config.handChangeShare) coveredLooks + 1 else 0
        return coveredLooks in 1..config.maxCoveredLooks
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
                settling = true
                reference.clear()
                if (++jumps >= config.jumpLooksToReset) {
                    jumps = 0
                    settling = false
                    // The darts are kept: they are stored at board positions, which a moved camera does not change.
                    candidates.clear()
                    known.forEach { it.misses = 0; it.rematched = false }
                    graceLooks = config.graceLooks
                    movedLooks = config.graceLooks
                    cal
                } else previous
            } else {
                jumps = 0
                settling = false
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
