package com.opendarts.ui.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingCollectorTest {
    @Test
    fun `the comment goes right after the start of image marker and the rest of the jpeg is untouched`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val out = TrainingCollector.withComment(jpeg, "{\"a\":1}")
        assertEquals(0xFF, out[0].toInt() and 0xFF); assertEquals(0xD8, out[1].toInt() and 0xFF)
        assertEquals(0xFF, out[2].toInt() and 0xFF); assertEquals(0xFE, out[3].toInt() and 0xFF)
        val length = ((out[4].toInt() and 0xFF) shl 8) or (out[5].toInt() and 0xFF)
        assertEquals(7 + 2, length)
        assertEquals("{\"a\":1}", String(out, 6, 7))
        assertArrayEquals(jpeg.copyOfRange(2, jpeg.size), out.copyOfRange(2 + 4 + 7, out.size))
    }

    @Test
    fun `a corrected dart is noted with what the camera said and what the player says`() {
        assertEquals("fix-T20-S5", TrainingCollector.fixNote("T20", "S5"))
        assertEquals("fix-missed-D16", TrainingCollector.fixNote(null, "D16"))
        assertEquals("fix-T20-removed", TrainingCollector.fixNote("T20", null))
    }
}