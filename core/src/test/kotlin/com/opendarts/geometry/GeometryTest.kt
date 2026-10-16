package com.opendarts.geometry

import com.opendarts.game.model.DartHit
import com.opendarts.game.model.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    @Test fun everyScoringAreaRoundTripsThroughItsCentre() {
        val hits = buildList {
            for (n in 1..20) for (m in Multiplier.values()) add(DartHit(n, m))
            add(DartHit.OUTER_BULL); add(DartHit.INNER_BULL); add(DartHit.MISS)
        }
        hits.forEach { assertEquals(it.toString(), it, BoardGeometry.hitAt(BoardGeometry.centreOf(it))) }
    }

    @Test fun knownCoordinates() {
        assertEquals(DartHit.treble(20), BoardGeometry.hitAt(0.0, 103.0))
        assertEquals(DartHit.double(6), BoardGeometry.hitAt(166.0, 0.0))
        assertEquals(DartHit.single(3), BoardGeometry.hitAt(0.0, -60.0))
        assertEquals(DartHit.single(11), BoardGeometry.hitAt(-60.0, 0.0))
        assertEquals(DartHit.MISS, BoardGeometry.hitAt(0.0, 171.0))
        assertEquals(DartHit.INNER_BULL, BoardGeometry.hitAt(2.0, 2.0))
    }

    @Test fun wiresSplitNeighbouringSegments() {
        assertEquals(20, BoardGeometry.hitAt(15.0, 100.0).base)
        assertEquals(1, BoardGeometry.hitAt(17.0, 100.0).base)
    }

    @Test fun homographyRecoversAPerspectiveView() {
        val camera = listOf(Point(310.0, 120.0), Point(900.0, 180.0), Point(950.0, 720.0), Point(260.0, 640.0))
        val board = listOf(Point(-170.0, 170.0), Point(170.0, 170.0), Point(170.0, -170.0), Point(-170.0, -170.0))
        val h = Homography.fromFourPoints(camera, board)
        camera.zip(board).forEach { (c, b) ->
            val p = h.apply(c)
            assertEquals(b.x, p.x, 1e-6); assertEquals(b.y, p.y, 1e-6)
        }
        val back = h.inverse().apply(Point(0.0, 0.0))
        val again = h.apply(back)
        assertEquals(0.0, again.x, 1e-6); assertEquals(0.0, again.y, 1e-6)
    }

    @Test fun calibrationPointsSitOnTheWires() {
        val p = BoardGeometry.CALIBRATION_POINTS
        assertEquals(4, p.size)
        // 20|1 wire is 9 degrees clockwise from the top
        assertEquals(Math.sin(Math.toRadians(9.0)) * 170.0, p[0].x, 1e-9)
        assertEquals(Math.cos(Math.toRadians(9.0)) * 170.0, p[0].y, 1e-9)
    }
}