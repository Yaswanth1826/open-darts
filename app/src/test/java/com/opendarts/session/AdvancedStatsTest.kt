package com.opendarts.session

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import com.opendarts.game.model.Player
import com.opendarts.game.x01.InOut
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedStatsTest {
    private fun game(config: X01Config) = X01Game(listOf(Player(0, "Ann"), Player(1, "Bo")), config)
    private val quick = X01Config(40, InOut.STRAIGHT, InOut.DOUBLE)

    @Test fun everyPossibleDartSurvivesBeingWrittenAsOneCharacter() {
        val all = listOf(DartHit.MISS, DartHit.OUTER_BULL, DartHit.INNER_BULL) + (1..20).flatMap { n -> Multiplier.entries.map { DartHit(n, it) } }
        val text = all.map(ShotCodes::encode).joinToString("")
        assertEquals(all.size, text.toSet().size)
        assertEquals(all, ShotCodes.decode(text))
        assertTrue(text.none { it in ",;|=&% " })
        assertEquals(emptyList<DartHit>(), ShotCodes.decode("?!"))
    }

    @Test fun theDartsAndTheTriesAtADoubleAreWorkedOutForEachPlayer() {
        val g = game(quick)
        g.throwDart(DartHit.single(20)); g.throwDart(DartHit.single(1)); g.throwDart(DartHit.single(1))
        g.throwDart(DartHit.double(20))
        val (ann, bo) = ShotDetail.of(g)
        assertEquals(listOf(DartHit.single(20), DartHit.single(1), DartHit.single(1)), ShotCodes.decode(ann.shots))
        assertEquals(mapOf(10 to (1 to 0), 20 to (1 to 0)), ShotDetail.parseDoubles(ann.doubles))
        assertEquals(mapOf(20 to (1 to 1)), ShotDetail.parseDoubles(bo.doubles))
        assertEquals(3 to 22, ann.first9Darts to ann.first9Points)
        assertEquals(1 to 40, bo.first9Darts to bo.first9Points)
    }

    @Test fun onlyTheFirstNineDartsOfALegCount() {
        val g = game(X01Config(501))
        repeat(4) { visit ->
            repeat(3) { g.throwDart(DartHit.single(20)) }
            repeat(3) { g.throwDart(DartHit.single(1)) }
        }
        val (ann, bo) = ShotDetail.of(g)
        assertEquals(9 to 180, ann.first9Darts to ann.first9Points)
        assertEquals(9 to 9, bo.first9Darts to bo.first9Points)
        assertEquals(12, ann.shots.length)
    }

    @Test fun everyLegHasItsOwnFirstNine() {
        val g = game(X01Config(40, InOut.STRAIGHT, InOut.DOUBLE, legsToWin = 2))
        g.throwDart(DartHit.double(20)); g.throwDart(DartHit.double(20)); g.throwDart(DartHit.double(20))
        val (ann, bo) = ShotDetail.of(g)
        assertEquals(2 to 80, ann.first9Darts to ann.first9Points)
        assertEquals(1 to 40, bo.first9Darts to bo.first9Points)
    }

    @Test fun aBustScoresNothingInTheFirstNine() {
        val g = game(X01Config(40, InOut.STRAIGHT, InOut.DOUBLE))
        g.throwDart(DartHit.single(20)); g.throwDart(DartHit.single(20))
        val ann = ShotDetail.of(g)[0]
        assertEquals(2 to 0, ann.first9Darts to ann.first9Points)
    }

    @Test fun aDartTakenBackIsNotCounted() {
        val g = game(quick)
        g.throwDart(DartHit.single(5)); g.throwDart(DartHit.single(7))
        g.undo()
        assertEquals(listOf(DartHit.single(5)), ShotCodes.decode(ShotDetail.of(g)[0].shots))
    }

    @Test fun otherGamesKeepTheirDartsToo() {
        val g = com.opendarts.game.cricket.CricketGame(listOf(Player(0, "Ann"), Player(1, "Bo")))
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.MISS)
        val ann = ShotDetail.of(g)[0]
        assertEquals(listOf(DartHit.treble(20), DartHit.MISS), ShotCodes.decode(ann.shots))
        assertEquals("", ann.doubles)
    }

    private fun record(id: Long, vararg players: MatchPlayer) = MatchRecord(id, GameKind.X01, "501", InputMode.MANUAL, 5, players.toList())

    private fun player(name: String, shots: List<DartHit>, doubles: String = "", f9d: Int = 0, f9p: Int = 0, darts: Int = 30, points: Int = 300) =
        MatchPlayer(name, false, darts = darts, points = points, detail = ShotDetail(shots.map(ShotCodes::encode).joinToString(""), doubles, f9p, f9d))

    @Test fun theFiguresAddUpOverEveryGame() {
        val records = listOf(
            record(2, player("Ann", listOf(DartHit.treble(20), DartHit.double(16)), "16-4-1.20-2-2", 9, 90, points = 450)),
            record(1, player("Ann", listOf(DartHit.treble(20), DartHit.single(3)), "16-2-1", 9, 180)),
        )
        val s = records.advancedStats("Ann")!!
        assertEquals(4, s.darts)
        assertEquals(2, s.heat[DartHit.treble(20)])
        assertEquals(2, s.trebles); assertEquals(1, s.doubleHits)
        assertEquals(45.0, s.first9!!, 0.001)
        assertEquals(listOf(DoubleFigure(16, 6, 2), DoubleFigure(20, 2, 2)), s.doubles)
        assertEquals(listOf(DoubleFigure(16, 6, 2)), s.weakestDoubles())
        assertEquals(33, s.doubles[0].percent)
        assertEquals(listOf(30.0, 45.0), s.trend)
    }

    @Test fun aPlayerWithoutRecordedDartsHasNoAdvancedStatsAndABotNever() {
        val records = listOf(record(1, MatchPlayer("Ann", true, darts = 30, points = 300), MatchPlayer("Bot Club", false, bot = true, detail = ShotDetail("K"))))
        assertNull(records.advancedStats("Ann"))
        assertNull(records.advancedStats("Bot Club"))
        assertNull(records.advancedStats("Nobody"))
    }

    @Test fun theDetailSurvivesBeingSavedAndAnOldRecordLoadsWithoutIt() {
        val r = record(1, player("Ann", listOf(DartHit.treble(20), DartHit.MISS), "20-2-1", 6, 100))
        assertEquals(r, HistoryCodec.decode(HistoryCodec.encode(r)))
        val old = HistoryCodec.encode(r).split('|').let { f -> (f.take(5) + f[5].split(',').take(13).joinToString(",")).joinToString("|") }
        assertEquals(ShotDetail(), HistoryCodec.decode(old)!!.players[0].detail)
    }

    @Test fun aFinishedGameKeepsTheDartsOfItsPlayers() {
        val book = object : MatchHistory {
            val list = ArrayList<MatchRecord>()
            override fun all() = list.toList()
            override fun add(record: MatchRecord) { list.add(0, record) }
            override fun remove(id: Long) {}
            override fun clear() = list.clear()
        }
        val s = GameSession().also { it.history = book; it.start(GameSetup(playerNames = listOf("Ann", "Bo"), x01 = quick, input = InputMode.MANUAL)) }
        s.throwDart(DartHit.single(20)); s.throwDart(DartHit.single(1)); s.throwDart(DartHit.single(1))
        s.nextPlayer()
        s.throwDart(DartHit.double(20))
        val r = book.list.single()
        assertEquals(3, r.players[0].detail.shots.length)
        assertEquals("20-1-1", r.players[1].detail.doubles)
        assertNotNull(book.list.advancedStats("Bo"))
    }

    @Test fun theSpreadsheetHasOneRowPerPlayerAndNeverRunsAFormula() {
        val csv = listOf(record(0, MatchPlayer("=SUM(1,2)", true, darts = 30, points = 300), MatchPlayer("Bo, Jr", false))).toCsv().lines().filter { it.isNotEmpty() }
        assertEquals(3, csv.size)
        assertTrue(csv[0].startsWith("date,game"))
        assertTrue(csv[1].contains("\"''=SUM(1,2)\"") || csv[1].contains("'=SUM(1,2)"))
        assertFalse(csv[1].split(',').any { it.startsWith("=") })
        assertTrue(csv[2].contains("\"Bo, Jr\""))
    }
}