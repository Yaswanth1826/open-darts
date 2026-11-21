package com.opendarts.game.party

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

/** The nine targets of Halve-It, in order. */
enum class HalveTarget(val label: String) {
    FIFTEEN("15"), SIXTEEN("16"), ANY_DOUBLE("Any double"), SEVENTEEN("17"), EIGHTEEN("18"), ANY_TREBLE("Any treble"), NINETEEN("19"), TWENTY("20"), BULL("Bull");

    /** What a dart is worth against this target; zero when it does not count. */
    fun worth(hit: DartHit): Int = when (this) {
        ANY_DOUBLE -> if (hit.isDouble) hit.points else 0
        ANY_TREBLE -> if (hit.isTreble) hit.points else 0
        BULL -> if (hit.isBull) hit.points else 0
        else -> if (hit.base == label.toInt()) hit.points else 0
    }
}

data class HalveItState(val scores: List<Int>)

/**
 * Halve-It: nine rounds with a different target each. Darts on the target add their points. A visit without a single
 * hit on the target halves the whole score (rounded down). The highest score after the last round wins.
 */
class HalveItGame(players: List<Player>) : DartGame<HalveItState>(players) {
    override fun initialState() = HalveItState(players.map { START })

    override fun withoutPlayer(state: HalveItState, index: Int) = HalveItState(state.scores.filterIndexed { i, _ -> i != index })

    fun targetFor(round: Int): HalveTarget = HalveTarget.entries[(round - 1).coerceIn(0, ROUNDS - 1)]

    /** The target of the visit on screen (or of the next one, once it is over); null when the game is over. */
    val currentTarget: HalveTarget?
        get() {
            if (isFinished) return null
            val next = if (turnOver && currentPlayerIndex == players.lastIndex) round + 1 else round
            return if (next > ROUNDS) null else targetFor(next)
        }

    override fun onThrow(state: HalveItState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<HalveItState> {
        val worth = targetFor(round).worth(hit)
        if (worth == 0) return StepResult(state, Outcome.NO_SCORE)
        return StepResult(HalveItState(state.scores.toMutableList().also { it[player] += worth }), Outcome.SCORED)
    }

    override fun onTurnFinished(state: HalveItState, player: Int, turn: List<DartHit>, round: Int): FinishResult<HalveItState> {
        val target = targetFor(round)
        var scores = state.scores
        if (turn.none { target.worth(it) > 0 }) scores = scores.toMutableList().also { it[player] /= 2 }
        val updated = HalveItState(scores)
        if (round < ROUNDS || player != players.lastIndex) return FinishResult(updated)
        val best = scores.max()
        return FinishResult(updated, scores.indices.filter { scores[it] == best })
    }

    companion object {
        const val ROUNDS = 9
        const val START = 40
    }
}
