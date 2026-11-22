package com.opendarts.session

import com.opendarts.game.engine.DartGame
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.game.practice.TargetDrillGame
import com.opendarts.game.x01.X01Game

/**
 * Where a player's darts landed, one character per dart so a whole game stays a few dozen bytes in the history.
 * A miss is "0", then the numbers 1 to 20 each as single, double, treble, then the outer bull and the bull.
 */
object ShotCodes {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"

    fun encode(hit: DartHit): Char {
        val code = when {
            hit.base == 0 -> 0
            hit.base == 25 -> if (hit.multiplier == Multiplier.DOUBLE) 62 else 61
            else -> 1 + (hit.base - 1) * 3 + hit.multiplier.ordinal
        }
        return ALPHABET[code]
    }

    fun decode(c: Char): DartHit? {
        val code = ALPHABET.indexOf(c)
        return when {
            code < 0 -> null
            code == 0 -> DartHit.MISS
            code == 61 -> DartHit.OUTER_BULL
            code == 62 -> DartHit.INNER_BULL
            code > 62 -> null
            else -> DartHit((code - 1) / 3 + 1, Multiplier.entries[(code - 1) % 3])
        }
    }

    fun decode(text: String): List<DartHit> = text.mapNotNull { decode(it) }
}

/** The per-dart figures of one player in one game, worked out from the game's [DartGame.trail]. */
data class ShotDetail(
    /** Every dart, see [ShotCodes]. */
    val shots: String = "",
    /** X01 finishing attempts and Weak spots darts per double as "double-attempts-hits" groups joined by dots; the bull is 25. */
    val doubles: String = "",
    /** Points and darts in the first nine darts of every leg. */
    val first9Points: Int = 0,
    val first9Darts: Int = 0,
) {
    companion object {
        fun of(game: DartGame<*>): List<ShotDetail> {
            val shots = List(game.players.size) { StringBuilder() }
            for (dart in game.trail) shots[dart.player].append(ShotCodes.encode(dart.hit))

            val doubles = List(game.players.size) { sortedMapOf<Int, IntArray>() }
            val first9Points = IntArray(game.players.size)
            val first9Darts = IntArray(game.players.size)
            fun tried(player: Int, double: Int, hit: Boolean) {
                val counts = doubles[player].getOrPut(double) { IntArray(2) }
                counts[0]++
                if (hit) counts[1]++
            }
            if (game is TargetDrillGame) {
                for (dart in game.trail) tried(dart.player, game.targetOf(dart.round), dart.hit.isDouble && dart.hit.base == game.targetOf(dart.round))
            }
            if (game is X01Game) {
                val thrownInLeg = HashMap<Pair<Int, Int>, Int>()
                for (dart in game.trail) {
                    val before = dart.before.players[dart.player]
                    val after = dart.after.players[dart.player]
                    val double = doubleFor(before.remaining)
                    if (before.isIn && double != null) tried(dart.player, double, after.checkoutHits > before.checkoutHits)
                    val leg = dart.before.legsPlayed
                    val thrown = thrownInLeg.merge(dart.player to leg, 1, Int::plus)!!
                    if (thrown <= 9) {
                        first9Darts[dart.player]++
                        first9Points[dart.player] += after.totalPoints - before.totalPoints
                    }
                }
            }
            return game.players.indices.map { i ->
                ShotDetail(shots[i].toString(), doubles[i].entries.joinToString(".") { (d, c) -> "$d-${c[0]}-${c[1]}" }, first9Points[i], first9Darts[i])
            }
        }

        /** The double that finishes [remaining] in one dart, or null when no double does. */
        private fun doubleFor(remaining: Int): Int? = when {
            remaining == 50 -> 25
            remaining in 2..40 && remaining % 2 == 0 -> remaining / 2
            else -> null
        }

        fun parseDoubles(text: String): Map<Int, Pair<Int, Int>> = text.split('.').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('-')
            if (p.size == 3) p[0].toInt() to (p[1].toInt() to p[2].toInt()) else null
        }.toMap()
    }
}
