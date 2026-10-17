package com.opendarts.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun `an ultra wide camera offers its widest view first`() {
        assertEquals(listOf(0.6f, 1f, 2f, 3f, 5f), CameraZoom.steps(0.6f, 8f))
    }

    @Test
    fun `a plain camera offers only the steps it can do`() {
        assertEquals(listOf(1f, 2f, 3f), CameraZoom.steps(1f, 3f))
        assertEquals(listOf(1f), CameraZoom.steps(1f, 1f))
    }
}