package com.opendarts.game.engine

import com.opendarts.geometry.Point

/**
 * What the app remembers about one dart of the turn on screen: where the camera saw it ([spot], board millimetres, null when it was
 * keyed in) and, when the player said so, which earlier dart it is hiding behind ([behind], an index into the turn).
 */
class DartTrace(val spot: Point?, val behind: Int? = null)

/**
 * The order the darts of the current turn arrived in, with where each one sits on the board. A dart that is hidden behind
 * another one cannot be seen, so this is how the app keeps both of them straight when the front dart is later removed.
 */
class TurnTrace {
    private val darts = ArrayList<DartTrace>()

    val size: Int get() = darts.size

    fun spotOf(index: Int): Point? = darts.getOrNull(index)?.spot

    fun behindOf(index: Int): Int? = darts.getOrNull(index)?.behind

    /** True when a dart the player reported as hidden is recorded behind dart [index]. */
    fun hasDartBehind(index: Int): Boolean = darts.any { it.behind == index }

    /** Makes the trace [size] darts long: unknown darts are added, extra ones dropped. */
    fun sync(size: Int) {
        while (darts.size > size) darts.removeAt(darts.lastIndex)
        while (darts.size < size) darts += DartTrace(null)
    }

    /** Dart number [turnSize] of the turn has just been thrown. */
    fun landed(turnSize: Int, trace: DartTrace) {
        sync(turnSize - 1)
        darts += trace
    }

    /**
     * Dart [index] was taken out of the turn. A dart recorded behind it is now the one in front, so it inherits its place on the
     * board (the camera keeps tracking that spot) and later "behind" references move up by one.
     */
    fun removed(index: Int) {
        val gone = darts.getOrNull(index) ?: return
        darts.removeAt(index)
        for (i in darts.indices) {
            val d = darts[i]
            val behind = d.behind ?: continue
            darts[i] = when {
                behind == index -> DartTrace(gone.spot)
                behind > index -> DartTrace(d.spot, behind - 1)
                else -> d
            }
        }
    }
}
