package com.opendarts.game.x01

/** What a player did over a finished (or running) X01 match, ready to show. */
data class X01Summary(
    val name: String,
    val average: Double,
    val highestVisit: Int,
    val darts: Int,
    val hundredPlus: Int,
    val oneFortyPlus: Int,
    val oneEighties: Int,
    /** Share of finishing chances that were taken, in whole percent, or null if the player never had one. */
    val checkoutPercent: Int?,
    val legs: Int,
    val sets: Int,
    val won: Boolean,
)

fun X01Game.summaries(): List<X01Summary> = players.mapIndexed { i, player ->
    val p = gameState.players[i]
    X01Summary(
        name = player.name,
        average = p.average,
        highestVisit = p.highestVisit,
        darts = p.totalDarts,
        hundredPlus = p.hundredPlus,
        oneFortyPlus = p.oneFortyPlus,
        oneEighties = p.oneEighties,
        checkoutPercent = if (p.finishingDarts == 0) null else Math.round(p.checkoutHits * 100.0 / p.finishingDarts).toInt().coerceAtMost(100),
        legs = p.legs,
        sets = p.sets,
        won = winners.orEmpty().contains(i),
    )
}
