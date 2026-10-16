package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

data class CountUpConfig(val rounds: Int = 8) {
    init { require(rounds >= 1) { "rounds must be at least 1" } }
}

data class CountUpState(val scores: List<Int>)

/** Score as much as possible over a fixed number of rounds; highest total wins. */
class CountUpGame(players: List<Player>, val config: CountUpConfig = CountUpConfig()) : DartGame<CountUpState>(players) {
    override fun initialState() = CountUpState(players.map { 0 })

    override fun onThrow(state: CountUpState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<CountUpState> {
        val scores = state.scores.toMutableList().also { it[player] += hit.points }
        return StepResult(CountUpState(scores), if (hit.points > 0) Outcome.SCORED else Outcome.NO_SCORE)
    }

    override fun onTurnFinished(state: CountUpState, player: Int, turn: List<DartHit>, round: Int): FinishResult<CountUpState> {
        if (round < config.rounds || player != players.lastIndex) return FinishResult(state)
        val best = state.scores.max()
        return FinishResult(state, state.scores.indices.filter { state.scores[it] == best })
    }
}