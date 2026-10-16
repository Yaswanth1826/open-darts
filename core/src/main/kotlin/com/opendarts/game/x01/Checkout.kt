package com.opendarts.game.x01

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier

/** Finishing suggestions for X01. */
object Checkout {
    private val doublePreference = listOf(20, 16, 8, 10, 12, 18, 6, 4, 14, 2, 19, 17, 15, 13, 11, 9, 7, 5, 3, 1)

    private val finishers: Map<InOut, List<DartHit>> = mapOf(
        InOut.DOUBLE to (doublePreference.map { DartHit.double(it) } + DartHit.INNER_BULL),
        InOut.MASTER to (doublePreference.map { DartHit.double(it) } + DartHit.INNER_BULL + (1..20).reversed().map { DartHit.treble(it) }),
    )

    private val setup: List<DartHit> = buildList {
        for (n in 20 downTo 1) add(DartHit.treble(n))
        add(DartHit.INNER_BULL)
        for (n in 20 downTo 1) add(DartHit.single(n))
        add(DartHit.OUTER_BULL)
    }

    /** Shortest route (at most [darts] darts) that checks out [remaining], or null when none exists. */
    fun suggest(remaining: Int, outMode: InOut = InOut.DOUBLE, darts: Int = 3): List<DartHit>? {
        if (remaining < 1 || darts < 1) return null
        val ends = finishers[outMode] ?: return if (remaining <= 60 && darts >= 1) straight(remaining, darts) else null
        for (count in 1..darts) {
            val route = search(remaining, count, ends)
            if (route != null) return route
        }
        return null
    }

    private fun search(remaining: Int, count: Int, ends: List<DartHit>): List<DartHit>? {
        if (count == 1) return ends.firstOrNull { it.points == remaining }?.let { listOf(it) }
        for (first in setup) {
            if (first.points >= remaining - 1) continue
            val rest = search(remaining - first.points, count - 1, ends) ?: continue
            return listOf(first) + rest
        }
        return null
    }

    private fun straight(remaining: Int, darts: Int): List<DartHit>? {
        val all = setup.sortedByDescending { it.points }
        for (count in 1..darts) {
            fun go(left: Int, n: Int): List<DartHit>? {
                if (n == 1) return all.firstOrNull { it.points == left }?.let { listOf(it) }
                for (h in all) if (h.points < left) go(left - h.points, n - 1)?.let { return listOf(h) + it }
                return null
            }
            go(remaining, count)?.let { return it }
        }
        return null
    }

    fun format(route: List<DartHit>): String = route.joinToString(" ") { it.toString() }
}