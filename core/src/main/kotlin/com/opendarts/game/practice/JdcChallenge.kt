package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.game.model.Player

/** Which section of the challenge a dart belongs to. */
enum class JdcPart { SHANGHAI_LOW, DOUBLES, SHANGHAI_HIGH }

/** The number being aimed at (25 for the bull) and whether only its double counts. */
data class JdcTarget(val part: JdcPart, val number: Int, val doubleOnly: Boolean) {
    val label: String get() = when {
        number == 25 -> "Bull"
        doubleOnly -> "D$number"
        else -> number.toString()
    }
}

data class JdcPlayerState(
    val shanghaiLow: Int = 0,
    val doubles: Int = 0,
    val shanghaiHigh: Int = 0,
    val shanghais: Int = 0,
) {
    val total: Int get() = shanghaiLow + doubles + shanghaiHigh
    fun part(part: JdcPart): Int = when (part) {
        JdcPart.SHANGHAI_LOW -> shanghaiLow
        JdcPart.DOUBLES -> doubles
        JdcPart.SHANGHAI_HIGH -> shanghaiHigh
    }
}

data class JdcState(val players: List<JdcPlayerState>)

/**
 * The JDC Challenge: 19 three-dart rounds (57 darts).
 *  - Rounds 1-6: 10 to 15, three darts each. Only the target number scores (at face value) and a single,
 *    double and treble of it in one visit earns a 100 bonus.
 *  - Rounds 7-13: one dart at each double D1-D20 then the bull. A double scores 50, the inner bull 100.
 *  - Rounds 14-19: 15 to 20, scored like the first section.
 * Highest total wins.
 */
class JdcChallengeGame(players: List<Player>) : DartGame<JdcState>(players) {
    override fun initialState() = JdcState(players.map { JdcPlayerState() })

    /** The target for the [dartIndex]-th dart (0-based) of the given round. */
    fun targetFor(round: Int, dartIndex: Int): JdcTarget = when (round) {
        in 1..6 -> JdcTarget(JdcPart.SHANGHAI_LOW, 9 + round, false)
        in 7..13 -> {
            val n = (round - 7) * 3 + dartIndex + 1
            JdcTarget(JdcPart.DOUBLES, if (n > 20) 25 else n, true)
        }
        else -> JdcTarget(JdcPart.SHANGHAI_HIGH, round + 1, false)
    }

    /** The target the next dart is aimed at, or null when the game is over. */
    val currentTarget: JdcTarget?
        get() {
            if (isFinished) return null
            val nextRound = if (turnOver && currentPlayerIndex == players.lastIndex) round + 1 else round
            return if (nextRound > TOTAL_ROUNDS) null else targetFor(nextRound, if (turnOver) 0 else turnHits.size)
        }

    override fun onThrow(state: JdcState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<JdcState> {
        val target = targetFor(round, turnBefore.size)
        var gained = 0
        var shanghai = false
        when (target.part) {
            JdcPart.DOUBLES -> if (target.number == 25) {
                if (hit.base == 25 && hit.isDouble) gained = 100
            } else if (hit.base == target.number && hit.isDouble) gained = 50
            else -> {
                if (hit.base == target.number) gained = hit.points
                val onTarget = (turnBefore + hit).filter { it.base == target.number }.map { it.multiplier }.toSet()
                if (hit.base == target.number && onTarget.size == Multiplier.values().size) {
                    gained += SHANGHAI_BONUS
                    shanghai = true
                }
            }
        }
        val old = state.players[player]
        val updated = when (target.part) {
            JdcPart.SHANGHAI_LOW -> old.copy(shanghaiLow = old.shanghaiLow + gained)
            JdcPart.DOUBLES -> old.copy(doubles = old.doubles + gained)
            JdcPart.SHANGHAI_HIGH -> old.copy(shanghaiHigh = old.shanghaiHigh + gained)
        }.let { if (shanghai) it.copy(shanghais = it.shanghais + 1) else it }
        val list = state.players.toMutableList().also { it[player] = updated }
        val outcome = when {
            shanghai -> Outcome.SPECIAL
            gained > 0 -> Outcome.SCORED
            else -> Outcome.NO_SCORE
        }
        return StepResult(JdcState(list), outcome)
    }

    override fun onTurnFinished(state: JdcState, player: Int, turn: List<DartHit>, round: Int): FinishResult<JdcState> {
        if (round < TOTAL_ROUNDS || player != players.lastIndex) return FinishResult(state)
        val best = state.players.maxOf { it.total }
        return FinishResult(state, state.players.indices.filter { state.players[it].total == best })
    }

    companion object {
        const val TOTAL_ROUNDS = 19
        const val SHANGHAI_BONUS = 100
    }
}