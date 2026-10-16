package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

data class AroundTheClockConfig(
    /** Doubles skip one extra number and trebles skip two. */
    val skipOnMultiplier: Boolean = false,
    val doublesOnly: Boolean = false,
)

/** Targets are 1..20 then the bull. [targets] is the index of the next number to hit; 21 means finished. */
data class ClockState(val targets: List<Int>)

const val CLOCK_BULL_INDEX = 21

class AroundTheClockGame(players: List<Player>, val config: AroundTheClockConfig = AroundTheClockConfig()) :
    DartGame<ClockState>(players) {
    override fun initialState() = ClockState(players.map { 1 })

    fun targetNumber(index: Int): Int = if (index >= CLOCK_BULL_INDEX) 25 else index

    override fun onThrow(state: ClockState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<ClockState> {
        val current = state.targets[player]
        val wanted = targetNumber(current)
        val valid = hit.base == wanted && (!config.doublesOnly || hit.isDouble)
        if (!valid) return StepResult(state, Outcome.NO_SCORE)
        val step = if (config.skipOnMultiplier && wanted != 25) hit.multiplier.factor else 1
        val next = if (wanted == 25) CLOCK_BULL_INDEX + 1 else minOf(current + step, CLOCK_BULL_INDEX)
        val list = state.targets.toMutableList().also { it[player] = next }
        val done = next > CLOCK_BULL_INDEX
        return StepResult(ClockState(list), if (done) Outcome.GAME_WON else Outcome.SCORED, winners = if (done) listOf(player) else null)
    }
}