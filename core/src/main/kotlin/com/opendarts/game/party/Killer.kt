package com.opendarts.game.party

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import kotlin.random.Random

data class KillerState(val numbers: List<Int>, val lives: List<Int>, val killers: List<Boolean>) {
    fun alive(player: Int) = lives[player] > 0
}

/**
 * Killer, for two or more players. Every player gets their own number (drawn from [seed], so a saved game replays the same).
 * Hit the double of your own number to become a killer. A killer who hits the double of another player's number takes one
 * of that player's lives; at zero they are out. The last player standing wins.
 */
class KillerGame(players: List<Player>, val seed: Int = 0, val startLives: Int = 3) : DartGame<KillerState>(players) {
    init { require(players.size >= 2) { "Killer needs at least two players" } }

    override fun initialState() =
        KillerState((1..20).shuffled(Random(seed)).take(players.size), players.map { startLives }, players.map { false })

    override fun withoutPlayer(state: KillerState, index: Int) = KillerState(
        state.numbers.filterIndexed { i, _ -> i != index },
        state.lives.filterIndexed { i, _ -> i != index },
        state.killers.filterIndexed { i, _ -> i != index },
    )

    override fun isActive(state: KillerState, player: Int) = state.alive(player)

    override fun onThrow(state: KillerState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<KillerState> {
        if (!hit.isDouble) return StepResult(state, Outcome.NO_SCORE)
        if (!state.killers[player]) {
            if (hit.base != state.numbers[player]) return StepResult(state, Outcome.NO_SCORE)
            return StepResult(state.copy(killers = state.killers.toMutableList().also { it[player] = true }), Outcome.SPECIAL)
        }
        val victim = state.numbers.indices.firstOrNull { it != player && state.alive(it) && state.numbers[it] == hit.base }
            ?: return StepResult(state, Outcome.NO_SCORE)
        val lives = state.lives.toMutableList().also { it[victim]-- }
        val next = state.copy(lives = lives)
        val standing = lives.indices.filter { lives[it] > 0 }
        return when {
            standing.size == 1 -> StepResult(next, Outcome.GAME_WON, winners = standing)
            lives[victim] == 0 -> StepResult(next, Outcome.ELIMINATED)
            else -> StepResult(next, Outcome.SCORED)
        }
    }
}
