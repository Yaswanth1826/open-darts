package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import com.opendarts.ui.setup.BullOffScreen
import com.opendarts.ui.theme.OpenDartsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-port-xxhdpi")
class BullOffScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val bitmap = rule.activity.window.decorView.drawToBitmap()
        File("build/shots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(label: String) = rule.onAllNodesWithText(label).onLast().performClick()

    private fun show(vararg names: String, onWinner: (Int) -> Unit = {}) {
        rule.setContent { OpenDartsTheme { BullOffScreen(names.toList(), -1, 0, onWinner, {}) } }
        rule.waitForIdle()
    }

    @Test fun theClosestDartStartsTheGame() {
        var winner = -1
        show("Ann", "Bo", onWinner = { winner = it })
        shot("bulloff-start")
        tap("25")
        rule.waitForIdle()
        tap("Bull")
        rule.waitForIdle()
        shot("bulloff-won")
        rule.onNodeWithText("Bo throws first").assertExists()
        rule.onNodeWithText("Start game").performClick()
        assertEquals(1, winner)
    }

    @Test fun aTieIsThrownAgainByTheTiedPlayersOnly() {
        var winner = -1
        show("Ann", "Bo", "Cy", onWinner = { winner = it })
        tap("25"); rule.waitForIdle()
        tap("25"); rule.waitForIdle()
        tap("Miss"); rule.waitForIdle()
        shot("bulloff-tie")
        rule.onNodeWithText("Throw again").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Cy").assertExists()
        rule.onNodeWithText("Ann: what did your dart hit?").assertExists()
        tap("Miss"); rule.waitForIdle()
        tap("Bull"); rule.waitForIdle()
        rule.onNodeWithText("Bo throws first").assertExists()
        rule.onNodeWithText("Start game").performClick()
        assertEquals(1, winner)
    }

    @Test fun thePlayersCanSayWhoWasCloser() {
        var winner = -1
        show("Ann", "Bo", onWinner = { winner = it })
        tap("Miss"); rule.waitForIdle()
        tap("Miss"); rule.waitForIdle()
        rule.onNodeWithText("Bo was closer").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Start game").performClick()
        assertEquals(1, winner)
    }
}