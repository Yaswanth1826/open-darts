package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.opendarts.session.GameKind
import com.opendarts.session.GameSession
import com.opendarts.session.GameSetup
import com.opendarts.session.InputMode
import com.opendarts.ui.game.GameScreen
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
class GameScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val bitmap = rule.activity.window.decorView.drawToBitmap()
        File("build/shots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun play(kind: GameKind = GameKind.X01, shotName: String) {
        val session = GameSession().also { it.start(GameSetup(kind = kind, input = InputMode.MANUAL)) }
        rule.setContent { OpenDartsTheme { GameScreen(session, onExit = {}, onCalibrate = {}) } }
        rule.waitForIdle()
        shot("$shotName-start")
        rule.onAllNodesWithText("20")[0].performClick()
        rule.waitForIdle()
        shot("$shotName-after")
        if (kind == GameKind.X01) check(rule.onAllNodesWithText("481").fetchSemanticsNodes().isNotEmpty()) { "score did not update" }
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun keypadScoreUpdatesInPortrait() = play(shotName = "manual-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun keypadScoreUpdatesInLandscape() = play(shotName = "manual-landscape")

    private fun camera(shotName: String) {
        val session = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }
        rule.setContent { OpenDartsTheme { GameScreen(session, onExit = {}, onCalibrate = {}) } }
        rule.waitForIdle()
        rule.runOnIdle { session.throwDart(com.opendarts.game.model.DartHit(20, com.opendarts.game.model.Multiplier.TRIPLE), fromCamera = true) }
        rule.waitForIdle()
        shot(shotName)
        check(rule.onAllNodesWithText("T20").fetchSemanticsNodes().isNotEmpty()) { "dart slot did not update" }
        check(rule.onAllNodesWithText("441").fetchSemanticsNodes().isNotEmpty()) { "score did not update" }
        rule.runOnIdle { session.editDart(0, null) }
        rule.waitForIdle()
        check(rule.onAllNodesWithText("T20").fetchSemanticsNodes().isEmpty()) { "removed dart still shown" }
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun cameraScreenFollowsTheGamePortrait() = camera("camera-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun cameraScreenFollowsTheGameLandscape() = camera("camera-landscape")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun editorOffersTheHiddenDartOptions() {
        val session = GameSession().also { it.start(GameSetup(input = InputMode.CAMERA)) }
        rule.setContent { OpenDartsTheme { GameScreen(session, onExit = {}, onCalibrate = {}) } }
        rule.runOnIdle { session.throwDart(com.opendarts.game.model.DartHit(20, com.opendarts.game.model.Multiplier.TRIPLE), fromCamera = true, spot = com.opendarts.geometry.Point(0.0, 100.0)) }
        rule.waitForIdle()
        rule.onNodeWithText("T20").performClick()
        rule.onNodeWithText("Remove \u2013 a dart is behind it").assertExists().performClick()
        rule.waitForIdle()
        check(session.game!!.turnHits.isEmpty()) { "dart was not removed" }

    }

    private fun fourPlayers(input: InputMode, shotName: String) {
        val names = listOf("Player 1", "Player 2", "Player 3", "Player 4")
        val session = GameSession().also { it.start(GameSetup(playerNames = names, input = input)) }
        rule.setContent { OpenDartsTheme { GameScreen(session, onExit = {}, onCalibrate = {}) } }
        rule.waitForIdle()
        shot(shotName)
        for (name in names) check(rule.onAllNodesWithText(name.uppercase()).fetchSemanticsNodes().isNotEmpty()) { "$name is not shown" }
    }

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun showsEveryPlayerInPortraitKeypadMode() = fourPlayers(InputMode.MANUAL, "four-manual-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun showsEveryPlayerInLandscapeKeypadMode() = fourPlayers(InputMode.MANUAL, "four-manual-landscape")

    @Test @Config(qualifiers = "w360dp-h780dp-port-xxhdpi")
    fun showsEveryPlayerInPortraitCameraMode() = fourPlayers(InputMode.CAMERA, "four-camera-portrait")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun showsEveryPlayerInLandscapeCameraMode() = fourPlayers(InputMode.CAMERA, "four-camera-landscape")

    @Test @Config(qualifiers = "w700dp-h360dp-land-xxhdpi")
    fun scorePadFitsInLandscapeWithoutScrolling() {
        val session = GameSession().also { it.start(GameSetup(input = InputMode.MANUAL)) }
        rule.setContent { OpenDartsTheme { GameScreen(session, onExit = {}, onCalibrate = {}) } }
        rule.runOnIdle { session.throwDart(com.opendarts.game.model.DartHit(20, com.opendarts.game.model.Multiplier.SINGLE)) }
        rule.waitForIdle()
        rule.onNodeWithText("S20").performClick()
        rule.onNodeWithText("Change score").performClick()
        rule.waitForIdle()
        for (label in listOf("1", "10", "11", "20", "Bull", "Miss", "Triple")) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().let { check(it.isNotEmpty()) { "$label missing" } }
        }
        val bottom = rule.onAllNodesWithText("11").fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        check(bottom < 360f * 3f) { "pad runs off the screen: $bottom" }
    }
}

