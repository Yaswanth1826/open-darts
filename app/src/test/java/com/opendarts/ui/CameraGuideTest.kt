package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.opendarts.ui.common.CameraGuide
import com.opendarts.ui.common.CameraGuideCard
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.theme.OpenDartsTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CameraGuideTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val bitmap = rule.activity.window.decorView.drawToBitmap()
        File("build/shots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun walk(prefix: String) {
        var closed = false
        rule.setContent { OpenDartsTheme { ScreenBox { Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { CameraGuideCard({ closed = true }) } } } }
        rule.waitForIdle()
        shot("$prefix-1")
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle(); shot("$prefix-2")
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle(); shot("$prefix-3")
        rule.onNodeWithText("Next").performClick(); rule.waitForIdle(); shot("$prefix-4")
        assertFalse(closed)
        rule.onNodeWithText("Got it").performClick()
        assertTrue(closed)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun guideWalksThroughFourStepsInPortrait() = walk("guide-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun guideWalksThroughFourStepsInLandscape() = walk("guide-landscape")

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun theGuideOpensByItselfOnlyUntilItWasSeen() {
        assertFalse(CameraGuide.seen(rule.activity))
        CameraGuide.markSeen(rule.activity)
        assertTrue(CameraGuide.seen(rule.activity))
    }
}
