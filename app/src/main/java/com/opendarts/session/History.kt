package com.opendarts.session

import android.content.Context
import com.opendarts.game.engine.DartGame
import com.opendarts.game.x01.X01Game
import java.net.URLDecoder
import java.net.URLEncoder

/** What one player did in a finished game. The X01 figures are zero in the other games. */
data class MatchPlayer(
    val name: String,
    val won: Boolean,
    val darts: Int = 0,
    val points: Int = 0,
    val bestVisit: Int = 0,
    val hundredPlus: Int = 0,
    val oneFortyPlus: Int = 0,
    val oneEighties: Int = 0,
    val finishingDarts: Int = 0,
    val checkoutHits: Int = 0,
    val legs: Int = 0,
    val sets: Int = 0,
    /** A computer opponent: it is listed in the game but has no figures of its own. */
    val bot: Boolean = false,
    /** Where every dart landed and the finishing and first-nine figures built from them. */
    val detail: ShotDetail = ShotDetail(),
) {
    /** Three-dart average. */
    val average: Double get() = if (darts == 0) 0.0 else points * 3.0 / darts
    val checkoutPercent: Int? get() = if (finishingDarts == 0) null else Math.round(checkoutHits * 100.0 / finishingDarts).toInt().coerceAtMost(100)
}

/** A finished game, kept small: who played, who won and the X01 figures. [id] is the time it started, in milliseconds. */
data class MatchRecord(
    val id: Long,
    val kind: GameKind,
    val title: String,
    val input: InputMode,
    val rounds: Int,
    val players: List<MatchPlayer>,
) {
    val winners: List<MatchPlayer> get() = players.filter { it.won }

    companion object {
        fun of(id: Long, setup: GameSetup, game: DartGame<*>): MatchRecord {
            val x01 = (game as? X01Game)?.gameState?.players
            val won = game.winners.orEmpty()
            val details = ShotDetail.of(game)
            val players = game.players.mapIndexed { i, player ->
                val s = x01?.getOrNull(i)
                MatchPlayer(
                    name = player.name, won = i in won,
                    darts = s?.totalDarts ?: 0, points = s?.totalPoints ?: 0, bestVisit = s?.highestVisit ?: 0,
                    hundredPlus = s?.hundredPlus ?: 0, oneFortyPlus = s?.oneFortyPlus ?: 0, oneEighties = s?.oneEighties ?: 0,
                    finishingDarts = s?.finishingDarts ?: 0, checkoutHits = s?.checkoutHits ?: 0, legs = s?.legs ?: 0, sets = s?.sets ?: 0, bot = player.isBot, detail = details[i],
                )
            }
            return MatchRecord(id, setup.kind, setup.title, setup.input, game.round, players)
        }
    }
}

/** A player's figures added up over every finished game they played in. */
data class PlayerStats(
    val name: String,
    val games: Int,
    val wins: Int,
    val x01Games: Int,
    val darts: Int,
    val points: Int,
    val bestVisit: Int,
    val hundredPlus: Int,
    val oneFortyPlus: Int,
    val oneEighties: Int,
    val finishingDarts: Int,
    val checkoutHits: Int,
) {
    /** Three-dart X01 average over all darts, so a long game counts for more than a short one. */
    val average: Double get() = if (darts == 0) 0.0 else points * 3.0 / darts
    val checkoutPercent: Int? get() = if (finishingDarts == 0) null else Math.round(checkoutHits * 100.0 / finishingDarts).toInt().coerceAtMost(100)
}

/** One entry per player name, the one with most games first. */
fun List<MatchRecord>.playerStats(): List<PlayerStats> {
    val byName = LinkedHashMap<String, MutableList<Pair<MatchRecord, MatchPlayer>>>()
    for (record in this) for (p in record.players.filter { !it.bot }) byName.getOrPut(p.name.trim()) { ArrayList() } += record to p
    return byName.map { (name, games) ->
        val x01 = games.filter { it.first.kind == GameKind.X01 }.map { it.second }
        PlayerStats(
            name = name, games = games.size, wins = games.count { it.second.won }, x01Games = x01.size,
            darts = x01.sumOf { it.darts }, points = x01.sumOf { it.points }, bestVisit = x01.maxOfOrNull { it.bestVisit } ?: 0,
            hundredPlus = x01.sumOf { it.hundredPlus }, oneFortyPlus = x01.sumOf { it.oneFortyPlus }, oneEighties = x01.sumOf { it.oneEighties },
            finishingDarts = x01.sumOf { it.finishingDarts }, checkoutHits = x01.sumOf { it.checkoutHits },
        )
    }.sortedByDescending { it.games }
}

/** A player's X01 three-dart average over the games in this list, rounded, or null when they have played too little for it to mean anything. */
fun List<MatchRecord>.x01Average(name: String): Int? {
    val stats = playerStats().firstOrNull { it.name == name.trim() } ?: return null
    return if (stats.darts >= 30) Math.round(stats.average).toInt() else null
}

/** Where finished games are kept. */
interface MatchHistory {
    /** Newest first. */
    fun all(): List<MatchRecord>
    fun add(record: MatchRecord)
    fun remove(id: Long)
    fun clear()
}

/** A [MatchRecord] as one line of text; every field is URL-encoded, so the separators can never appear inside one. */
object HistoryCodec {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    fun encode(r: MatchRecord): String {
        val players = r.players.joinToString(";") { p ->
            listOf(enc(p.name), p.won, p.darts, p.points, p.bestVisit, p.hundredPlus, p.oneFortyPlus, p.oneEighties, p.finishingDarts, p.checkoutHits, p.legs, p.sets, p.bot, p.detail.shots, p.detail.doubles, p.detail.first9Points, p.detail.first9Darts).joinToString(",")
        }
        return listOf(r.id.toString(), r.kind.name, enc(r.title), r.input.name, r.rounds.toString(), players).joinToString("|")
    }

    fun decode(line: String): MatchRecord? = runCatching {
        val f = line.split('|')
        val players = f[5].split(';').filter { it.isNotEmpty() }.map { text ->
            val p = text.split(',')
            MatchPlayer(dec(p[0]), p[1].toBoolean(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toInt(), p[8].toInt(), p[9].toInt(), p[10].toInt(), p[11].toInt(), p.getOrNull(12).toBoolean(),
                detail = ShotDetail(p.getOrNull(13).orEmpty(), p.getOrNull(14).orEmpty(), p.getOrNull(15)?.toIntOrNull() ?: 0, p.getOrNull(16)?.toIntOrNull() ?: 0))
        }
        MatchRecord(f[0].toLong(), GameKind.valueOf(f[1]), dec(f[2]), InputMode.valueOf(f[3]), f[4].toInt(), players)
    }.getOrNull()
}

/** Keeps the finished games in the app's preferences, one line each, the newest [MAX] only. */
class HistoryStore(context: Context) : MatchHistory {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    private fun read(): List<MatchRecord> = prefs.getString("records", null).orEmpty().lines().mapNotNull { HistoryCodec.decode(it) }

    private fun write(records: List<MatchRecord>) {
        prefs.edit().putString("records", records.take(MAX).joinToString("\n") { HistoryCodec.encode(it) }).apply()
    }

    override fun all() = read()

    override fun add(record: MatchRecord) = write(listOf(record) + read().filter { it.id != record.id })

    override fun remove(id: Long) = write(read().filter { it.id != id })

    override fun clear() = write(emptyList())

    companion object { const val MAX = 300 }
}
