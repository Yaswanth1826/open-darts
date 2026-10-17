package com.opendarts.vision.ml

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloDecoderTest {
    private val attributes = 4 + DartClass.COUNT

    private fun channelsFirst(candidates: Int, rows: List<FloatArray>): FloatArray {
        val out = FloatArray(attributes * candidates)
        rows.forEachIndexed { i, row -> for (a in 0 until attributes) out[a * candidates + i] = row[a] }
        return out
    }

    private fun row(cx: Float, cy: Float, cls: Int, score: Float) =
        FloatArray(attributes).also { it[0] = cx; it[1] = cy; it[2] = 0.025f; it[3] = 0.025f; it[4 + cls] = score }

    @Test
    fun readsChannelsFirstOutputAndKeepsConfidentDetections() {
        val rows = listOf(row(0.5f, 0.4f, DartClass.DART, 0.9f), row(0.1f, 0.1f, 2, 0.1f), row(0.2f, 0.8f, 3, 0.7f))
        val found = YoloDecoder.decode(channelsFirst(rows.size, rows), attributes, rows.size, 640)
        assertEquals(2, found.size)
        assertTrue(found.any { it.cls == DartClass.DART && Math.abs(it.x - 0.5) < 1e-6 && Math.abs(it.y - 0.4) < 1e-6 })
        assertTrue(found.any { it.cls == 3 })
    }

    @Test
    fun readsChannelsLastOutput() {
        val rows = listOf(row(0.5f, 0.4f, DartClass.DART, 0.9f), row(0.7f, 0.3f, 1, 0.8f))
        val flat = FloatArray(rows.size * attributes)
        rows.forEachIndexed { i, r -> r.copyInto(flat, i * attributes) }
        assertEquals(2, YoloDecoder.decode(flat, rows.size, attributes, 640).size)
    }

    @Test
    fun convertsPixelBoxesToShares() {
        val rows = listOf(row(320f, 160f, DartClass.DART, 0.9f))
        val found = YoloDecoder.decode(channelsFirst(1, rows), attributes, 1, 640).single()
        assertEquals(0.5, found.x, 1e-6)
        assertEquals(0.25, found.y, 1e-6)
    }

    @Test
    fun mergesDuplicateDetectionsOfTheSameDart() {
        val rows = listOf(row(0.5f, 0.5f, DartClass.DART, 0.9f), row(0.502f, 0.501f, DartClass.DART, 0.6f), row(0.8f, 0.5f, DartClass.DART, 0.7f))
        val found = YoloDecoder.decode(channelsFirst(rows.size, rows), attributes, rows.size, 640)
        assertEquals(2, found.size)
        assertEquals(0.9f, found.maxOf { it.score })
    }
}

/** A pinhole camera looking at the board from the side (the board turned by [yawDegrees] about its vertical axis). */
private fun obliqueBoard(yawDegrees: Double, focal: Double = 900.0, distance: Double = 700.0, cx: Double = 320.0, cy: Double = 240.0): Homography {
    val yaw = Math.toRadians(yawDegrees)
    fun project(p: Point): Point {
        val depth = distance + p.x * Math.sin(yaw)
        return Point(cx + focal * p.x * Math.cos(yaw) / depth, cy - focal * p.y / depth)
    }
    val mm = listOf(Point(-100.0, -100.0), Point(100.0, -100.0), Point(100.0, 100.0), Point(-100.0, 100.0))
    return Homography.fromFourPoints(mm, mm.map(::project))
}

class TwoPassDetectorTest {
    private val size = 320
    private var runs = 0

    /** Fake network: reports every coloured dot it can see; the blue channel says which class it is. */
    private val net = object : DartNet {
        override val inputSize = size
        override fun run(image: ColorFrame): List<Detection> {
            runs++
            val out = ArrayList<Detection>()
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val o = (y * image.width + x) * 3
                val r = image.rgb[o].toInt() and 0xFF
                val g = image.rgb[o + 1].toInt() and 0xFF
                val b = image.rgb[o + 2].toInt() and 0xFF
                if (r > 240 && g < 15 && b >= 8 && b <= 60) out += Detection(Math.round(b / 10.0).toInt() - 1, 0.9f, (x + 0.5) / image.width, (y + 0.5) / image.height)
            }
            return cluster(out)
        }

        private fun cluster(points: List<Detection>) = points.groupBy { it.cls }.flatMap { (_, group) ->
            val kept = ArrayList<Detection>()
            for (d in group) if (kept.none { Math.hypot(it.x - d.x, it.y - d.y) < 0.05 }) kept += d
            kept.map { k ->
                val near = group.filter { Math.hypot(it.x - k.x, it.y - k.y) < 0.05 }
                Detection(k.cls, k.score, near.map { it.x }.average(), near.map { it.y }.average())
            }
        }
    }

    private fun frameWithDots(width: Int, height: Int, dots: List<Pair<Int, Point>>): ColorFrame {
        val data = ByteArray(width * height * 3) { 40 }
        for ((cls, p) in dots) {
            for (dy in -10..10) for (dx in -10..10) {
                if (dx * dx + dy * dy > 100) continue
                val x = Math.round(p.x).toInt() + dx; val y = Math.round(p.y).toInt() + dy
                if (x !in 0 until width || y !in 0 until height) continue
                val o = (y * width + x) * 3
                data[o] = 255.toByte(); data[o + 1] = 0; data[o + 2] = ((cls + 1) * 10).toByte()
            }
        }
        return ColorFrame(width, height, data)
    }

    private fun near(a: Point, b: Point, tolerance: Double = 2.5) = Math.hypot(a.x - b.x, a.y - b.y) < tolerance

    private val board = obliqueBoard(55.0)
    private val corners = BoardGeometry.MODEL_CALIBRATION_POINTS.map(board::apply)
    private val dartAt = board.apply(BoardGeometry.centreOf(DartHit(20, Multiplier.TRIPLE)))

    private fun sideFrame(withCorners: List<Int> = listOf(0, 1, 2, 3)) =
        frameWithDots(640, 480, withCorners.map { (it + 1) to corners[it] } + (DartClass.DART to dartAt))

    @Test
    fun findsASideOnBoardByFlatteningItAndMapsBackToFramePixels() {
        val result = TwoPassDetector(net).detect(sideFrame())
        val cal = result.calibration!!
        corners.forEachIndexed { i, p -> assertTrue("corner ${i + 1}: ${cal[i]} vs $p", near(cal[i], p)) }
        assertEquals(1, result.darts.size)
        assertTrue("${result.darts[0]} vs $dartAt", near(result.darts[0], dartAt))
        assertEquals(2, runs)
    }

    @Test
    fun skipsTheFirstPassWhenTheBoardIsAlreadyKnown() {
        val hint = corners.map { Point(it.x + 6, it.y - 5) }
        val result = TwoPassDetector(net).detect(sideFrame(), hint)
        assertEquals(1, runs)
        assertTrue(near(result.calibration!![2], corners[2]))
        assertTrue(near(result.darts.single(), dartAt))
    }

    @Test
    fun fallsBackToTheWholeFrameWhenTheHintIsWrong() {
        val hint = corners.map { Point(it.x * 0.5, it.y * 0.5) }
        val result = TwoPassDetector(net).detect(sideFrame(), hint)
        assertEquals(3, runs)
        assertTrue(result.calibration != null)
    }

    @Test
    fun stillFindsTheDartWhenOneCornerIsHidden() {
        val result = TwoPassDetector(net).detect(sideFrame(listOf(0, 1, 3)))
        assertEquals(3, result.found)
        assertNull(result.corners[2])
        assertTrue(near(result.darts.single(), dartAt))
    }

    @Test
    fun reportsNoBoardWhenCalibrationPointsAreMissing() {
        val frame = frameWithDots(640, 480, listOf(1 to Point(100.0, 100.0), 2 to Point(200.0, 200.0), DartClass.DART to Point(150.0, 150.0)))
        assertNull(TwoPassDetector(net).detect(frame).calibration)
    }
}

class PlacementTest {
    private fun assess(h: Homography, w: Int = 640, hgt: Int = 480) = PlacementAdvisor.assess(h, w, hgt)

    @Test
    fun measuresTheViewingAngle() {
        for (yaw in listOf(0.0, 30.0, 55.0, 65.0)) {
            val view = assess(obliqueBoard(yaw))
            assertEquals("yaw $yaw", yaw, view.tiltDegrees, 4.0)
        }
    }

    @Test
    fun acceptsAnythingFromStraightOnToAGoodSideAngle() {
        assertEquals(Placement.GOOD, assess(obliqueBoard(0.0)).placement)
        assertEquals(Placement.GOOD, assess(obliqueBoard(60.0)).placement)
    }

    @Test
    fun asksForMoreOfAnAngleOnlyWhenNearlyEdgeOn() {
        assertEquals(Placement.TOO_SIDEWAYS, assess(obliqueBoard(80.0)).placement)
    }

    @Test
    fun noticesABoardThatIsTooSmallOrCutOff() {
        assertEquals(Placement.TOO_FAR, assess(obliqueBoard(20.0, distance = 2500.0)).placement)
        assertEquals(Placement.CUT_OFF, assess(obliqueBoard(20.0, distance = 350.0)).placement)
        assertEquals(Placement.CUT_OFF, assess(obliqueBoard(20.0, cx = 560.0)).placement)
    }

    @Test
    fun predictsAMissingCornerFromTheOtherThree() {
        val board = obliqueBoard(25.0)
        val truth = BoardGeometry.MODEL_CALIBRATION_POINTS.map(board::apply)
        for (missing in 0 until 4) {
            val partial = truth.mapIndexed { i, p -> if (i == missing) null else p }
            val rough = completeCorners(partial)!![missing]
            assertTrue("corner $missing: $rough vs ${truth[missing]}", Math.hypot(rough.x - truth[missing].x, rough.y - truth[missing].y) < 60.0)
            val exact = completeCorners(partial, board)!![missing]
            assertTrue(Math.hypot(exact.x - truth[missing].x, exact.y - truth[missing].y) < 1e-6)
        }
        assertNull(completeCorners(listOf(null, null, truth[2], truth[3])))
    }
}
class MlDartTrackerTest {
    private val frame = ColorFrame(640, 480, ByteArray(640 * 480 * 3))
    private val boardToPixel = Homography.fromFourPoints(
        BoardGeometry.MODEL_CALIBRATION_POINTS,
        listOf(Point(268.0, 66.0), Point(384.0, 421.0), Point(112.0, 296.0), Point(525.0, 190.0)),
    )

    private fun pixelOf(hit: DartHit) = boardToPixel.apply(BoardGeometry.centreOf(hit))

    private var visible: List<Point> = emptyList()
    private var withBoard = true
    private var hidden: Set<Int> = emptySet()
    private var hints = ArrayList<List<Point>?>()
    private val tracker = MlDartTracker({ _, hint ->
        hints += hint
        FrameDetections(
            BoardGeometry.MODEL_CALIBRATION_POINTS.mapIndexed { i, p -> if (withBoard && i !in hidden) boardToPixel.apply(p) else null },
            visible,
        )
    })

    private fun look() = tracker.process(frame)
    private fun landed(events: List<TrackerEvent>) = events.filterIsInstance<TrackerEvent.DartLanded>()

    @Test
    fun scoresADartOnceItHasBeenSeenTwice() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        assertTrue(look().isEmpty())
        val events = landed(look())
        assertEquals(DartHit(20, Multiplier.TRIPLE), events.single().hit)
        assertEquals(1, tracker.darts)
        assertEquals(TrackerState.READY, tracker.state)
    }

    @Test
    fun scoresTheDartThatWasHidingBehindOneThatIsReleased() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        visible = listOf(pixelOf(hit))
        repeat(2) { look() }
        val spot = BoardGeometry.centreOf(hit)
        assertTrue(tracker.releaseAt(spot))
        assertEquals(0, tracker.darts)
        assertTrue(look().isEmpty())
        assertEquals(hit, landed(look()).single().hit)
        assertEquals(1, tracker.darts)
    }

    @Test
    fun releasingAnEmptySpotDoesNothing() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        repeat(2) { look() }
        assertEquals(false, tracker.releaseAt(BoardGeometry.centreOf(DartHit(3, Multiplier.SINGLE))))
        assertEquals(1, tracker.darts)
    }

    @Test
    fun doesNotScoreTheSameDartTwice() {
        visible = listOf(pixelOf(DartHit(19, Multiplier.SINGLE)))
        repeat(2) { look() }
        repeat(5) { assertTrue(landed(look()).isEmpty()) }
        assertEquals(1, tracker.darts)
    }

    @Test
    fun scoresNewDartsAsTheyArrive() {
        val hits = listOf(DartHit(20, Multiplier.TRIPLE), DartHit(5, Multiplier.SINGLE), DartHit(25, Multiplier.DOUBLE))
        val scored = ArrayList<DartHit>()
        for (i in hits.indices) {
            visible = hits.take(i + 1).map(::pixelOf)
            repeat(2) { scored += landed(look()).map { it.hit } }
        }
        assertEquals(hits, scored)
    }

    @Test
    fun ignoresAFlickeringDetectionThatIsNotSeenTwiceInARow() {
        val phantom = pixelOf(DartHit(7, Multiplier.SINGLE))
        repeat(4) {
            visible = if (it % 2 == 0) listOf(phantom) else emptyList()
            assertTrue(landed(look()).isEmpty())
        }
        assertEquals(0, tracker.darts)
    }

    @Test
    fun reportsDartsPulledOutOnlyAfterSeveralMissedLooks() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)), pixelOf(DartHit(1, Multiplier.SINGLE)))
        repeat(2) { look() }
        assertEquals(2, tracker.darts)
        visible = emptyList()
        assertTrue(look().isEmpty())
        assertTrue(look().isEmpty())
        val events = look()
        assertEquals(TrackerEvent.DartsRemoved(0), events.single())
        assertEquals(0, tracker.darts)
    }

    @Test
    fun aMissedLookWhileAHandCoversADartDoesNotRemoveIt() {
        val dart = pixelOf(DartHit(20, Multiplier.SINGLE))
        visible = listOf(dart)
        repeat(2) { look() }
        visible = emptyList(); look()
        visible = listOf(dart); look()
        visible = emptyList(); look()
        assertEquals(1, tracker.darts)
    }

    @Test
    fun ignoresDartsOutsideTheBoard() {
        visible = listOf(boardToPixel.apply(Point(0.0, 230.0)))
        repeat(3) { assertTrue(look().isEmpty()) }
        assertEquals(0, tracker.darts)
    }

    @Test
    fun waitsWhenTheBoardIsNotVisibleAndKeepsNoDarts() {
        withBoard = false
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        repeat(3) { assertTrue(look().isEmpty()) }
        assertEquals(TrackerState.WAITING_FOR_BOARD, tracker.state)
        assertNull(tracker.boardToPixel)
    }

    @Test
    fun marksTheBoardEmptyOnRequest() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        repeat(2) { look() }
        tracker.markBoardEmpty()
        assertEquals(0, tracker.darts)
    }

    @Test
    fun keepsScoringWithOneCornerHidden() {
        hidden = setOf(1)
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        look()
        assertEquals(DartHit(20, Multiplier.TRIPLE), landed(look()).single().hit)
    }

    @Test
    fun holdsTheBoardWhileAHandCoversItAndDoesNotLoseDarts() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        repeat(2) { look() }
        withBoard = false
        visible = emptyList()
        repeat(MlTrackerConfig().holdLooks) {
            assertTrue(look().isEmpty())
            assertEquals(TrackerState.READY, tracker.state)
        }
        assertEquals(1, tracker.darts)
        look()
        assertEquals(TrackerState.WAITING_FOR_BOARD, tracker.state)
        assertEquals(1, tracker.darts)
    }

    @Test
    fun givesTheDetectorLastLooksCornersAsAHintAndReportsTheView() {
        look(); look()
        assertNull(hints[0])
        assertEquals(4, hints[1]!!.size)
        assertTrue(tracker.view != null)
    }
}