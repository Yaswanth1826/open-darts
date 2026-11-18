package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import com.opendarts.ui.home.OpenDartsApp
import com.opendarts.ui.theme.OpenDartsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val bitmap = rule.activity.window.decorView.drawToBitmap()
        File("build/shots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tour(prefix: String) {
        rule.setContent { OpenDartsTheme { OpenDartsApp() } }
        rule.waitForIdle()
        shot("$prefix-play")
        rule.onNodeWithText("Players").performClick()
        rule.waitForIdle()
        shot("$prefix-players")
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        shot("$prefix-settings")
        rule.onNodeWithText("Play").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("X01").performClick()
        rule.waitForIdle()
        shot("$prefix-new-x01")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun tourInPortrait() = tour("home-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun tourInLandscape() = tour("home-landscape")
}
