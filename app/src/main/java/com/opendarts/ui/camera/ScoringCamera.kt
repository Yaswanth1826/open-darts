package com.opendarts.ui.camera

import android.os.Handler
import android.os.Looper
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import com.opendarts.vision.BoardDetector
import com.opendarts.vision.BoardFit
import com.opendarts.vision.DartTracker
import com.opendarts.vision.GrayFrame
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState
import com.opendarts.vision.ml.BoardView
import com.opendarts.vision.ml.DartNet
import com.opendarts.vision.ml.MlDartTracker
import com.opendarts.vision.ml.TwoPassDetector
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

enum class CameraPhase { FINDING_BOARD, NOT_FOUND, TRACKING }

/** What the UI shows about the camera. [boardToPixel] and the frame size let it draw the board outline. */
data class CameraStatus(
    val phase: CameraPhase = CameraPhase.FINDING_BOARD,
    val tracker: TrackerState = TrackerState.WAITING_FOR_BOARD,
    val darts: Int = 0,
    val boardToPixel: Homography? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** True while the trained network is doing the detection. */
    val usingAi: Boolean = false,
    /** How the camera sees the board (angle, size, cut-off), once the network has found it. */
    val view: BoardView? = null,
    /** A hand or arm is moving in front of the board, so darts are neither added nor taken off until it has gone. */
    val handInWay: Boolean = false,
    /** Training photos stored on the phone. */
    val collected: Int = 0,
)

/**
 * Finds the board by itself (no taps) and turns camera frames into dart events on the main thread.
 *
 * With a trained network ([net]) every frame is not needed: the network is asked about twice a second and
 * a dart counts once it is seen in two looks in a row. Without a network (or after the player lines the board
 * up by hand) frames go through the classic [DartTracker] and the board is found by [BoardDetector].
 */
class ScoringCamera(
    private val store: CalibrationStore,
    private val onEvent: (TrackerEvent) -> Unit,
    private val onStatus: (CameraStatus) -> Unit,
    private val capture: DebugCapture? = null,
    private val net: DartNet? = null,
    private val collector: TrainingCollector? = null,
) {
    private val mlTracker = net?.let { MlDartTracker(TwoPassDetector(it)::detect) }
    @Volatile private var mlActive = net != null
    @Volatile private var released = false
    private var lastLook = 0L
    private var missedLooks = 0

    private val main = Handler(Looper.getMainLooper())
    private val detector = Executors.newSingleThreadExecutor()
    private val detecting = AtomicBoolean(false)

    @Volatile private var tracker: DartTracker? = null
    @Volatile private var fit: Homography? = null
    @Volatile private var frameSize = 0 to 0
    @Volatile private var failedAttempts = 0
    @Volatile private var useStoredCalibration = false
    private val pending = java.util.concurrent.CopyOnWriteArrayList<BoardFit>()
    private val resetRequested = AtomicBoolean(true)
    private val startRequested = AtomicBoolean(false)
    private val searchAgain = AtomicBoolean(false)
    private var lastStatus: CameraStatus? = null
    @Volatile private var lastFrame: CameraFrame? = null
    @Volatile private var lastColor: com.opendarts.vision.ml.ColorFrame? = null

    /** Keeps a labelled photo of every dart (and of the emptied board) to retrain the network on real pictures. */
    @Volatile var collecting = false
    @Volatile private var collected = collector?.count() ?: 0

    /** The player corrected a dart, so the latest photo's labels need a second look before training. */
    fun flagLatestPhoto() {
        if (collecting) collector?.flagLatest()
    }

    /** Saves the latest frame now (for example when a dart was misread). */
    fun saveFrame(label: String, onSaved: (String) -> Unit) {
        val frame = lastFrame
        if (frame == null || capture == null) { onSaved(""); return }
        capture.save(frame.gray, label, mlTracker?.boardToPixel?.takeIf { mlActive } ?: fit, emptyList(), lastColor) { name -> main.post { onSaved(name) } }
    }

    /** The next calm frame is taken as the empty board. */
    fun boardIsEmpty() {
        resetRequested.set(true)
        mlTracker?.markBoardEmpty()
    }

    /** Takes the next frame as the empty board, however busy the scene is. */
    fun startNow() {
        startRequested.set(true)
        mlTracker?.markBoardEmpty()
    }

    /** Stops the automatic search while the player lines the board up by hand. */
    @Volatile var searchPaused = false

    /** Uses a board circle the player placed over the picture (assumes the board is seen roughly square-on). */
    fun useGuide(guide: Guide) {
        mlActive = false
        val (w, h) = frameSize
        if (w == 0) return
        val scale = guide.radius * minOf(w, h) / BoardGeometry.DOUBLE_OUTER_RADIUS
        val src = BoardGeometry.CALIBRATION_POINTS
        val dst = src.map { Point(guide.x * w + scale * it.x, guide.y * h - scale * it.y) }
        useStoredCalibration = false
        failedAttempts = 0
        pending.clear()
        install(Homography.fromFourPoints(src, dst), w, h)
    }

    private val releases = java.util.concurrent.ConcurrentLinkedQueue<Point>()

    /** A dart at [board] (millimetres) was taken out by the player because another is hiding behind it: score what is still seen there. */
    fun releaseSpot(board: Point) {
        releases.add(board)
    }

    /** Forgets the board and looks for it again. */
    fun findBoardAgain() {
        mlActive = net != null
        missedLooks = 0
        useStoredCalibration = false
        searchAgain.set(true)
    }

    /** Uses the four-tap calibration saved by the manual calibration screen. */
    fun useManualCalibration() {
        mlActive = false
        useStoredCalibration = true
        searchAgain.set(true)
    }

    /** Turns the board model by whole segments, in case the 20 was not found at the top. */
    fun rotateBoard(degrees: Double) {
        val current = fit ?: return
        val a = Math.toRadians(degrees)
        val src = BoardGeometry.CALIBRATION_POINTS
        val dst = src.map {
            current.apply(it.x * cos(a) - it.y * sin(a), it.x * sin(a) + it.y * cos(a))
        }
        val (w, h) = frameSize
        install(Homography.fromFourPoints(src, dst), w, h)
    }

    /** Frames arrive on the analyzer thread; one bad frame or a late frame after [release] must never crash the app. */
    fun onFrame(camera: CameraFrame) {
        if (released) return
        try {
            handle(camera)
        } catch (e: Exception) {
            if (!released) android.util.Log.w("ScoringCamera", "frame skipped", e)
        }
    }

    private fun handle(camera: CameraFrame) {
        lastFrame = camera
        if (mlActive && mlTracker != null) { look(camera, mlTracker); return }
        val frame = camera.gray
        if (searchAgain.getAndSet(false)) {
            tracker = null
            fit = null
            pending.clear()
            failedAttempts = 0
        }
        val size = frame.width to frame.height
        if (size != frameSize) {
            frameSize = size
            tracker = null
            fit = null
            pending.clear()
        }
        var t = tracker
        if (t == null) {
            if (useStoredCalibration) tryStored(frame)
            else if (!searchPaused && detecting.compareAndSet(false, true)) detector.execute { search(frame) }
            publish(CameraStatus(if (failedAttempts >= NOT_FOUND_AFTER) CameraPhase.NOT_FOUND else CameraPhase.FINDING_BOARD, frameWidth = frame.width, frameHeight = frame.height))
            return
        }
        if (resetRequested.getAndSet(false)) t.markBoardEmpty()
        if (startRequested.getAndSet(false)) t.startNow()
        val events = t.process(frame)
        publish(CameraStatus(CameraPhase.TRACKING, t.state, t.darts, fit, frame.width, frame.height))
        events.forEach { event -> main.post { onEvent(event) } }
    }

    private fun look(camera: CameraFrame, tracker: MlDartTracker) {
        val now = System.currentTimeMillis()
        if (searchAgain.getAndSet(false)) { tracker.markBoardEmpty(); missedLooks = 0 }
        if (now - lastLook < LOOK_INTERVAL_MS) return
        lastLook = now
        val colour = camera.color()
        lastColor = colour
        frameSize = colour.width to colour.height
        resetRequested.set(false)
        startRequested.set(false)
        while (true) tracker.releaseAt(releases.poll() ?: break)
        val events = tracker.process(colour)
        val ready = tracker.state == com.opendarts.vision.TrackerState.READY
        missedLooks = if (ready) 0 else missedLooks + 1
        val phase = when {
            ready -> CameraPhase.TRACKING
            missedLooks >= NOT_FOUND_LOOKS -> CameraPhase.NOT_FOUND
            else -> CameraPhase.FINDING_BOARD
        }
        publish(CameraStatus(phase, tracker.state, tracker.darts, tracker.boardToPixel, colour.width, colour.height, usingAi = true, view = tracker.view.takeIf { ready }, handInWay = tracker.handInWay, collected = collected))
        events.forEach { event ->
            if (collecting) collect(event, colour, tracker)
            main.post { onEvent(event) }
        }
    }

    private fun collect(event: TrackerEvent, colour: com.opendarts.vision.ml.ColorFrame, tracker: MlDartTracker) {
        val corners = tracker.cornerPixels ?: return
        val note = when {
            event is TrackerEvent.DartLanded -> "dart-${event.hit.multiplier.name}-${event.hit.base}"
            event is TrackerEvent.DartsRemoved && event.remaining == 0 -> "empty"
            else -> return
        }
        collector?.save(colour, corners, tracker.dartPixels, note) { n ->
            if (n >= 0) {
                collected = n
                lastStatus?.let { publish(it.copy(collected = n)) }
            }
        }
    }

    private fun tryStored(frame: GrayFrame) {
        val normalised = store.load(frame.height >= frame.width)
        if (normalised == null) { failedAttempts = NOT_FOUND_AFTER; return }
        val pixels = normalised.map { Point(it.x * frame.width, it.y * frame.height) }
        install(Homography.fromFourPoints(BoardGeometry.CALIBRATION_POINTS, pixels), frame.width, frame.height)
    }

    private fun search(frame: GrayFrame) {
        try {
            val found = runCatching { BoardDetector.detect(frame) }.getOrNull()
            if (tracker != null) return
            if (found == null) {
                pending.clear()
                failedAttempts++
            } else if (pending.any { found.differenceTo(it) < AGREEMENT }) {
                failedAttempts = 0
                store.save(frame.height >= frame.width, found.normalisedCorners)
                install(found.boardToPixel, frame.width, frame.height)
                pending.clear()
            } else {
                pending.add(found)
                if (pending.size > 3) pending.removeAt(0)
                failedAttempts++
            }
        } finally {
            detecting.set(false)
        }
    }
    private fun install(boardToPixel: Homography, width: Int, height: Int) {
        if (width == 0) return
        val created = runCatching { DartTracker(boardToPixel.inverse(), width, height) }.getOrNull() ?: return
        fit = boardToPixel
        resetRequested.set(true)
        startRequested.set(false)
        tracker = created
    }

    private fun publish(status: CameraStatus) {
        if (status == lastStatus) return
        lastStatus = status
        main.post { onStatus(status) }
    }

    fun release() {
        released = true
        detector.shutdown()
        (net as? AutoCloseable)?.close()
        capture?.release()
        collector?.release()
    }

    private companion object {
        /** Two detections must agree to within this share of the frame's shorter side. */
        const val AGREEMENT = 0.012
        const val NOT_FOUND_AFTER = 6
        const val LOOK_INTERVAL_MS = 450L
        const val NOT_FOUND_LOOKS = 8
    }
}