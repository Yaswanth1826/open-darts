package com.opendarts.ui.camera

import android.os.Handler
import android.os.Looper
import com.opendarts.geometry.BoardGeometry
import com.opendarts.geometry.Homography
import com.opendarts.geometry.Point
import com.opendarts.vision.DartTracker
import com.opendarts.vision.GrayFrame
import com.opendarts.vision.TrackerEvent
import com.opendarts.vision.TrackerState
import java.util.concurrent.atomic.AtomicBoolean

/** Runs frames through a [DartTracker] (created lazily once the frame size is known) and posts events to the main thread. */
class ScoringCamera(
    private val store: CalibrationStore,
    private val onEvent: (TrackerEvent) -> Unit,
    private val onState: (TrackerState, Int) -> Unit,
    private val onUncalibrated: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val resetRequested = AtomicBoolean(true)
    private var tracker: DartTracker? = null
    private var trackerKey = ""
    private val invalidated = AtomicBoolean(false)
    private var lastState: TrackerState? = null
    private var lastDarts = -1

    @Volatile var calibrated = true
        private set

    /** The next calm frame is taken as the empty board. */
    fun boardIsEmpty() = resetRequested.set(true)

    fun onFrame(frame: GrayFrame) {
        val portrait = frame.height >= frame.width
        val key = "${frame.width}x${frame.height}"
        if (invalidated.getAndSet(false)) tracker = null
        if (tracker == null || key != trackerKey) {
            val normalised = store.load(portrait)
            if (normalised == null) {
                if (calibrated) main.post { onUncalibrated() }
                calibrated = false
                return
            }
            calibrated = true
            val pixels = normalised.map { Point(it.x * frame.width, it.y * frame.height) }
            tracker = DartTracker(Homography.fromFourPoints(pixels, BoardGeometry.CALIBRATION_POINTS), frame.width, frame.height)
            trackerKey = key
            resetRequested.set(true)
        }
        val t = tracker ?: return
        if (resetRequested.getAndSet(false)) t.markBoardEmpty()
        val events = t.process(frame)
        if (t.state != lastState || t.darts != lastDarts) {
            lastState = t.state
            lastDarts = t.darts
            val state = t.state
            val darts = t.darts
            main.post { onState(state, darts) }
        }
        events.forEach { event -> main.post { onEvent(event) } }
    }

    /** Forces a rebuild after the user recalibrated. */
    fun invalidate() = invalidated.set(true)
}