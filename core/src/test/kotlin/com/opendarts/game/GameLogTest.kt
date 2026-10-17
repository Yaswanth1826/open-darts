package com.opendarts.game

import com.opendarts.game.cricket.CricketGame
import com.opendarts.game.engine.GameAction
import com.opendarts.game.engine.GameLog
import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.x01.X01Config
import com.opendarts.game.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameLogTest {
    private val players = listOf(Player(0, "A"), Player(1, "B"))

    private fun play(game: X01Game, log: GameLog) {
        fun throwIt(hit: DartHit) { game.throwDart(hit); log.add(GameAction.Throw(hit)) }
        throwIt(DartHit.treble(20)); throwIt(DartHit.single(5)); throwIt(DartHit.double(10))
        game.endTurn(); log.add(GameAction.NextTurn)
        throwIt(DartHit.MISS); throwIt(DartHit.INNER_BULL)
        game.undo(); log.add(GameAction.Undo)
        game.editDart(0, DartHit.single(1)); log.add(GameAction.Edit(0, DartHit.single(1)))
        game.editDart(0, null); log.add(GameAction.Edit(0, null))
        throwIt(DartHit.treble(19))
        game.previousPlayer(); log.add(GameAction.Previous)
        game.editDart(2, DartHit.treble(20)); log.add(GameAction.Edit(2, DartHit.treble(20)))
        game.endTurn(); log.add(GameAction.NextTurn)
    }

    @Test
    fun replayingTheLogRebuildsTheSameGame() {
        val original = X01Game(players, X01Config(301))
        val log = GameLog()
        play(original, log)

        val copy = X01Game(players, X01Config(301))
        GameLog.decode(log.encode())!!.replay(copy)

        assertEquals(original.gameState, copy.gameState)
        assertEquals(original.currentPlayerIndex, copy.currentPlayerIndex)
        assertEquals(original.turnHits, copy.turnHits)
        assertEquals(original.round, copy.round)
        assertEquals(original.canUndo, copy.canUndo)
    }

    @Test
    fun aDartThrownAfterTheTurnEndedHandsOverOnReplayToo() {
        val original = X01Game(players, X01Config(301))
        val log = GameLog()
        listOf(DartHit.single(1), DartHit.single(2), DartHit.single(3), DartHit.single(4)).forEach { original.throwDart(it); log.add(GameAction.Throw(it)) }
        original.undo(); log.add(GameAction.Undo)
        val copy = X01Game(players, X01Config(301))
        log.replay(copy)
        assertEquals(original.currentPlayerIndex, copy.currentPlayerIndex)
        assertEquals(original.turnHits, copy.turnHits)
        assertEquals(original.gameState, copy.gameState)
    }

    @Test
    fun worksForOtherGamesToo() {
        val log = GameLog(listOf(GameAction.Throw(DartHit.treble(20)), GameAction.Throw(DartHit.single(19)), GameAction.NextTurn))
        val a = CricketGame(players)
        val b = CricketGame(players)
        log.replay(a)
        GameLog.decode(log.encode())!!.replay(b)
        assertEquals(a.gameState, b.gameState)
        assertEquals(1, b.currentPlayerIndex)
    }

    @Test
    fun damagedTextIsRejected() {
        assertNull(GameLog.decode("T,99.7"))
        assertNull(GameLog.decode("Q"))
        assertEquals(0, GameLog.decode("")!!.size)
    }
}