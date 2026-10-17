package com.opendarts.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.DartTrace
import com.opendarts.game.engine.TurnTrace
import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit
import com.opendarts.geometry.Point

/** Holds the running game across configuration changes and exposes a revision counter for Compose. */
class GameSession : ViewModel() {
    var setup by mutableStateOf(GameSetup())
        private set
    /** The setup being edited on the setup screen. */
    var draft by mutableStateOf(GameSetup())

    var game: DartGame<*>? = null
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

    val isRunning: Boolean get() = game != null

    fun start(newSetup: GameSetup) {
        setup = newSetup
        game = newSetup.buildGame()
        trace.sync(0)
        message = "${game!!.currentPlayer.name} to throw"
        dismissedResult = false
        revision++
    }

    fun rematch() = start(setup)

    /** Switches between camera and keypad scoring in the middle of a game. */
    fun switchInput(mode: InputMode) {
        setup = setup.copy(input = mode)
        revision++
    }

    fun leave() {
        game = null
        revision++
    }

    fun throwDart(hit: DartHit, fromCamera: Boolean = false, spot: Point? = null, behind: Int? = null) {
        val g = game ?: return
        if (g.isFinished) return
        val thrower = g.currentPlayer.name
        val outcome = g.throwDart(hit)
        trace.landed(g.turnHits.size, DartTrace(spot, behind))
        val source = if (fromCamera) "Camera: " else ""
        message = source + when (outcome) {
            Outcome.BUST -> "$thrower: $hit - BUST"
            Outcome.NOT_IN -> "$thrower: $hit - not in yet"
            Outcome.LEG_WON -> "$thrower wins the leg!"
            Outcome.SET_WON -> "$thrower wins the set!"
            Outcome.GAME_WON -> "$thrower wins!"
            Outcome.NO_SCORE -> "$thrower: $hit"
            else -> "$thrower: $hit"
        }
        revision++
    }

    fun undo() {
        val g = game ?: return
        message = if (g.undo()) "Last dart removed" else "Nothing to undo"
        trace.sync(g.turnHits.size)
        dismissedResult = false
        revision++
    }

    fun nextPlayer() {
        val g = game ?: return
        if (g.isFinished) return
        g.endTurn()
        trace.sync(0)
        message = "${g.currentPlayer.name} to throw"
        revision++
    }

    /** Goes back to the previous player's turn, keeping their darts so they can be corrected. */
    fun previousPlayer() {
        val g = game ?: return
        if (g.previousPlayer()) {
            trace.sync(0)
            trace.sync(g.turnHits.size)
            message = "Back to ${g.currentPlayer.name}"
            dismissedResult = false
        } else message = "Already at the first turn"
        revision++
    }

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
        val done = when {
            index < g.turnHits.size -> g.editDart(index, hit)
            hit != null && index == g.turnHits.size && !g.isFinished -> { g.throwDart(hit); trace.landed(g.turnHits.size, DartTrace(null)); true }
            else -> false
        }
        if (!done) return null
        if (hit == null) trace.removed(index)
        trace.sync(g.turnHits.size)
        message = when {
            hit == null && lookBehind -> "Dart ${index + 1} removed \u2013 looking for a dart behind it"
            hit == null -> "Dart ${index + 1} removed"
            else -> "${g.currentPlayer.name}: dart ${index + 1} is $hit"
        }
        dismissedResult = false
        revision++
        return if (hit == null && lookBehind) spot else null
    }

    /** The next empty slot [slot] holds a dart that landed behind dart [front]: it scores the same, and the pair is remembered. */
    fun dartBehind(slot: Int, front: Int) {
        val g = game ?: return
        val hit = g.turnHits.getOrNull(front) ?: return
        if (slot != g.turnHits.size || g.isFinished) return
        g.throwDart(hit)
        trace.landed(g.turnHits.size, DartTrace(null, behind = front))
        message = "${g.currentPlayer.name}: dart ${slot + 1} is $hit, behind dart ${front + 1}"
        dismissedResult = false
        revision++
    }

    /** Called when the camera sees the board cleared: hands over only if the current player actually threw. */
    fun dartsPulled() {
        val g = game ?: return
        if (!g.isFinished && g.turnHits.isNotEmpty()) nextPlayer()
    }
}