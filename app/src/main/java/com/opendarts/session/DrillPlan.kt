package com.opendarts.session

/** Which doubles the Weak spots drill aims at. */
object DrillPlan {
    const val SIZE = 6

    /** The doubles that finish most legs, used to fill up when there is too little to go on. */
    val FALLBACK = listOf(20, 16, 8, 10, 12, 18, 4, 14)

    fun label(double: Int) = if (double == 25) "Bull" else "D$double"

    /** The targets and how many of them come from the player's own weakest doubles. */
    class Plan(val targets: List<Int>, val personal: Int)

    fun plan(records: List<MatchRecord>, name: String?): Plan {
        val weak = name?.let { records.advancedStats(it) }?.weakestDoubles()?.map { it.double }.orEmpty().take(SIZE)
        return Plan((weak + FALLBACK).distinct().take(SIZE), weak.size)
    }
}