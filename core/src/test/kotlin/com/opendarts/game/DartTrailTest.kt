package com.opendarts.game

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Player
import com.opendarts.game.x01.X01Game
import org.junit.Assert.assertEquals
import org.junit.Test

class DartTrailTest {
    private fun game() = X01Game(listOf(Player(0, "A"), Player(1, "B")))

    @Test fun everyDartIsListedWithItsThrowerInOrder() {
        val g = game()
        g.throwDart(DartHit.treble(20)); g.throwDart(DartHit.single(5)); g.throwDart(DartHit.single(1))
        g.throwDart(DartHit.double(16))
        assertEquals(listOf(0 to DartHit.treble(20), 0 to DartHit.single(5), 0 to DartHit.single(1), 1 to DartHit.double(16)), g.trail.map { it.player to it.hit })
    }

    @Test fun eachDartShowsTheGameBeforeAndAfter() {
        val g = game()
        g.throwDart(DartHit.treble(20))
        val d = g.trail.single()
        assertEquals(501, d.before.players[0].remaining)
        assertEquals(441, d.after.players[0].remaining)
    }

    @Test fun anUndoneDartIsGoneAndAnEditedOneShowsAsEdited() {
        val g = game()
        g.throwDart(DartHit.single(20)); g.throwDart(DartHit.single(5))
        g.undo()
        assertEquals(listOf(DartHit.single(20)), g.trail.map { it.hit })
        g.editDart(0, DartHit.treble(20))
        assertEquals(listOf(DartHit.treble(20)), g.trail.map { it.hit })
    }

    @Test fun aNewGameHasNoDarts() {
        assertEquals(0, game().trail.size)
    }
}