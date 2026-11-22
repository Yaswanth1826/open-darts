package com.opendarts.session

import com.opendarts.game.model.DartHit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How often one double finished a leg when a player had the chance to. */
data class DoubleFigure(val double: Int, val attempts: Int, val hits: Int) {
    val percent: Int get() = Math.round(hits * 100.0 / attempts).toInt().coerceAtMost(100)
    val label: String get() = if (double == 25) "Bull" else "D$double"
}

/** The figures that need every dart of every game, so they are a premium feature. */
data class AdvancedStats(
    val name: String,
    /** Darts recorded, in all games that kept them. */
    val darts: Int,
    /** How often each dart landed, in any game. */
    val heat: Map<DartHit, Int>,
    /** Three-dart average over the first nine darts of every X01 leg, or null with too few darts. */
    val first9: Double?,
    /** Doubles with at least one attempt, the most tried first. */
    val doubles: List<DoubleFigure>,
    /** The X01 average of each game, oldest first, the last [TREND_GAMES] only. */
    val trend: List<Double>,
) {
    val trebles: Int get() = heat.filterKeys { it.isTreble }.values.sum()
    val doubleHits: Int get() = heat.filterKeys { it.isDouble }.values.sum()

    /** The doubles with at least [minAttempts] tries, worst first. */
    fun weakestDoubles(minAttempts: Int = 3): List<DoubleFigure> = doubles.filter { it.attempts >= minAttempts }.sortedWith(compareBy({ it.percent }, { -it.attempts }))

    companion object { const val TREND_GAMES = 20 }
}

/** The advanced figures of the player called [name], or null when none of their games kept any darts. */
fun List<MatchRecord>.advancedStats(name: String): AdvancedStats? {
    val mine = flatMap { r -> r.players.filter { !it.bot && it.name.trim() == name.trim() }.map { r to it } }
    val heat = HashMap<DartHit, Int>()
    var darts = 0
    for ((_, p) in mine) for (hit in ShotCodes.decode(p.detail.shots)) { heat.merge(hit, 1, Int::plus); darts++ }
    if (darts == 0) return null

    val doubles = HashMap<Int, IntArray>()
    for ((_, p) in mine) for ((d, c) in ShotDetail.parseDoubles(p.detail.doubles)) {
        val total = doubles.getOrPut(d) { IntArray(2) }
        total[0] += c.first; total[1] += c.second
    }
    val firstDarts = mine.sumOf { it.second.detail.first9Darts }
    val firstPoints = mine.sumOf { it.second.detail.first9Points }
    val trend = mine.filter { it.first.kind == GameKind.X01 && it.second.darts >= 9 }.sortedBy { it.first.id }
        .map { it.second.average }.takeLast(AdvancedStats.TREND_GAMES)
    return AdvancedStats(
        name = name.trim(), darts = darts, heat = heat,
        first9 = if (firstDarts >= 9) firstPoints * 3.0 / firstDarts else null,
        doubles = doubles.map { (d, c) -> DoubleFigure(d, c[0], c[1]) }.sortedWith(compareBy({ -it.attempts }, { it.double })),
        trend = trend,
    )
}

/** Every finished game as CSV, one row per player, for a spreadsheet. */
fun List<MatchRecord>.toCsv(): String {
    // A leading = + - or @ would run as a formula in a spreadsheet, so such text gets a quote mark in front.
    fun cell(raw: String): String {
        val s = if (raw.firstOrNull() in setOf('=', '+', '-', '@')) "'" + raw else raw
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val rows = sortedBy { it.id }.flatMap { r ->
        r.players.map { p ->
            listOf(
                date.format(Date(r.id)), r.kind.title, r.title, if (r.input == InputMode.CAMERA) "camera" else "keypad", p.name, if (p.bot) "bot" else "player",
                if (p.won) "yes" else "no", p.darts, p.points, "%.2f".format(Locale.US, p.average), p.bestVisit, p.hundredPlus, p.oneFortyPlus, p.oneEighties,
                p.checkoutPercent ?: "",
            ).joinToString(",") { cell(it.toString()) }
        }
    }
    return (listOf("date,game,title,scoring,player,type,won,darts,points,average,best_visit,100plus,140plus,180s,checkout_percent") + rows).joinToString("\n") + "\n"
}
