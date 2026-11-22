package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

data class DrillState(val hits: List<Int>, val darts: List<Int>)

/**
 * A doubles drill on a list of [targets] (1 to 20, or 25 for the bull). Every round all players throw one visit at the
 * round's double; the targets repeat for [PASSES] passes. Every dart in the double scores a point, the most points wins.
 */
class TargetDrillGame(players: List<Player>, val targets: List<Int>) : DartGame<DrillState>(players) {
    init {
        require(targets.isNotEmpty()) { "a drill needs a target" }
        require(targets.all { it in 1..20 || it == 25 }) { "targets are the doubles 1 to 20 and the bull" }
    }

    val rounds: Int get() = targets.size * PASSES

    override fun initialState() = DrillState(players.map { 0 }, players.map { 0 })

    override fun withoutPlayer(state: DrillState, index: Int) =
        DrillState(state.hits.filterIndexed { i, _ -> i != index }, state.darts.filterIndexed { i, _ -> i != index })

    /** The double aimed at in [round]. */
    fun targetOf(round: Int): Int = targets[(round - 1).coerceAtLeast(0) % targets.size]

    /** The double aimed at by the next dart, or null when the drill is over. */
    val currentTarget: Int?
        get() {
            if (isFinished) return null
            val next = if (turnOver && isLastActivePlayerInRound(gameState, currentPlayerIndex)) round + 1 else round
            return next.takeIf { it <= rounds }?.let(::targetOf)
        }

    override fun onThrow(state: DrillState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<DrillState> {
        val scored = hit.isDouble && hit.base == targetOf(round)
        val darts = state.darts.toMutableList().also { it[player]++ }
        val hits = state.hits.toMutableList().also { if (scored) it[player]++ }
        return StepResult(DrillState(hits, darts), if (scored) Outcome.SCORED else Outcome.NO_SCORE)
    }

    override fun onTurnFinished(state: DrillState, player: Int, turn: List<DartHit>, round: Int): FinishResult<DrillState> {
        if (round < rounds || !isLastActivePlayerInRound(state, player)) return FinishResult(state)
        val best = state.hits.max()
        return FinishResult(state, state.hits.indices.filter { state.hits[it] == best })
    }

    companion object {
        const val PASSES = 2
    }
}