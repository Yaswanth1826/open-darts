package com.opendarts.game.bot

import com.opendarts.game.cricket.CRICKET_NUMBERS
import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.engine.DartGame
import com.opendarts.game.model.DartHit
import com.opendarts.game.practice.AroundTheClockGame
import com.opendarts.game.practice.CountUpGame
import com.opendarts.game.x01.Checkout
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Game
import com.opendarts.geometry.BoardGeometry
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** A skill the player can pick: the three-dart X01 average the computer plays at. */
data class BotLevel(val average: Int, val label: String)

/**
 * A computer opponent. It decides what to aim at like a sensible player would (scoring, setting up a double, finishing,
 * closing cricket numbers) and then misses the aim by a random distance on the board, larger for weaker levels.
 */
object DartBot {
    val LEVELS: List<BotLevel> = listOf(
        BotLevel(30, "Rookie"), BotLevel(45, "Casual"), BotLevel(60, "Club"),
        BotLevel(75, "League"), BotLevel(90, "Pro"), BotLevel(100, "Elite"),
    )

    fun levelName(average: Int): String = LEVELS.firstOrNull { it.average == average }?.label ?: "Avg $average"

    /** Standard deviation (mm, each direction) of how far a dart lands from the aim, for a bot with this X01 average. */
    fun sigma(average: Int): Double {
        val points = SIGMA_BY_AVERAGE
        if (average <= points.first().first) return points.first().second
        if (average >= points.last().first) return points.last().second
        val i = points.indexOfLast { it.first <= average }
        val (a0, s0) = points[i]
        val (a1, s1) = points[i + 1]
        return s0 + (s1 - s0) * (average - a0) / (a1 - a0)
    }

    /** The games a bot can play. */
    fun supports(game: DartGame<*>): Boolean = game is X01Game || game is CricketGame || game is AroundTheClockGame || game is CountUpGame

    /** What the player to throw would aim at, or null when the bot cannot play this game. */
    fun aim(game: DartGame<*>): DartHit? = when (game) {
        is X01Game -> x01Aim(game)
        is CricketGame -> cricketAim(game)
        is AroundTheClockGame -> clockAim(game)
        is CountUpGame -> DartHit.treble(20)
        else -> null
    }

    /** One dart thrown by the bot at [average] for the player to throw. */
    fun throwDart(game: DartGame<*>, average: Int, random: Random = Random.Default): DartHit {
        val aim = aim(game) ?: return DartHit.MISS
        return land(aim, average, random)
    }

    fun land(aim: DartHit, average: Int, random: Random): DartHit = land(aim, sigma(average), random)

    /** Where a dart aimed at [aim] lands when it misses by a random distance with standard deviation [s] (mm). */
    fun land(aim: DartHit, s: Double, random: Random): DartHit {
        val centre = BoardGeometry.centreOf(aim)
        return BoardGeometry.hitAt(centre.x + gauss(random) * s, centre.y + gauss(random) * s)
    }

    internal fun gauss(random: Random): Double {
        val u = random.nextDouble().coerceAtLeast(1e-12)
        return sqrt(-2.0 * ln(u)) * cos(2 * Math.PI * random.nextDouble())
    }

    private val finishingDoubles = listOf(20, 16, 8, 10, 12, 18, 6, 4, 14, 2, 19, 17, 15, 13, 11, 9, 7, 5, 3, 1)

    private val setupDarts: List<DartHit> = (20 downTo 1).map { DartHit.treble(it) } + (20 downTo 1).map { DartHit.single(it) } + DartHit.OUTER_BULL + DartHit.INNER_BULL

    private fun x01Aim(game: X01Game): DartHit {
        val p = game.gameState.players[game.currentPlayerIndex]
        val config = game.config
        if (!p.isIn) return if (config.inMode == InOut.DOUBLE) DartHit.double(20) else DartHit.treble(20)
        val left = game.dartsLeftInTurn.coerceAtLeast(1)
        val remaining = p.remaining
        Checkout.suggest(remaining, config.outMode, left)?.let { return it.first() }
        if (remaining > 170) return DartHit.treble(20)
        return setupDarts
            .filter { hit -> hit.points <= remaining - (if (config.outMode == InOut.STRAIGHT) 0 else 2) && hit.points < remaining }
            .minByOrNull { hit -> leaveCost(Checkout.suggest(remaining - hit.points, config.outMode, 3), hit) }
            ?: DartHit.treble(20)
    }

    /** Fewer darts to finish is better; a one-dart finish is better the easier its double. A treble is a smaller target than a single. */
    private fun leaveCost(route: List<DartHit>?, aim: DartHit): Int {
        val size = route?.size ?: 9
        val finish = route?.takeIf { it.size == 1 }?.first()
        val rank = when {
            finish == null -> 0
            finish.isBull -> 6
            else -> finishingDoubles.indexOf(finish.base).coerceAtLeast(0)
        }
        val difficulty = when { aim.isTreble -> 15; aim.isBull -> 25; else -> 0 }
        return size * 1000 + rank * 10 + difficulty
    }

    private fun cricketAim(game: CricketGame): DartHit {
        val state = game.gameState
        val me = state.players[game.currentPlayerIndex]
        val others = state.players.indices.filter { it != game.currentPlayerIndex }
        val target = CRICKET_NUMBERS.firstOrNull { !me.isClosed(it) }
            ?: CRICKET_NUMBERS.firstOrNull { n -> others.any { !state.players[it].isClosed(n) } }
            ?: 20
        return if (target == 25) DartHit.INNER_BULL else DartHit.treble(target)
    }

    private fun clockAim(game: AroundTheClockGame): DartHit {
        val number = game.targetNumber(game.gameState.targets[game.currentPlayerIndex])
        return when {
            number == 25 -> DartHit.INNER_BULL
            game.config.doublesOnly -> DartHit.double(number)
            game.config.skipOnMultiplier -> DartHit.treble(number)
            else -> DartHit.single(number)
        }
    }

    /** (X01 average, sigma) pairs found by simulation (see DartBotTest). Below 30 and above 100 the nearest value is used.. */
    private val SIGMA_BY_AVERAGE: List<Pair<Int, Double>> = listOf(
        30 to 28.2, 45 to 17.4, 60 to 12.7, 75 to 9.7, 90 to 7.45, 100 to 6.25,
    )
}
