package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun everyCategoryHasItsGames() {
        rule.setContent { OpenDartsTheme { OpenDartsApp() } }
        rule.waitForIdle()
        rule.onNodeWithText("Party").performClick()
        rule.waitForIdle()
        shot("new-party")
        rule.onNodeWithText("Killer").performClick()
        rule.waitForIdle()
        shot("new-killer")
        rule.onNodeWithText("Practice").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Checkout practice").performClick()
        rule.waitForIdle()
        shot("new-checkout")
        rule.onNodeWithText("Weak spots").performClick()
        rule.waitForIdle()
        shot("new-weak-spots")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun theBotCanBePickedInTheSetup() {
        rule.setContent { OpenDartsTheme { OpenDartsApp() } }
        rule.waitForIdle()
        rule.onNodeWithText("X01").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Club \u00B7 60").performScrollTo().performClick()
        rule.waitForIdle()
        shot("new-bot")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun aNewGameOpensWithABullOffWhichSetsWhoThrowsFirst() {
        rule.setContent { OpenDartsTheme { OpenDartsApp() } }
        rule.waitForIdle()
        rule.onNodeWithText("X01").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Manual keypad").performScrollTo().performClick()
        rule.onNodeWithText("Bull-off").performScrollTo()
        shot("new-bulloff-option")
        rule.onNodeWithText("Start game").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Player 1: what did your dart hit?").assertExists()
        rule.onAllNodesWithText("Miss").onLast().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Bull").onLast().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Player 2 throws first").assertExists()
        rule.onNodeWithText("Start game").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Player 2 to throw", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h1500dp-port-xxhdpi")
    fun theAdvancedStatsShowWhereTheDartsGo() {
        val random = kotlin.random.Random(7)
        val aims = List(120) { com.opendarts.game.model.DartHit.treble(20) } + List(40) { com.opendarts.game.model.DartHit.treble(19) } + List(40) { com.opendarts.game.model.DartHit.double(16) } + List(20) { com.opendarts.game.model.DartHit.double(8) }
        val shots = aims.map { com.opendarts.game.bot.DartBot.land(it, 60, random) }.joinToString("") { com.opendarts.session.ShotCodes.encode(it).toString() }
        val records = (1..6).map { n ->
            MatchRecord(n * 1000L, GameKind.X01, "501", InputMode.CAMERA, 11, listOf(MatchPlayer("Player 1", true, darts = 60, points = 380 + n * 25, detail = com.opendarts.session.ShotDetail(shots, "16-9-3.8-5-2.20-4-2", 120 + n * 10, 18))))
        }
        rule.setContent { OpenDartsTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) { com.opendarts.ui.home.AdvancedPanel(records, listOf("Player 1"), true) {} } } }
        rule.waitForIdle()
        shot("advanced-stats")
    }

    @Test @Config(qualifiers = "w360dp-h400dp-port-xxhdpi")
    fun theAdvancedStatsAreLockedForFreePlayers() {
        rule.setContent { OpenDartsTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { com.opendarts.ui.home.AdvancedPanel(emptyList(), listOf("Player 1"), false) {} } } }
        rule.waitForIdle()
        shot("advanced-stats-locked")
    }

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun tourInLandscape() = tour("home-landscape")
}
