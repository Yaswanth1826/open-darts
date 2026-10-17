package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onAllNodesWithText
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
}
