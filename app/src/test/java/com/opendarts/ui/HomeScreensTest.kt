package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import com.opendarts.session.GameKind
import com.opendarts.session.HistoryStore
import com.opendarts.session.InputMode
import com.opendarts.session.MatchPlayer
import com.opendarts.session.MatchRecord
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

    private fun seedHistory() {
        val store = HistoryStore(ApplicationProvider.getApplicationContext())
        store.clear()
        val now = System.currentTimeMillis()
        store.add(MatchRecord(now - 90_000_000, GameKind.X01, "501", InputMode.CAMERA, 11, listOf(
            MatchPlayer("Player 1", true, darts = 99, points = 501, bestVisit = 140, hundredPlus = 3, oneFortyPlus = 1, finishingDarts = 6, checkoutHits = 1),
            MatchPlayer("Player 2", false, darts = 96, points = 380, bestVisit = 100, hundredPlus = 1, finishingDarts = 3),
        )))
        store.add(MatchRecord(now - 3_600_000, GameKind.X01, "301", InputMode.MANUAL, 7, listOf(
            MatchPlayer("Player 1", false, darts = 60, points = 240, bestVisit = 180, oneEighties = 1),
            MatchPlayer("Player 2", true, darts = 57, points = 301, bestVisit = 121, hundredPlus = 2, finishingDarts = 2, checkoutHits = 1),
        )))
        store.add(MatchRecord(now - 600_000, GameKind.CRICKET, "Cricket", InputMode.MANUAL, 9, listOf(MatchPlayer("Player 1", true), MatchPlayer("Player 2", false))))
    }

    private fun tour(prefix: String) {
        seedHistory()
        rule.setContent { OpenDartsTheme { OpenDartsApp() } }
        rule.waitForIdle()
        shot("$prefix-play")
        rule.onNodeWithText("Stats").performClick()
        rule.waitForIdle()
        shot("$prefix-stats")
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
