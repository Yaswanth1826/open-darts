package com.opendarts.game.engine

import com.opendarts.game.bot.DartBot
import com.opendarts.game.model.DartHit
import com.opendarts.geometry.BoardGeometry
import kotlin.math.hypot
import kotlin.random.Random

/** One dart thrown in a bull-off. The camera and the bot know where it landed ([x], [y] in mm from the centre of the bull); the keypad only knows what it hit. */
data class Toss(val hit: DartHit, val x: Double? = null, val y: Double? = null) {
    /** Millimetres from the centre of the bull, or null when only the ring is known. */
    val distance: Double? get() = if (x == null || y == null) null else hypot(x, y)
}

/** The bull-off that decides who throws first: everyone throws one dart at the bull and the closest starts. */
object BullOff {
    /** Darts closer together than this (mm) count as equally close. */
    private const val SAME = 0.1

    /** 2 for the bull, 1 for the outer bull, 0 for anything else (the keypad cannot tell how close a miss was). */
    fun rank(hit: DartHit): Int = when (hit.points) { 50 -> 2; 25 -> 1; else -> 0 }

    /** The players (by index) whose dart was closest; more than one means a tie. Uses exact distances when every dart has one, otherwise the ring that was hit. */
    fun leaders(tosses: Map<Int, Toss>): List<Int> {
        if (tosses.isEmpty()) return emptyList()
        val distances = tosses.mapValues { it.value.distance }
        if (distances.values.all { it != null }) {
            val best = distances.values.minOf { it!! }
            return distances.filter { it.value!! - best < SAME }.keys.sorted()
        }
        val best = tosses.values.maxOf { rank(it.hit) }
        return tosses.filter { rank(it.value.hit) == best }.keys.sorted()
    }

    /** The bot's dart at the bull, with where it landed. */
    fun botToss(average: Int, random: Random = Random.Default): Toss {
        val s = DartBot.sigma(average)
        val x = DartBot.gauss(random) * s
        val y = DartBot.gauss(random) * s
        return Toss(BoardGeometry.hitAt(x, y), x, y)
    }

    fun label(toss: Toss): String = toss.distance?.let { "${Math.round(it)} mm" } ?: when (rank(toss.hit)) { 2 -> "Bull"; 1 -> "25"; else -> "Miss" }
}

/** Where a bull-off stands. Immutable: every step returns the next state. */
data class BullOffState(
    val players: Int,
    val bot: Int = -1,
    /** Who is still in; after a tie only the tied players throw again. */
    val contenders: List<Int> = (0 until players).toList(),
    val tosses: Map<Int, Toss> = emptyMap(),
    val round: Int = 1,
    /** Chosen by the players after a tie they could not separate. */
    val picked: Int? = null,
) {
    /** The player who throws next, or null once everyone has thrown. */
    val next: Int? get() = if (picked != null) null else contenders.firstOrNull { it !in tosses }

    private val allThrown: Boolean get() = contenders.all { it in tosses }

    val leaders: List<Int> get() = if (allThrown) BullOff.leaders(tosses) else emptyList()

    /** Who throws first, once it is settled. */
    val winner: Int? get() = picked ?: leaders.singleOrNull()

    val tied: Boolean get() = picked == null && allThrown && leaders.size > 1

    fun thrown(player: Int, toss: Toss): BullOffState = if (player != next) this else copy(tosses = tosses + (player to toss))

    /** The tied players throw again. */
    fun again(): BullOffState = if (!tied) this else copy(contenders = leaders, tosses = emptyMap(), round = round + 1)

    /** The players agreed that [player], one of those tied, was closer. */
    fun closer(player: Int): BullOffState = if (tied && player in leaders) copy(picked = player) else this

    /** Takes back the latest dart. */
    fun undone(): BullOffState = when {
        picked != null -> copy(picked = null)
        tosses.isEmpty() -> this
        else -> copy(tosses = tosses - tosses.keys.last())
    }

    /** The camera saw the dart that landed at ([fromX], [fromY]) get nudged to [to]; it replaces that player's toss. */
    fun moved(fromX: Double, fromY: Double, to: Toss): BullOffState {
        val who = tosses.entries.filter { it.value.x != null }.minByOrNull { hypot(it.value.x!! - fromX, it.value.y!! - fromY) } ?: return this
        if (hypot(who.value.x!! - fromX, who.value.y!! - fromY) > 12.0) return this
        return copy(tosses = tosses + (who.key to to))
    }
}