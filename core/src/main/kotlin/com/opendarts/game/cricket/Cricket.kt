package com.opendarts.game.cricket

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

enum class CricketMode { STANDARD, CUT_THROAT, NO_SCORE }

data class CricketConfig(val mode: CricketMode = CricketMode.STANDARD)

data class CricketPlayer(val marks: Map<Int, Int> = emptyMap(), val points: Int = 0) {
    fun marksOn(number: Int) = marks[number] ?: 0
    fun isClosed(number: Int) = marksOn(number) >= 3
    val allClosed: Boolean get() = CRICKET_NUMBERS.all { isClosed(it) }
}

data class CricketState(val players: List<CricketPlayer>)

val CRICKET_NUMBERS: List<Int> = listOf(20, 19, 18, 17, 16, 15, 25)

class CricketGame(players: List<Player>, val config: CricketConfig = CricketConfig()) : DartGame<CricketState>(players) {
    override fun initialState() = CricketState(players.map { CricketPlayer() })

    override fun onThrow(state: CricketState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<CricketState> {
        if (hit.base !in CRICKET_NUMBERS) return StepResult(state, Outcome.NO_SCORE)

        val list = state.players.toMutableList()
        val me = list[player]
        val before = me.marksOn(hit.base)
        val total = before + hit.multiplier.factor
        val extra = (total - maxOf(before, 3)).coerceAtLeast(0)
        list[player] = me.copy(marks = me.marks + (hit.base to total))

        var outcome = if (total > before) Outcome.SCORED else Outcome.NO_SCORE
        if (extra > 0 && config.mode != CricketMode.NO_SCORE) {
            val value = extra * hit.base
            val others = list.indices.filter { it != player }
            when (config.mode) {
                CricketMode.STANDARD -> if (others.any { !list[it].isClosed(hit.base) }) {
                    list[player] = list[player].copy(points = list[player].points + value)
                }
                CricketMode.CUT_THROAT -> others.filter { !list[it].isClosed(hit.base) }.forEach {
                    list[it] = list[it].copy(points = list[it].points + value)
                }
                CricketMode.NO_SCORE -> {}
            }
        }

        val next = CricketState(list)
        val winner = if (list[player].allClosed && leads(next, player)) listOf(player) else null
        if (winner != null) outcome = Outcome.GAME_WON
        return StepResult(next, outcome, winners = winner)
    }

    private fun leads(state: CricketState, player: Int): Boolean {
        val me = state.players[player].points
        val others = state.players.filterIndexed { i, _ -> i != player }.map { it.points }
        return when (config.mode) {
            CricketMode.STANDARD -> others.all { me >= it }
            CricketMode.CUT_THROAT -> others.all { me <= it }
            CricketMode.NO_SCORE -> true
        }
    }
}