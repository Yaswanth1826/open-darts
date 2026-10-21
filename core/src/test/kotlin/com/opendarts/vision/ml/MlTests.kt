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
        val detector = TwoPassDetector(net)
        detector.detect(sideFrame(), hint)
        assertEquals(1, runs)
        val result = detector.detect(sideFrame(), hint)
        assertEquals(4, runs)
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

/** Fake tip network: the tip is the centre of the white blob in the patch; no blob means no tip. */
private class WhiteBlobTipNet(override val patchSize: Int = 32) : TipNet {
    var runs = 0

    override fun run(patch: ColorFrame): TipReading {
        runs++
        var n = 0; var sx = 0.0; var sy = 0.0
        for (y in 0 until patch.height) for (x in 0 until patch.width) {
            val o = (y * patch.width + x) * 3
            if ((patch.rgb[o].toInt() and 0xFF) > 200 && (patch.rgb[o + 1].toInt() and 0xFF) > 200 && (patch.rgb[o + 2].toInt() and 0xFF) > 200) { n++; sx += x + 0.5; sy += y + 0.5 }
        }
        if (n == 0) return TipReading(0.0, 0.0, 0.05)
        return TipReading(sx / n - patch.width / 2.0, sy / n - patch.height / 2.0, 0.95)
    }
}

class TipRefinerTest {
    private fun flatWithTips(vararg tips: Point): ColorFrame {
        val data = ByteArray(320 * 320 * 3) { 40 }
        for (tip in tips) for (dy in -3..3) for (dx in -3..3) {
            val o = ((Math.round(tip.y).toInt() + dy) * 320 + Math.round(tip.x).toInt() + dx) * 3
            data[o] = 255.toByte(); data[o + 1] = 255.toByte(); data[o + 2] = 255.toByte()
        }
        return ColorFrame(320, 320, data)
    }

    @Test
    fun movesAMarkOntoTheTip() {
        val refined = TipRefiner(WhiteBlobTipNet()).refine(flatWithTips(Point(100.0, 100.0)), listOf(Point(104.0, 97.0))).single()
        assertTrue("$refined", Math.hypot(refined.x - 100.0, refined.y - 100.0) < 1.0)
    }

    @Test
    fun dropsAMarkWhereThereIsNoTip() {
        assertTrue(TipRefiner(WhiteBlobTipNet()).refine(flatWithTips(Point(100.0, 100.0)), listOf(Point(200.0, 200.0))).isEmpty())
    }

    @Test
    fun mergesTwoMarksOfTheSameDartAndKeepsTheBestFirst() {
        val refined = TipRefiner(WhiteBlobTipNet()).refine(flatWithTips(Point(100.0, 100.0), Point(220.0, 150.0)), listOf(Point(103.0, 98.0), Point(98.0, 101.0), Point(223.0, 152.0)))
        assertEquals(2, refined.size)
        assertTrue(Math.hypot(refined[0].x - 100.0, refined[0].y - 100.0) < 1.0)
        assertTrue(Math.hypot(refined[1].x - 220.0, refined[1].y - 150.0) < 1.0)
    }

    @Test
    fun leavesAMarkAloneWhenItIsAlreadyOnTheTip() {
        val net = WhiteBlobTipNet()
        val refined = TipRefiner(net).refine(flatWithTips(Point(100.0, 100.0)), listOf(Point(100.0, 100.0))).single()
        assertTrue(Math.hypot(refined.x - 100.0, refined.y - 100.0) < 1.0)
        assertEquals(1, net.runs)
    }

    @Test
    fun theDetectorUsesTheRefinerOnTheFlatBoard() {
        val none = object : TipNet {
            override val patchSize = 32
            override fun run(patch: ColorFrame) = TipReading(0.0, 0.0, 0.0)
        }
        val net = object : DartNet {
            override val inputSize = 320
            override fun run(image: ColorFrame) = listOf(Detection(DartClass.DART, 0.9f, 0.5, 0.5)) +
                (DartClass.CAL_FIRST..DartClass.CAL_LAST).map { Detection(it, 0.9f, 0.1 * it, 0.1 * it) }
        }
        val corners = listOf(Point(100.0, 100.0), Point(500.0, 100.0), Point(500.0, 400.0), Point(100.0, 400.0))
        val frame = ColorFrame(640, 480, ByteArray(640 * 480 * 3))
        assertEquals(1, TwoPassDetector(net).detect(frame, corners).darts.size)
        assertTrue(TwoPassDetector(net, tips = TipRefiner(none)).detect(frame, corners).darts.isEmpty())
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
    private val corners = listOf(Point(268.0, 66.0), Point(384.0, 421.0), Point(112.0, 296.0), Point(525.0, 190.0))
    private var boardToPixel = Homography.fromFourPoints(BoardGeometry.MODEL_CALIBRATION_POINTS, corners)

    /** The phone is nudged: everything in the picture, the board and the darts, moves by the same amount. */
    private fun moveCamera(dx: Double, dy: Double, keep: List<DartHit>) {
        boardToPixel = Homography.fromFourPoints(BoardGeometry.MODEL_CALIBRATION_POINTS, corners.map { Point(it.x + dx, it.y + dy) })
        visible = keep.map(::pixelOf)
    }

    private fun pixelOf(hit: DartHit) = boardToPixel.apply(BoardGeometry.centreOf(hit))

    private var visible: List<Point> = emptyList()
    private var withBoard = true
    private var hidden: Set<Int> = emptySet()
    private var hints = ArrayList<List<Point>?>()
    private val detect: (ColorFrame, List<Point>?) -> FrameDetections = { _, hint ->
        hints += hint
        FrameDetections(
            BoardGeometry.MODEL_CALIBRATION_POINTS.mapIndexed { i, p -> if (withBoard && i !in hidden) boardToPixel.apply(p) else null },
            visible,
        )
    }
    private val tracker = MlDartTracker(detect)

    private fun look() = tracker.process(frame)
    private fun landed(events: List<TrackerEvent>) = events.filterIsInstance<TrackerEvent.DartLanded>()

    /** A frame with a bright block (a hand) at the given step along its path, or the plain scene. */
    private fun handAt(step: Int?): ColorFrame {
        val rgb = ByteArray(640 * 480 * 3)
        if (step != null) for (y in 100 until 300) for (x in 20 + step * 100 until 220 + step * 100) {
            val i = (y * 640 + x) * 3
            rgb[i] = -1; rgb[i + 1] = -1; rgb[i + 2] = -1
        }
        return ColorFrame(640, 480, rgb)
    }

    /** A hand reaches in and leaves again, as it does to pull darts. */
    private fun handPass() { for (step in 0 until 4) tracker.process(handAt(step)) }

    @Test
    fun aSmallCameraNudgeDoesNotScoreTheDartsAgain() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        visible = listOf(pixelOf(hit))
        repeat(3) { look() }
        moveCamera(14.0, -9.0, listOf(hit))
        repeat(8) { assertTrue(landed(look()).isEmpty()) }
        assertEquals(1, tracker.darts)
    }

    @Test
    fun aBiggerCameraMoveKeepsTheDartsAndScoresNothingNew() {
        val hit = DartHit(5, Multiplier.SINGLE)
        visible = listOf(pixelOf(hit))
        repeat(3) { look() }
        moveCamera(45.0, 30.0, listOf(hit))
        repeat(15) {
            val events = look()
            assertTrue(landed(events).isEmpty())
            assertTrue(events.none { it is TrackerEvent.DartsRemoved })
        }
        assertEquals(1, tracker.darts)
    }

    @Test
    fun aHandCoveringTheDartsDoesNotPullThemOrPassTheTurn() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        repeat(3) { tracker.process(handAt(null)) }
        assertEquals(1, tracker.darts)
        visible = emptyList()
        for (step in 0 until 8) {
            val events = tracker.process(handAt(step % 4))
            assertTrue(events.none { it is TrackerEvent.DartsRemoved })
            assertTrue(tracker.handInWay)
        }
        assertEquals(1, tracker.darts)
    }

    private fun flat(level: Int) = ColorFrame(640, 480, ByteArray(640 * 480 * 3) { level.toByte() })

    @Test
    fun aWholePictureBrightnessFlickerIsNotAHand() {
        for (i in 0 until 10) {
            tracker.process(flat(if (i % 2 == 0) 90 else 150))
            assertEquals(false, tracker.handInWay)
        }
    }

    @Test
    fun aPictureThatKeepsChangingIsEventuallyTakenAsNoiseNotAHand() {
        for (step in 0 until 30) tracker.process(handAt(step % 4))
        assertEquals(false, tracker.handInWay)
    }

    @Test
    fun dartsAreRemovedOnceTheHandHasGoneAndTheSceneIsStill() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        repeat(3) { tracker.process(handAt(null)) }
        visible = emptyList()
        for (step in 0 until 4) tracker.process(handAt(step))
        val events = (0 until 6).flatMap { tracker.process(handAt(null)) }
        assertEquals(0, tracker.darts)
        assertEquals(TrackerEvent.DartsRemoved(0), events.filterIsInstance<TrackerEvent.DartsRemoved>().single())
    }

    @Test
    fun aDartHeldInAMovingHandIsNotScored() {
        repeat(2) { tracker.process(handAt(null)) }
        for (step in 0 until 8) {
            visible = listOf(boardToPixel.apply(20.0 + step * 30, 30.0 + step * 20))
            assertTrue(landed(tracker.process(handAt(step % 4))).isEmpty())
        }
        assertEquals(0, tracker.darts)
    }

    /** The plain scene with a bright patch where a dart has landed. */
    private fun sceneWithDartAt(pixel: Point): ColorFrame {
        val rgb = ByteArray(640 * 480 * 3)
        for (y in pixel.y.toInt() - 12 until pixel.y.toInt() + 12) for (x in pixel.x.toInt() - 12 until pixel.x.toInt() + 12) {
            val i = (y * 640 + x) * 3
            rgb[i] = -1; rgb[i + 1] = -1; rgb[i + 2] = -1
        }
        return ColorFrame(640, 480, rgb)
    }

    @Test
    fun aDartThatVisiblyChangedThePictureIsScoredFromASingleLook() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        repeat(3) { tracker.process(handAt(null)) }
        visible = listOf(pixelOf(hit))
        assertEquals(hit, landed(tracker.process(sceneWithDartAt(pixelOf(hit)))).single().hit)
    }

    @Test
    fun aMarkWhereNothingChangedNeedsTwoLooks() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        repeat(3) { tracker.process(handAt(null)) }
        visible = listOf(pixelOf(hit))
        assertTrue(landed(tracker.process(handAt(null))).isEmpty())
        assertEquals(hit, landed(tracker.process(handAt(null))).single().hit)
    }

    @Test
    fun aChangeOnlyCountsWhenTheCameraIsSteady() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        repeat(3) { tracker.process(handAt(null)) }
        moveCamera(45.0, 30.0, emptyList())
        visible = listOf(pixelOf(hit))
        assertTrue(landed(tracker.process(sceneWithDartAt(pixelOf(hit)))).isEmpty())
    }

    @Test
    fun aDartThatStaysPutWhileAHandMovesIsScoredAfterThreeLooks() {
        repeat(2) { tracker.process(handAt(null)) }
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        assertTrue(landed(tracker.process(handAt(0))).isEmpty())
        assertTrue(landed(tracker.process(handAt(1))).isEmpty())
        assertEquals(DartHit(20, Multiplier.TRIPLE), landed(tracker.process(handAt(2))).single().hit)
    }

    @Test
    fun dartsConfirmedInTheSameLookAreScoredInTheOrderTheyWereFirstSeen() {
        val first = pixelOf(DartHit(20, Multiplier.TRIPLE))
        val second = pixelOf(DartHit(3, Multiplier.SINGLE))
        repeat(2) { tracker.process(handAt(null)) }
        visible = listOf(first)
        tracker.process(handAt(0))
        visible = listOf(second, first)
        assertTrue(landed(tracker.process(handAt(2))).isEmpty())
        val events = landed(tracker.process(handAt(2)))
        assertEquals(listOf(DartHit(20, Multiplier.TRIPLE), DartHit(3, Multiplier.SINGLE)), events.map { it.hit })
    }

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

    /** A point on the board [mm] to the right of where [hit] is centred. */
    private fun besideInMm(hit: DartHit, mm: Double) = BoardGeometry.centreOf(hit).let { Point(it.x + mm, it.y) }

    @Test
    fun aDartHidingNextToAnotherIsScoredWhenTheFrontDartIsNudgedAndItsTipShowsUp() {
        val front = DartHit(20, Multiplier.SINGLE)
        val hidden = besideInMm(front, 10.0)
        visible = listOf(pixelOf(front))
        repeat(2) { look() }
        assertEquals(1, tracker.darts)
        // The flights are moved aside: the front dart stays put and the dart behind it comes into view.
        visible = listOf(pixelOf(front), boardToPixel.apply(hidden))
        assertTrue(landed(look()).isEmpty())
        val scored = landed(look()).single()
        assertEquals(BoardGeometry.hitAt(hidden), scored.hit)
        assertEquals(2, tracker.darts)
        repeat(5) { assertTrue(landed(look()).isEmpty()) }
    }

    @Test
    fun aDartHidingNextToAnotherIsScoredWhenTheFrontDartIsPulledOut() {
        val front = DartHit(20, Multiplier.SINGLE)
        val hidden = besideInMm(front, 10.0)
        visible = listOf(pixelOf(front))
        repeat(2) { look() }
        visible = listOf(boardToPixel.apply(hidden))
        val events = (0 until 4).flatMap { tracker.process(handAt(it)) } + (0 until 8).flatMap { look() }
        assertEquals(listOf(BoardGeometry.hitAt(hidden)), landed(events).map { it.hit })
        assertEquals(1, tracker.darts)
    }

    @Test
    fun aTipThatJittersAFewMillimetresIsStillTheSameDart() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        visible = listOf(pixelOf(hit))
        repeat(2) { look() }
        for (i in 0 until 10) {
            visible = listOf(boardToPixel.apply(besideInMm(hit, if (i % 2 == 0) 5.0 else -5.0)))
            assertTrue(landed(look()).isEmpty())
        }
        assertEquals(1, tracker.darts)
    }

    @Test
    fun aMarkThePlayerRemovedDoesNotUseUpADartPlace() {
        // One real dart shows two marks (tip and barrel); the player removes the second one from the score.
        val tip = DartHit(20, Multiplier.SINGLE)
        val barrel = DartHit(5, Multiplier.SINGLE)
        visible = listOf(pixelOf(tip), pixelOf(barrel))
        repeat(2) { look() }
        assertEquals(2, tracker.darts)
        assertTrue(tracker.ignoreAt(BoardGeometry.centreOf(barrel)))
        assertEquals(1, tracker.darts)

        val second = DartHit(3, Multiplier.TRIPLE)
        val third = DartHit(19, Multiplier.DOUBLE)
        val scored = ArrayList<DartHit>()
        visible = listOf(pixelOf(tip), pixelOf(barrel), pixelOf(second))
        repeat(2) { scored += landed(look()).map { it.hit } }
        visible = listOf(pixelOf(tip), pixelOf(barrel), pixelOf(second), pixelOf(third))
        repeat(2) { scored += landed(look()).map { it.hit } }
        assertEquals(listOf(second, third), scored)
        assertEquals(3, tracker.darts)
    }

    @Test
    fun aMarkThePlayerRemovedIsNotScoredAgainAndDoesNotStopTheTurnPassing() {
        val tip = DartHit(20, Multiplier.SINGLE)
        val barrel = DartHit(5, Multiplier.SINGLE)
        visible = listOf(pixelOf(tip), pixelOf(barrel))
        repeat(2) { look() }
        tracker.ignoreAt(BoardGeometry.centreOf(barrel))
        repeat(6) { assertTrue(landed(look()).isEmpty()) }

        visible = listOf(pixelOf(barrel))
        handPass()
        val events = (0 until MlTrackerConfig().removeAfterMisses + 1).flatMap { look() }
        assertEquals(TrackerEvent.DartsRemoved(0), events.filterIsInstance<TrackerEvent.DartsRemoved>().single())
        assertEquals(0, tracker.darts)
        assertTrue(tracker.dartPixels.isEmpty())
    }

    @Test
    fun ignoringASpotWithNoDartDoesNothing() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        repeat(2) { look() }
        assertEquals(false, tracker.ignoreAt(BoardGeometry.centreOf(DartHit(3, Multiplier.SINGLE))))
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
        handPass()
        repeat(MlTrackerConfig().removeAfterMisses - 1) { assertTrue(look().isEmpty()) }
        val events = (0 until 3).flatMap { look() }
        assertEquals(TrackerEvent.DartsRemoved(0), events.single())
        assertEquals(0, tracker.darts)
    }

    @Test
    fun aNewTrackerCarriesOnFromTheDartsAnEarlierOneRemembered() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        visible = listOf(pixelOf(hit), pixelOf(DartHit(5, Multiplier.SINGLE)))
        repeat(2) { look() }
        val later = MlDartTracker(detect)
        later.remember(tracker.remembered())
        repeat(8) { assertTrue(landed(later.process(frame)).isEmpty()) }
        assertEquals(2, later.darts)
        assertEquals(2, later.dartPixels.size)
        assertTrue(later.dartPixels.any { Math.hypot(it.x - pixelOf(hit).x, it.y - pixelOf(hit).y) < 1.0 })
    }

    @Test
    fun aRememberedDartPulledWhileTheScreenWasAwayIsGivenUpAndAMarkTheGameIgnoredStaysIgnored() {
        val tip = DartHit(20, Multiplier.SINGLE)
        val mark = DartHit(5, Multiplier.SINGLE)
        visible = listOf(pixelOf(tip), pixelOf(mark))
        repeat(2) { look() }
        tracker.ignoreAt(BoardGeometry.centreOf(mark))
        val later = MlDartTracker(detect)
        later.remember(tracker.remembered())
        assertEquals(1, later.darts)
        visible = listOf(pixelOf(mark))
        val events = (0 until MlTrackerConfig().graceLooks + MlTrackerConfig().removeAfterMissesNoHand + 2).flatMap { later.process(frame) }
        assertEquals(TrackerEvent.DartsRemoved(0), events.filterIsInstance<TrackerEvent.DartsRemoved>().single())
        assertTrue(landed(events).isEmpty())
    }

    @Test
    fun dartsTheGameAlreadyHasAreAdoptedNotScoredAgainAndANewOneStillIs() {
        val first = DartHit(20, Multiplier.TRIPLE)
        val second = DartHit(5, Multiplier.SINGLE)
        val third = DartHit(19, Multiplier.DOUBLE)
        val later = MlDartTracker(detect)
        later.adopt(2)
        visible = listOf(pixelOf(first), pixelOf(second))
        repeat(4) { assertTrue(landed(later.process(frame)).isEmpty()) }
        assertEquals(2, later.darts)
        visible = listOf(pixelOf(first), pixelOf(second), pixelOf(third))
        val scored = (0 until 3).flatMap { landed(later.process(frame)) }
        assertEquals(listOf(third), scored.map { it.hit })
    }

    @Test
    fun adoptingGivesUpWhenTheDartsAreNotThereSoItNeverEatsARealNewDart() {
        val later = MlDartTracker(detect)
        later.adopt(2)
        visible = emptyList()
        repeat(MlTrackerConfig().adoptLooks + 1) { later.process(frame) }
        visible = listOf(pixelOf(DartHit(20, Multiplier.TRIPLE)))
        later.process(frame)
        assertEquals(DartHit(20, Multiplier.TRIPLE), landed(later.process(frame)).single().hit)
    }

    @Test
    fun aDartTheNetworkLosesWithNoHandNearTheBoardIsNotTakenAsPulled() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        repeat(2) { look() }
        visible = emptyList()
        val events = (0 until MlTrackerConfig().removeAfterMissesNoHand - 1).flatMap { look() }
        assertTrue(events.isEmpty())
        assertEquals(1, tracker.darts)
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        assertTrue(look().isEmpty())
        assertEquals(1, tracker.darts)
    }

    @Test
    fun aDartThatVanishesForGoodWithNoHandIsStillGivenUpEventually() {
        visible = listOf(pixelOf(DartHit(20, Multiplier.SINGLE)))
        repeat(2) { look() }
        visible = emptyList()
        val events = (0 until MlTrackerConfig().removeAfterMissesNoHand + 1).flatMap { look() }
        assertEquals(TrackerEvent.DartsRemoved(0), events.single())
    }

    @Test
    fun aTipReadATwelveMillimetresAwayWithNoHandAboutIsTheSameDartNotANewOne() {
        val hit = DartHit(20, Multiplier.TRIPLE)
        visible = listOf(pixelOf(hit))
        repeat(2) { look() }
        visible = listOf(boardToPixel.apply(besideInMm(hit, 12.0)))
        repeat(6) { assertTrue(landed(look()).isEmpty()) }
        assertEquals(1, tracker.darts)
        visible = listOf(boardToPixel.apply(besideInMm(hit, 12.0)))
        repeat(20) { assertTrue(look().isEmpty()) }
        assertEquals(1, tracker.darts)
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