package com.opendarts.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.Outcome
import com.opendarts.game.model.DartHit

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

    val isRunning: Boolean get() = game != null

    fun start(newSetup: GameSetup) {
        setup = newSetup
        game = newSetup.buildGame()
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

    fun throwDart(hit: DartHit, fromCamera: Boolean = false) {
        val g = game ?: return
        if (g.isFinished) return
        val thrower = g.currentPlayer.name
        val outcome = g.throwDart(hit)
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
        dismissedResult = false
        revision++
    }

    fun nextPlayer() {
        val g = game ?: return
        if (g.isFinished) return
        g.endTurn()
        message = "${g.currentPlayer.name} to throw"
        revision++
    }

    /** Called when the camera sees the board cleared: hands over only if the current player actually threw. */
    fun dartsPulled() {
        val g = game ?: return
        if (!g.isFinished && g.turnHits.isNotEmpty()) nextPlayer()
    }
}