package com.opendarts.game.party

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.game.model.Player

data class NumberRoundsState(val scores: List<Int>)

/** Games with one number per round (round 1 is the 1s, round 2 the 2s ...) where the highest total after the last round wins. */
abstract class NumberRoundsGame(players: List<Player>, val rounds: Int) : DartGame<NumberRoundsState>(players) {
    override fun initialState() = NumberRoundsState(players.map { 0 })

    override fun withoutPlayer(state: NumberRoundsState, index: Int) = NumberRoundsState(state.scores.filterIndexed { i, _ -> i != index })

    /** What a dart on the round's number is worth. */
    protected abstract fun worth(hit: DartHit): Int

    /** True when the visit so far wins the game on the spot. */
    protected open fun winsNow(onNumber: List<DartHit>): Boolean = false

    /** The number being thrown at (or about to be, once the visit on screen is over), or null when the game is over. */
    val currentNumber: Int?
        get() {
            if (isFinished) return null
            val next = if (turnOver && currentPlayerIndex == players.lastIndex) round + 1 else round
            return next.takeIf { it <= rounds }
        }

    override fun onThrow(state: NumberRoundsState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<NumberRoundsState> {
        if (hit.base != round) return StepResult(state, Outcome.NO_SCORE)
        val scores = state.scores.toMutableList().also { it[player] += worth(hit) }
        val next = NumberRoundsState(scores)
        if (winsNow((turnBefore + hit).filter { it.base == round })) return StepResult(next, Outcome.GAME_WON, winners = listOf(player))
        return StepResult(next, Outcome.SCORED)
    }

    override fun onTurnFinished(state: NumberRoundsState, player: Int, turn: List<DartHit>, round: Int): FinishResult<NumberRoundsState> {
        if (round < rounds || player != players.lastIndex) return FinishResult(state)
        val best = state.scores.max()
        return FinishResult(state, state.scores.indices.filter { state.scores[it] == best })
    }
}

/**
 * Shanghai: round 1 aims at the 1s, round 2 at the 2s and so on. A dart on the number scores its face value (a double 5 is 10).
 * A single, a double and a treble of the number in one visit is a Shanghai and wins the game at once. Otherwise the highest total wins.
 */
class ShanghaiGame(players: List<Player>, rounds: Int = 7) : NumberRoundsGame(players, rounds) {
    override fun worth(hit: DartHit) = hit.points

    override fun winsNow(onNumber: List<DartHit>) = onNumber.map { it.multiplier }.toSet().size == Multiplier.entries.size
}

/** Baseball: nine innings, inning n aims at the n's. Every dart on the number scores a run: 1 for a single, 2 for a double, 3 for a treble. Most runs wins. */
class BaseballGame(players: List<Player>, innings: Int = 9) : NumberRoundsGame(players, innings) {
    override fun worth(hit: DartHit) = hit.multiplier.factor
}
