package com.opendarts.game.engine

import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.X01Game

/** What is said out loud when a visit (the player's three darts) ends. Only games where the visit total matters have a call. */
object VisitCall {
    /**
     * The call for the visit that has just ended in [game], or null when this game has none. [outcome] is what the last dart
     * did; pass null when the visit was cut short (the player left after fewer than three darts).
     */
    fun of(game: DartGame<*>, outcome: Outcome?): String? {
        val hits = game.turnHits
        return when (game) {
            is X01Game -> when {
                hits.isEmpty() -> NO_SCORE
                outcome == Outcome.BUST -> "Bust"
                outcome == Outcome.LEG_WON || outcome == Outcome.SET_WON || outcome == Outcome.GAME_WON -> "Game shot"
                else -> total(game.gameState.players[game.currentPlayerIndex].turnPoints)
            }
            is CountUpGame -> total(hits.sumOf { it.points })
            else -> null
        }
    }

    private const val NO_SCORE = "No score"

    private fun total(points: Int) = when (points) {
        0 -> NO_SCORE
        180 -> "One hundred and eighty"
        else -> points.toString()
    }
}
