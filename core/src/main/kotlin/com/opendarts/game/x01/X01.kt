package com.opendarts.game.x01

import com.opendarts.game.engine.DartGame
import com.opendarts.game.engine.AdvanceResult
import com.opendarts.game.engine.FinishResult
import com.opendarts.game.engine.Outcome
import com.opendarts.game.engine.StepResult
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player

enum class InOut { STRAIGHT, DOUBLE, MASTER }

data class X01Config(
    val startingScore: Int = 501,
    val inMode: InOut = InOut.STRAIGHT,
    val outMode: InOut = InOut.DOUBLE,
    /** Legs needed to win a set (or the whole match when [setsToWin] is 1). */
    val legsToWin: Int = 1,
    val setsToWin: Int = 1,
) {
    init {
        require(startingScore > 1) { "startingScore must be greater than 1" }
        require(legsToWin >= 1 && setsToWin >= 1) { "legsToWin and setsToWin must be at least 1" }
    }
}

data class X01Player(
    val remaining: Int,
    val isIn: Boolean,
    val legs: Int = 0,
    val sets: Int = 0,
    val turnStart: Int = remaining,
    val turnStartIn: Boolean = isIn,
    val turnPoints: Int = 0,
    val totalPoints: Int = 0,
    val totalDarts: Int = 0,
    val highestVisit: Int = 0,
    val checkoutHits: Int = 0,
    /** Darts thrown when one dart could have finished the leg. */
    val finishingDarts: Int = 0,
    /** Visits that scored 100 or more, 140 or more, and exactly 180. */
    val hundredPlus: Int = 0,
    val oneFortyPlus: Int = 0,
    val oneEighties: Int = 0,
) {
    /** Three-dart average over the whole match. */
    val average: Double get() = if (totalDarts == 0) 0.0 else totalPoints * 3.0 / totalDarts
}

data class X01State(
    val players: List<X01Player>,
    val legStarter: Int = 0,
    val legsPlayed: Int = 0,
    val legPending: Boolean = false,
)

class X01Game(players: List<Player>, val config: X01Config = X01Config()) : DartGame<X01State>(players) {
    constructor(config: X01Config = X01Config()) : this(listOf(Player(0, "Player 1")), config)

    override fun initialState() = X01State(players.map { fresh() })

    private fun fresh() = X01Player(config.startingScore, config.inMode == InOut.STRAIGHT)

    private fun counts(mode: InOut, hit: DartHit) = when (mode) {
        InOut.STRAIGHT -> true
        InOut.DOUBLE -> hit.isDouble
        InOut.MASTER -> hit.isDouble || hit.isTreble
    }

    override fun onThrow(state: X01State, player: Int, hit: DartHit, turnBefore: List<DartHit>, round: Int): StepResult<X01State> {
        var p = state.players[player]
        if (turnBefore.isEmpty()) p = p.copy(turnStart = p.remaining, turnStartIn = p.isIn, turnPoints = 0)
        p = p.copy(totalDarts = p.totalDarts + 1)
        if (p.isIn && canFinishWithOneDart(p.remaining)) p = p.copy(finishingDarts = p.finishingDarts + 1)

        fun commit(np: X01Player) = state.copy(players = state.players.toMutableList().also { it[player] = np })

        if (!p.isIn) {
            if (!counts(config.inMode, hit)) return StepResult(commit(p), Outcome.NOT_IN)
            p = p.copy(isIn = true)
        }

        val next = p.remaining - hit.points
        val needsFinishDart = config.outMode != InOut.STRAIGHT
        val bust = next < 0 || (needsFinishDart && next == 1) || (next == 0 && !counts(config.outMode, hit))
        if (bust) {
            val reverted = p.copy(
                remaining = p.turnStart, isIn = p.turnStartIn,
                totalPoints = p.totalPoints - p.turnPoints, turnPoints = 0,
            )
            return StepResult(commit(reverted), Outcome.BUST, turnOver = true)
        }

        val turnPoints = p.turnPoints + hit.points
        p = p.copy(remaining = next, turnPoints = turnPoints, totalPoints = p.totalPoints + hit.points,
            highestVisit = maxOf(p.highestVisit, turnPoints))
        if (next > 0) return StepResult(commit(p), Outcome.SCORED)

        p = p.copy(checkoutHits = p.checkoutHits + 1, legs = p.legs + 1)
        if (p.legs < config.legsToWin) {
            return StepResult(commit(p).copy(legPending = true), Outcome.LEG_WON, turnOver = true)
        }
        p = p.copy(sets = p.sets + 1)
        if (p.sets < config.setsToWin) {
            val cleared = state.players.map { it.copy(legs = 0) }.toMutableList().also { it[player] = p.copy(legs = 0) }
            return StepResult(state.copy(players = cleared, legPending = true), Outcome.SET_WON, turnOver = true)
        }
        return StepResult(commit(p), Outcome.GAME_WON, turnOver = true, winners = listOf(player))
    }

    override fun onTurnFinished(state: X01State, player: Int, turn: List<DartHit>, round: Int): FinishResult<X01State> {
        val p = state.players[player]
        val points = p.turnPoints
        if (points < 100) return FinishResult(state)
        val counted = p.copy(
            hundredPlus = p.hundredPlus + 1,
            oneFortyPlus = p.oneFortyPlus + if (points >= 140) 1 else 0,
            oneEighties = p.oneEighties + if (points == 180) 1 else 0,
        )
        return FinishResult(state.copy(players = state.players.toMutableList().also { it[player] = counted }))
    }

    private fun canFinishWithOneDart(remaining: Int) = FINISHING_DARTS.any { it.points == remaining && counts(config.outMode, it) }

    override fun onAdvance(state: X01State, round: Int): AdvanceResult<X01State> {
        if (!state.legPending) return AdvanceResult(state)
        val legsPlayed = state.legsPlayed + 1
        val starter = (state.legStarter + 1) % players.size
        val reset = state.players.map { it.copy(remaining = config.startingScore, isIn = config.inMode == InOut.STRAIGHT,
            turnStart = config.startingScore, turnStartIn = config.inMode == InOut.STRAIGHT, turnPoints = 0) }
        return AdvanceResult(X01State(reset, starter, legsPlayed), nextPlayer = starter, newRound = 1)
    }
}

private val FINISHING_DARTS: List<DartHit> =
    (1..20).flatMap { listOf(DartHit.single(it), DartHit.double(it), DartHit.treble(it)) } + DartHit.OUTER_BULL + DartHit.INNER_BULL
