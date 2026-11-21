package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

data class OneTwentyOneConfig(val start: Int = 121, val attempts: Int = 10) {
    init {
        require(start in 2..170) { "start must be between 2 and 170" }
        require(attempts >= 1) { "attempts must be at least 1" }
    }
}

/**
 * [target] is the number being checked out now, [left] what is still to score in this attempt, [visit] how many of the attempt's
 * visits have been started, [attempt] how many attempts are over and [best] the highest number checked out (0 for none yet).
 */
data class OneTwentyOnePlayer(val target: Int, val left: Int, val visit: Int = 0, val attempt: Int = 0, val best: Int = 0)

data class OneTwentyOneState(val players: List<OneTwentyOnePlayer>)

/**
 * The 121 game. Check the target out (double out) with nine darts, three visits. A checkout moves the target up by one and starts a
 * fresh attempt; running out of darts sends the player back to the starting number. After the set number of attempts the highest
 * number checked out wins. A bust ends the visit and puts the visit's darts back.
 */
class OneTwentyOneGame(players: List<Player>, val config: OneTwentyOneConfig = OneTwentyOneConfig()) : DartGame<OneTwentyOneState>(players) {
    override fun initialState() = OneTwentyOneState(players.map { OneTwentyOnePlayer(config.start, config.start) })

    override fun withoutPlayer(state: OneTwentyOneState, index: Int) = OneTwentyOneState(state.players.filterIndexed { i, _ -> i != index })

    override fun isActive(state: OneTwentyOneState, player: Int) = state.players[player].attempt < config.attempts

    /** Darts the player still has in the attempt they are on. */
    fun dartsLeft(index: Int): Int {
        val p = gameState.players[index]
        val inVisit = if (index == currentPlayerIndex && !turnOver && turnHits.isNotEmpty()) dartsLeftInTurn else 0
        return (VISITS - p.visit) * DARTS_PER_VISIT + inVisit
    }

    override fun onThrow(state: OneTwentyOneState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<OneTwentyOneState> {
        var p = state.players[player]
        if (turnBefore.isEmpty()) p = p.copy(visit = p.visit + 1)
        val left = p.left - hit.points
        return when {
            left == 0 && hit.isDouble -> {
                val next = p.target + 1
                StepResult(update(state, player, p.copy(target = next, left = next, visit = 0, attempt = p.attempt + 1, best = maxOf(p.best, p.target))), Outcome.LEG_WON, turnOver = true)
            }
            left <= 1 -> StepResult(update(state, player, p.copy(left = p.left + turnBefore.sumOf { it.points })), Outcome.BUST, turnOver = true)
            else -> StepResult(update(state, player, p.copy(left = left)), if (hit.points > 0) Outcome.SCORED else Outcome.NO_SCORE)
        }
    }

    override fun onTurnFinished(state: OneTwentyOneState, player: Int, turn: List<DartHit>, round: Int): FinishResult<OneTwentyOneState> {
        var updated = state
        val p = state.players[player]
        if (p.visit >= VISITS) updated = update(state, player, OneTwentyOnePlayer(config.start, config.start, 0, p.attempt + 1, p.best))
        if (updated.players.any { it.attempt < config.attempts }) return FinishResult(updated)
        val best = updated.players.maxOf { it.best }
        return FinishResult(updated, updated.players.indices.filter { updated.players[it].best == best })
    }

    private fun update(state: OneTwentyOneState, player: Int, p: OneTwentyOnePlayer) =
        OneTwentyOneState(state.players.toMutableList().also { it[player] = p })

    companion object {
        const val VISITS = 3
        const val DARTS_PER_VISIT = 3
    }
}
