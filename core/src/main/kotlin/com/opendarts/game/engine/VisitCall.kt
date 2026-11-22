package com.opendarts.game.engine

import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.Checkout
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

    /**
     * Said as a player steps up: what they need to finish the leg, e.g. "Ann requires 121". Only when they are in the game and
     * the score can be finished with three darts; null otherwise (far from a finish, a score nobody can check out, or not X01).
     */
    fun requirement(game: DartGame<*>): String? {
        if (game !is X01Game) return null
        val p = game.gameState.players[game.currentPlayerIndex]
        if (!p.isIn || p.remaining <= 0 || Checkout.suggest(p.remaining, game.config.outMode) == null) return null
        return "${game.currentPlayer.name} requires ${p.remaining}"
    }

    private const val NO_SCORE = "No score"

    private fun total(points: Int) = when (points) {
        0 -> NO_SCORE
        180 -> "One hundred and eighty"
        else -> points.toString()
    }
}
