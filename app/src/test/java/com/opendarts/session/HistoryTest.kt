package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private class Memory : MatchHistory {
        val records = ArrayList<MatchRecord>()
        override fun all(): List<MatchRecord> = records.toList()
        override fun add(record: MatchRecord) { records.removeAll { it.id == record.id }; records.add(0, record) }
        override fun remove(id: Long) { records.removeAll { it.id == id } }
        override fun clear() = records.clear()
    }

    private val setup = GameSetup(playerNames = listOf("Ann & Bo", "C|y,;"), x01 = X01Config(40, InOut.STRAIGHT, InOut.DOUBLE), input = InputMode.MANUAL)

    private fun finished(book: Memory): GameSession {
        val s = GameSession().also { it.history = book; it.start(setup) }
        s.throwDart(DartHit.single(20)); s.throwDart(DartHit.single(1)); s.throwDart(DartHit.single(1))
        s.nextPlayer()
        s.throwDart(DartHit.double(20))
        return s
    }

    @Test
    fun aFinishedGameIsRecordedOnceWithTheFiguresOfEachPlayer() {
        val book = Memory()
        val s = finished(book)
        assertTrue(s.game!!.isFinished)
        assertEquals(1, book.records.size)
        val r = book.records.single()
        assertEquals(GameKind.X01, r.kind)
        assertEquals(listOf("Ann & Bo", "C|y,;"), r.players.map { it.name })
        assertEquals(listOf("C|y,;"), r.winners.map { it.name })
        assertEquals(3, r.players[0].darts)
        assertEquals(22, r.players[0].points)
        assertEquals(40, r.players[1].points)
        assertEquals(100, r.players[1].checkoutPercent)
        s.undo(); s.throwDart(DartHit.double(20))
        assertEquals(1, book.records.size)
    }

    @Test
    fun anUndoneWinIsTakenBackOutOfTheHistory() {
        val book = Memory()
        val s = finished(book)
        s.undo()
        assertTrue(book.records.isEmpty())
        s.throwDart(DartHit.double(20))
        assertEquals(1, book.records.size)
    }

    @Test
    fun aGameThatIsNotFinishedIsNotRecordedAndARematchIsAGameOfItsOwn() {
        val book = Memory()
        val s = GameSession().also { it.history = book; it.start(setup) }
        s.throwDart(DartHit.treble(20))
        assertTrue(book.records.isEmpty())
        s.leave()
        val t = finished(book)
        Thread.sleep(3)
        t.rematch()
        t.throwDart(DartHit.double(20))
        assertEquals(2, book.records.size)
    }

    @Test
    fun aRecordSurvivesBeingWrittenAsText() {
        val book = Memory()
        finished(book)
        val r = book.records.single()
        assertEquals(r, HistoryCodec.decode(HistoryCodec.encode(r)))
        assertNull(HistoryCodec.decode("garbage"))
        assertNull(HistoryCodec.decode(""))
    }

    @Test
    fun figuresAreAddedUpOverAllGamesAndLongGamesCountForMore() {
        fun record(id: Long, kind: GameKind, vararg p: MatchPlayer) = MatchRecord(id, kind, "x", InputMode.MANUAL, 3, p.toList())
        val records = listOf(
            record(1, GameKind.X01, MatchPlayer("Ann", true, darts = 30, points = 600, bestVisit = 140, oneEighties = 0, finishingDarts = 4, checkoutHits = 1), MatchPlayer("Bo", false, darts = 30, points = 300)),
            record(2, GameKind.X01, MatchPlayer("Ann", false, darts = 3, points = 180, bestVisit = 180, oneEighties = 1, finishingDarts = 0, checkoutHits = 0)),
            record(3, GameKind.CRICKET, MatchPlayer("Bo", true), MatchPlayer("Ann", false)),
        )
        val stats = records.playerStats()
        val ann = stats.first { it.name == "Ann" }
        assertEquals(3, ann.games)
        assertEquals(1, ann.wins)
        assertEquals(2, ann.x01Games)
        assertEquals((600 + 180) * 3.0 / 33, ann.average, 1e-9)
        assertEquals(180, ann.bestVisit)
        assertEquals(1, ann.oneEighties)
        assertEquals(25, ann.checkoutPercent)
        assertEquals(listOf("Ann", "Bo"), stats.map { it.name })
        assertNull(stats.first { it.name == "Bo" }.checkoutPercent)
    }

    @Test
    fun aBotIsListedInTheGameButHasNoFiguresOfItsOwn() {
        val book = Memory()
        val s = GameSession().also { it.history = book; it.start(setup.copy(playerNames = listOf("Ann", "Bot Club"), botLevel = 60)) }
        s.throwDart(DartHit.double(20)); s.throwDart(DartHit.single(20))
        val record = MatchRecord(1, GameKind.X01, "40", InputMode.MANUAL, 2, listOf(MatchPlayer("Ann", true, darts = 33, points = 400), MatchPlayer("Bot Club", false, darts = 33, points = 300, bot = true)))
        assertEquals(listOf("Ann"), listOf(record).playerStats().map { it.name })
        assertEquals(record, HistoryCodec.decode(HistoryCodec.encode(record)))
        assertTrue(HistoryCodec.decode(HistoryCodec.encode(record))!!.players[1].bot)
    }

    @Test
    fun aRecordSavedBeforeBotsExistedStillLoads() {
        val record = MatchRecord(1, GameKind.X01, "501", InputMode.MANUAL, 2, listOf(MatchPlayer("Ann", true, darts = 33, points = 400)))
        val old = HistoryCodec.encode(record).replace(",false;", ";").removeSuffix(",false")
        assertEquals(record, HistoryCodec.decode(old))
    }

    @Test
    fun theAverageNeedsEnoughDartsToMeanAnything() {
        val few = listOf(MatchRecord(1, GameKind.X01, "501", InputMode.MANUAL, 2, listOf(MatchPlayer("Ann", true, darts = 12, points = 400))))
        assertNull(few.x01Average("Ann"))
        val many = listOf(MatchRecord(1, GameKind.X01, "501", InputMode.MANUAL, 2, listOf(MatchPlayer("Ann", true, darts = 30, points = 520))))
        assertEquals(52, many.x01Average("Ann"))
        assertNull(many.x01Average("Bo"))
    }
}
