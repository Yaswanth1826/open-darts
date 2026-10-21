package com.opendarts.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.DartTrace
import com.opendarts.game.engine.GameAction
import com.opendarts.game.engine.GameLog
import com.opendarts.game.engine.TurnTrace
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.VisitCall
import com.opendarts.game.model.DartHit
import com.opendarts.geometry.Point
import com.opendarts.vision.ml.RememberedDart

enum class CalloutKind { PLAIN, GOOD, BUST }

/** The big word flashed over the board when a visit ends, e.g. BUST, NO SCORE, 60, 180 or GAME SHOT. */
data class Callout(val player: String, val text: String, val kind: CalloutKind, val id: Int) {
    companion object {
        fun of(player: String, call: String, id: Int): Callout {
            val kind = when (call) {
                "Bust" -> CalloutKind.BUST
                "Game shot", "One hundred and eighty" -> CalloutKind.GOOD
                else -> CalloutKind.PLAIN
            }
            val text = if (call == "One hundred and eighty") "180" else call.uppercase()
            return Callout(player, text, kind, id)
        }
    }
}

/** Holds the running game across configuration changes and exposes a revision counter for Compose. */
class GameSession : ViewModel() {
    var setup by mutableStateOf(GameSetup())
        private set
    /** The setup being edited on the setup screen. */
    var draft by mutableStateOf(GameSetup())

    var game: DartGame<*>? by mutableStateOf(null)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("")
        private set
    var dismissedResult by mutableStateOf(false)
    /** Set once the user calibrated by hand; the camera then uses that instead of searching for the board. */
    var manualCalibration by mutableStateOf(false)

    /** Where each dart of the turn on screen was seen, and which ones hide behind another. */
    private val trace = TurnTrace()

    /** Everything played so far, saved after every change so an unfinished game survives the app being closed. */
    private val log = GameLog()
    private var saver: GameSaver? = null

    /** Connects the place unfinished games are kept and picks up the game that was saved there, if none is running. */
    fun attach(saver: GameSaver) {
        this.saver = saver
        if (game != null) return
        val (saved, savedLog) = saver.load() ?: return
        val restored = runCatching { saved.buildGame().also { savedLog.replay(it) } }.getOrNull()
        if (restored == null) { saver.clear(); return }
        setup = saved
        game = restored
        log.clear()
        savedLog.actions.forEach(log::add)
        trace.sync(restored.turnHits.size)
        message = "Welcome back \u2013 ${restored.currentPlayer.name} to throw"
        dismissedResult = false
        revision++
    }

    private fun changed() {
        revision++
        if (game != null) saver?.save(setup, log)
    }

    val isRunning: Boolean get() = game != null

    /** What the camera knew about the darts on the board when its screen went away, so a turned phone or a pause does not score them again. */
    var cameraMemory: List<RememberedDart>? = null

    /** Set by the camera screen: the dart seen at this spot (mm) was undone, so the camera must not count it towards the three on the board. */
    var onForget: ((Point) -> Unit)? = null

    /** Set by the screen while it is showing: a dart was scored (from the camera or the keypad), and what to say when a visit ends. */
    var onDart: ((DartHit) -> Unit)? = null
    var onVisit: ((String) -> Unit)? = null

    /** The latest visit call, for the screen to flash; the screen clears it once shown. */
    var callout by mutableStateOf<Callout?>(null)
        private set
    private var calloutId = 0

    fun clearCallout(id: Int) { if (callout?.id == id) callout = null }

    private fun visitEnded(g: DartGame<*>, call: String) {
        callout = Callout.of(g.currentPlayer.name, call, ++calloutId)
        onVisit?.invoke(call)
    }

    /** After a bust or a leg won with fewer than three darts, further darts do not count; the camera still sees them until the board is cleared. */
    private var turnEndedEarly = false

    /** The camera saw darts leave the board, so whatever is thrown next is no longer left over from a bust. */
    fun cameraDartsRemoved() { turnEndedEarly = false }

    private fun scored(g: DartGame<*>, hit: DartHit, outcome: Outcome) {
        onDart?.invoke(hit)
        if (g.turnOver) VisitCall.of(g, outcome)?.let { visitEnded(g, it) }
    }

    fun start(newSetup: GameSetup) {
        setup = newSetup
        game = newSetup.buildGame()
        turnEndedEarly = false
        cameraMemory = null
        callout = null
        log.clear()
        trace.sync(0)
        message = "${game!!.currentPlayer.name} to throw"
        dismissedResult = false
        changed()
    }

    fun rematch() = start(setup)

    /** Switches between camera and keypad scoring in the middle of a game. */
    fun switchInput(mode: InputMode) {
        setup = setup.copy(input = mode)
        cameraMemory = null
        changed()
    }

    /** Quits for good: the unfinished game is thrown away. (Leaving the screen with Back keeps it for Resume.) */
    fun leave() {
        game = null
        cameraMemory = null
        callout = null
        log.clear()
        saver?.clear()
        revision++
    }

    fun throwDart(hit: DartHit, fromCamera: Boolean = false, spot: Point? = null, behind: Int? = null) {
        val g = game ?: return
        if (g.isFinished) return
        if (fromCamera && turnEndedEarly && g.turnOver) {
            message = "${g.currentPlayer.name}: dart seen after the visit ended is not counted"
            changed()
            return
        }
        val thrower = g.currentPlayer.name
        val outcome = g.throwDart(hit)
        turnEndedEarly = (outcome == Outcome.BUST || outcome == Outcome.LEG_WON || outcome == Outcome.SET_WON) && g.turnHits.size < g.dartsPerTurn
        log.add(GameAction.Throw(hit))
        trace.landed(g.turnHits.size, DartTrace(spot, behind))
        val source = if (fromCamera) "Camera: " else ""
        message = source + when (outcome) {
            Outcome.BUST -> "$thrower: ${hit.label} - BUST"
            Outcome.NOT_IN -> "$thrower: ${hit.label} - not in yet"
            Outcome.LEG_WON -> "$thrower wins the leg!"
            Outcome.SET_WON -> "$thrower wins the set!"
            Outcome.GAME_WON -> "$thrower wins!"
            Outcome.NO_SCORE -> "$thrower: ${hit.label}"
            else -> "$thrower: ${hit.label}"
        }
        changed()
        scored(g, hit, outcome)
    }

    fun undo() {
        val g = game ?: return
        turnEndedEarly = false
        val before = g.turnHits.size
        val seenAt = if (before > 0) trace.spotOf(before - 1) else null
        val undone = g.undo()
        if (undone && g.turnHits.size < before) seenAt?.let { onForget?.invoke(it) }
        message = if (undone) { log.add(GameAction.Undo); "Last dart removed" } else "Nothing to undo"
        trace.sync(g.turnHits.size)
        dismissedResult = false
        changed()
    }

    fun nextPlayer() {
        val g = game ?: return
        if (g.isFinished) return
        if (!g.turnOver) VisitCall.of(g, null)?.let { visitEnded(g, it) }
        g.endTurn()
        turnEndedEarly = false
        log.add(GameAction.NextTurn)
        trace.sync(0)
        message = "${g.currentPlayer.name} to throw"
        changed()
    }

    /** Goes back to the previous player's turn, keeping their darts so they can be corrected. */
    fun previousPlayer() {
        val g = game ?: return
        turnEndedEarly = false
        if (g.previousPlayer()) {
            log.add(GameAction.Previous)
            trace.sync(0)
            trace.sync(g.turnHits.size)
            message = "Back to ${g.currentPlayer.name}"
            dismissedResult = false
        } else message = "Already at the first turn"
        changed()
    }

    /** Where on the board (mm) the camera saw dart [index] of the turn on screen, or null if it was not seen by the camera. */
    fun spotOf(index: Int): Point? = trace.spotOf(index)

    /** True when the camera saw dart [index] and nothing recorded as hidden sits behind it, so a look behind it makes sense. */
    fun canLookBehind(index: Int): Boolean = trace.spotOf(index) != null && !trace.hasDartBehind(index)

    /**
     * Sets dart [index] of the turn on screen to [hit], removes it ([hit] null), or adds it when it is the next empty slot.
     * With [lookBehind] a removed camera dart also tells the camera to forget it; the spot is returned so the camera can
     * score whatever dart is still visible there as a new one. Otherwise null.
     */
    fun editDart(index: Int, hit: DartHit?, lookBehind: Boolean = false): Point? {
        val g = game ?: return null
        val spot = trace.spotOf(index)
        var added: Outcome? = null
        val done = when {
            index < g.turnHits.size -> g.editDart(index, hit).also { if (it) log.add(GameAction.Edit(index, hit)) }
            hit != null && index == g.turnHits.size && !g.isFinished -> {
                added = g.throwDart(hit); log.add(GameAction.Throw(hit)); trace.landed(g.turnHits.size, DartTrace(null)); true
            }
            else -> false
        }
        if (!done) return null
        if (hit == null) trace.removed(index)
        trace.sync(g.turnHits.size)
        message = when {
            hit == null && lookBehind -> "Dart ${index + 1} removed \u2013 looking for a dart behind it"
            hit == null -> "Dart ${index + 1} removed"
            else -> "${g.currentPlayer.name}: dart ${index + 1} is ${hit.label}"
        }
        dismissedResult = false
        changed()
        if (hit != null && added != null) scored(g, hit, added)
        return if (hit == null && lookBehind) spot else null
    }

    /** The next empty slot [slot] holds a dart that landed behind dart [front]: it scores the same, and the pair is remembered. */
    fun dartBehind(slot: Int, front: Int) {
        val g = game ?: return
        val hit = g.turnHits.getOrNull(front) ?: return
        if (slot != g.turnHits.size || g.isFinished) return
        val outcome = g.throwDart(hit)
        log.add(GameAction.Throw(hit))
        trace.landed(g.turnHits.size, DartTrace(null, behind = front))
        message = "${g.currentPlayer.name}: dart ${slot + 1} is ${hit.label}, behind dart ${front + 1}"
        dismissedResult = false
        changed()
        scored(g, hit, outcome)
    }

    /** Called when the camera sees the board cleared: hands over only if the current player actually threw. */
    fun dartsPulled() {
        val g = game ?: return
        if (!g.isFinished && g.turnHits.isNotEmpty()) nextPlayer()
    }
}