package com.opendarts.game.practice

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import kotlin.random.Random

/** How hard the numbers to check out are. */
enum class CheckoutLevel(val label: String, val range: IntRange) {
    EASY("Easy", 2..60),
    MEDIUM("Medium", 61..110),
    HARD("Hard", 111..170),
}

data class CheckoutPracticeConfig(val level: CheckoutLevel = CheckoutLevel.MEDIUM, val attempts: Int = 10, val seed: Int = 0) {
    init { require(attempts >= 1) { "attempts must be at least 1" } }
}

data class CheckoutPracticeState(val checkouts: List<Int>)

/**
 * Finishing practice. Each round gives every player the same number to check out (double out) in three darts.
 * A checkout scores one point; running out of darts or going bust scores nothing. Most checkouts after the set number of rounds wins.
 * The numbers come from [CheckoutPracticeConfig.seed], so a saved game replays with the same numbers.
 */
class CheckoutPracticeGame(players: List<Player>, val config: CheckoutPracticeConfig = CheckoutPracticeConfig()) :
    DartGame<CheckoutPracticeState>(players) {
    private val numbers: List<Int> by lazy {
        config.level.range.filter { Checkout.suggest(it, InOut.DOUBLE, 3) != null }.shuffled(Random(config.seed))
    }

    override fun initialState() = CheckoutPracticeState(players.map { 0 })

    override fun withoutPlayer(state: CheckoutPracticeState, index: Int) =
        CheckoutPracticeState(state.checkouts.filterIndexed { i, _ -> i != index })

    /** The number to check out in [round]. */
    fun targetFor(round: Int): Int = numbers[(round - 1) % numbers.size]

    /** The number the next dart of the visit on screen is aimed at, or null when the game is over. */
    val remaining: Int?
        get() {
            if (isFinished) return null
            val nextRound = if (turnOver && currentPlayerIndex == players.lastIndex) round + 1 else round
            if (nextRound > config.attempts) return null
            return if (turnOver) targetFor(nextRound) else targetFor(round) - turnHits.sumOf { it.points }
        }

    override fun onThrow(state: CheckoutPracticeState, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<CheckoutPracticeState> {
        val left = targetFor(round) - turnBefore.sumOf { it.points } - hit.points
        return when {
            left == 0 && hit.isDouble ->
                StepResult(CheckoutPracticeState(state.checkouts.toMutableList().also { it[player]++ }), Outcome.LEG_WON, turnOver = true)
            left <= 1 -> StepResult(state, Outcome.BUST, turnOver = true)
            else -> StepResult(state, if (hit.points > 0) Outcome.SCORED else Outcome.NO_SCORE)
        }
    }

    override fun onTurnFinished(state: CheckoutPracticeState, player: Int, turn: List<DartHit>, round: Int): FinishResult<CheckoutPracticeState> {
        if (round < config.attempts || player != players.lastIndex) return FinishResult(state)
        val best = state.checkouts.max()
        return FinishResult(state, state.checkouts.indices.filter { state.checkouts[it] == best })
    }
}
