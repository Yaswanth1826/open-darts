package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

data class BobsPlayer(val score: Int = Bobs27Game.START, val out: Boolean = false)

data class BobsState(val players: List<BobsPlayer>)

/**
 * Bob's 27, a doubles drill. Everyone starts on 27. Round 1 aims at D1, round 2 at D2 and so on up to D20. Every dart that
 * lands in the round's double adds twice its number; a visit with no hit takes that amount away. Dropping below zero puts a
 * player out. When round 20 is done the highest score wins.
 */
class Bobs27Game(players: List<Player>) : DartGame<BobsState>(players) {
    override fun initialState() = BobsState(players.map { BobsPlayer() })

    override fun withoutPlayer(state: BobsState, index: Int) = BobsState(state.players.filterIndexed { i, _ -> i != index })

    override fun isActive(state: BobsState, player: Int) = !state.players[player].out

    /** The double aimed at for the next dart, or null when the game is over. */
    val currentTarget: Int?
        get() {
            if (isFinished) return null
            val next = if (turnOver && isLastActivePlayerInRound(gameState, currentPlayerIndex)) round + 1 else round
            return next.takeIf { it <= ROUNDS }
        }

    override fun onThrow(state: BobsState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<BobsState> {
        if (hit.base != round || !hit.isDouble) return StepResult(state, Outcome.NO_SCORE)
        val list = state.players.toMutableList().also { it[player] = it[player].copy(score = it[player].score + 2 * round) }
        return StepResult(BobsState(list), Outcome.SCORED)
    }

    override fun onTurnFinished(state: BobsState, player: Int, turn: List<DartHit>, round: Int): FinishResult<BobsState> {
        var updated = state
        if (turn.none { it.base == round && it.isDouble }) {
            val p = state.players[player]
            val score = p.score - 2 * round
            updated = BobsState(state.players.toMutableList().also { it[player] = BobsPlayer(score, out = score < 0) })
        }
        val finished = updated.players.none { !it.out } || (round >= ROUNDS && isLastActivePlayerInRound(updated, player))
        if (!finished) return FinishResult(updated)
        val pool = updated.players.indices.filter { !updated.players[it].out }.ifEmpty { updated.players.indices.toList() }
        val best = pool.maxOf { updated.players[it].score }
        return FinishResult(updated, pool.filter { updated.players[it].score == best })
    }

    companion object {
        const val ROUNDS = 20
        const val START = 27
    }
}
