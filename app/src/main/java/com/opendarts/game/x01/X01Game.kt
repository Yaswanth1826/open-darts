package com.opendarts.game.x01

import com.opendarts.game.model.DartHit

/** Rules and state for a single-player X01 leg. UI code should call throwDart/undo. */
data class X01Config(
    val startingScore: Int = 501,
    val doubleIn: Boolean = false,
    val doubleOut: Boolean = true,
) {
    init { require(startingScore > 0) { "startingScore must be positive" } }
}

enum class ThrowStatus { SCORED, NOT_IN_YET, BUST, LEG_WON, LEG_ALREADY_FINISHED }

data class ThrowResult(
    val hit: DartHit,
    val status: ThrowStatus,
    val remaining: Int,
    val visitPoints: Int,
    val dartsInVisit: Int,
)

class X01Game(val config: X01Config = X01Config()) {
    var remaining: Int = config.startingScore
        private set
    var isIn: Boolean = !config.doubleIn
        private set
    var isFinished: Boolean = false
        private set
    var dartsThrown: Int = 0
        private set
    var legsWon: Int = 0
        private set
    private var visitStartScore = config.startingScore
    private var visitHits = mutableListOf<DartHit>()
    private val history = mutableListOf<Snapshot>()

    val currentVisitHits: List<DartHit> get() = visitHits.toList()
    val currentVisitPoints: Int get() = if (isIn) visitStartScore - remaining else 0
    val dartsInVisit: Int get() = visitHits.size
    val canUndo: Boolean get() = history.isNotEmpty()

    fun throwDart(hit: DartHit): ThrowResult {
        if (isFinished) return ThrowResult(hit, ThrowStatus.LEG_ALREADY_FINISHED, remaining, currentVisitPoints, dartsInVisit)
        if (visitHits.size == 3) beginNextVisit()
        history += snapshot()
        visitHits += hit
        dartsThrown++

        if (!isIn) {
            if (hit.isDouble) isIn = true else {
                if (visitHits.size == 3) beginNextVisit()
                return ThrowResult(hit, ThrowStatus.NOT_IN_YET, remaining, currentVisitPoints, dartsInVisit)
            }
        }

        val next = remaining - hit.points
        val wins = next == 0 && (!config.doubleOut || hit.isDouble)
        val bust = next < 0 || (config.doubleOut && next == 1) || (next == 0 && config.doubleOut && !hit.isDouble)
        val status = when {
            wins -> {
                remaining = 0
                isFinished = true
                legsWon++
                ThrowStatus.LEG_WON
            }
            bust -> {
                remaining = visitStartScore
                ThrowStatus.BUST
            }
            else -> {
                remaining = next
                ThrowStatus.SCORED
            }
        }
        if (status == ThrowStatus.BUST || status == ThrowStatus.LEG_WON || visitHits.size == 3) {
            if (status != ThrowStatus.LEG_WON) beginNextVisit()
        }
        return ThrowResult(hit, status, remaining, currentVisitPoints, dartsInVisit)
    }

    fun undo(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        restore(previous)
        return true
    }

    fun reset() {
        remaining = config.startingScore
        isIn = !config.doubleIn
        isFinished = false
        dartsThrown = 0
        legsWon = 0
        visitStartScore = config.startingScore
        visitHits.clear()
        history.clear()
    }

    private fun beginNextVisit() {
        visitHits.clear()
        visitStartScore = remaining
    }

    private data class Snapshot(
        val remaining: Int, val isIn: Boolean, val isFinished: Boolean,
        val dartsThrown: Int, val legsWon: Int, val visitStartScore: Int,
        val visitHits: List<DartHit>,
    )
    private fun snapshot() = Snapshot(remaining, isIn, isFinished, dartsThrown, legsWon, visitStartScore, visitHits.toList())
    private fun restore(s: Snapshot) {
        remaining = s.remaining; isIn = s.isIn; isFinished = s.isFinished
        dartsThrown = s.dartsThrown; legsWon = s.legsWon; visitStartScore = s.visitStartScore
        visitHits = s.visitHits.toMutableList()
    }
}
