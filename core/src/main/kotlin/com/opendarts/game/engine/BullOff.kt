package com.opendarts.game.engine

import com.opendarts.game.bot.DartBot
import com.opendarts.game.model.DartHit
import kotlin.random.Random

/** The bull-off that decides who throws first: everyone throws one dart at the bull and the closest starts. */
object BullOff {
    /** 2 for the bull, 1 for the outer bull, 0 for anything else (the keypad cannot tell how close a miss was). */
    fun rank(hit: DartHit): Int = when (hit.points) { 50 -> 2; 25 -> 1; else -> 0 }

    /** The players (by index) whose dart was closest; more than one means a tie. */
    fun leaders(results: Map<Int, DartHit>): List<Int> {
        if (results.isEmpty()) return emptyList()
        val best = results.values.maxOf(::rank)
        return results.filter { rank(it.value) == best }.keys.sorted()
    }

    /** The bot's dart at the bull. */
    fun botDart(average: Int, random: Random = Random.Default): DartHit = DartBot.land(DartHit.INNER_BULL, average, random)

    fun label(hit: DartHit): String = when (rank(hit)) { 2 -> "Bull"; 1 -> "25"; else -> "Miss" }
}