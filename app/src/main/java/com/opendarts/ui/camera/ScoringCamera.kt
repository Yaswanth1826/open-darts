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
)

/**
 * Finds the board by itself (no taps), then runs frames through a [DartTracker] and posts events to the main thread.
 * Detection runs off the analysis thread, and the board is only accepted when two detections agree.
 */
class ScoringCamera(
    private val store: CalibrationStore,
    private val onEvent: (TrackerEvent) -> Unit,
    private val onStatus: (CameraStatus) -> Unit,
    private val capture: DebugCapture? = null,
) {
    private val main = Handler(Looper.getMainLooper())
    private val detector = Executors.newSingleThreadExecutor()
    private val detecting = AtomicBoolean(false)

    @Volatile private var tracker: DartTracker? = null
    @Volatile private var fit: Homography? = null
    @Volatile private var frameSize = 0 to 0
    @Volatile private var failedAttempts = 0
    @Volatile private var useStoredCalibration = false
    private var pending: BoardFit? = null
    private val resetRequested = AtomicBoolean(true)
    private val startRequested = AtomicBoolean(false)
    private val searchAgain = AtomicBoolean(false)
    private var lastStatus: CameraStatus? = null
    @Volatile private var lastFrame: GrayFrame? = null

    /** Saves a frame with every detected dart, to study or tune detection later. */
    @Volatile var captureEveryDart = false

    /** Saves the latest frame now (for example when a dart was misread). */
    fun saveFrame(label: String, onSaved: (String) -> Unit) {
        val frame = lastFrame
        if (frame == null || capture == null) { onSaved(""); return }
        capture.save(frame, label, fit, emptyList()) { name -> main.post { onSaved(name) } }
    }

    /** The next calm frame is taken as the empty board. */
    fun boardIsEmpty() = resetRequested.set(true)

    /** Takes the next frame as the empty board, however busy the scene is. */
    fun startNow() = startRequested.set(true)

    /** Forgets the board and looks for it again. */
    fun findBoardAgain() {
        useStoredCalibration = false
        searchAgain.set(true)
    }

    /** Uses the four-tap calibration saved by the manual calibration screen. */
    fun useManualCalibration() {
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

    fun onFrame(frame: GrayFrame) {
        if (searchAgain.getAndSet(false)) {
            tracker = null
            fit = null
            pending = null
            failedAttempts = 0
        }
        lastFrame = frame
        val size = frame.width to frame.height
        if (size != frameSize) {
            frameSize = size
            tracker = null
            fit = null
            pending = null
        }
        var t = tracker
        if (t == null) {
            if (useStoredCalibration) tryStored(frame)
            else if (detecting.compareAndSet(false, true)) detector.execute { search(frame) }
            publish(CameraStatus(if (failedAttempts >= NOT_FOUND_AFTER) CameraPhase.NOT_FOUND else CameraPhase.FINDING_BOARD, frameWidth = frame.width, frameHeight = frame.height))
            return
        }
        if (resetRequested.getAndSet(false)) t.markBoardEmpty()
        if (startRequested.getAndSet(false)) t.startNow()
        val events = t.process(frame)
        publish(CameraStatus(CameraPhase.TRACKING, t.state, t.darts, fit, frame.width, frame.height))
        events.forEach { event ->
            if (captureEveryDart && event is TrackerEvent.DartLanded) {
                capture?.save(frame, "dart-${event.hit.multiplier.name}-${event.hit.base}", fit, listOf(event.pixel))
            }
            main.post { onEvent(event) }
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
            val previous = pending
            if (found == null) {
                pending = null
                failedAttempts++
            } else if (previous != null && found.differenceTo(previous) < AGREEMENT) {
                failedAttempts = 0
                store.save(frame.height >= frame.width, found.normalisedCorners)
                install(found.boardToPixel, frame.width, frame.height)
                pending = null
            } else {
                pending = found
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
        detector.shutdown()
        capture?.release()
    }

    private companion object {
        /** Two detections must agree to within this share of the frame's shorter side. */
        const val AGREEMENT = 0.012
        const val NOT_FOUND_AFTER = 6
    }
}