package com.opendarts.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.opendarts.ui.common.ScreenBox
import com.opendarts.ui.home.AccountCard
import com.opendarts.ui.home.AccountView
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
class AccountCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(name: String, view: AccountView, taps: MutableList<String> = mutableListOf()) {
        rule.setContent {
            OpenDartsTheme {
                ScreenBox {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccountCard(
                            view,
                            onSignIn = { taps += "signIn" }, onRetry = { taps += "retry" }, onSubscribe = { taps += "subscribe" },
                            onRestore = { taps += "restore" }, onManage = { taps += "manage" }, onSignOut = { taps += "signOut" }, onDelete = { taps += "delete" },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.activity.window.decorView.drawToBitmap()
        File("build/shots").apply { mkdirs() }.resolve("account-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val me = "yaswanthnimmalagoud@gmail.com"

    @Test fun signedOut() = show("signed-out", AccountView(signedIn = false))
    @Test fun trial() = show("trial", AccountView(email = me, freeLeft = 2, price = "\u20AC3.99"))
    @Test fun trialUsed() = show("used", AccountView(email = me, freeLeft = 0, price = "\u20AC3.99"))
    @Test fun subscribed() = show("subscribed", AccountView(email = me, premium = true))
    @Test fun unknown() = show("unknown", AccountView(email = me, known = false))

    @Test fun theButtonsReportTheirTaps() {
        val taps = mutableListOf<String>()
        show("taps", AccountView(email = me, freeLeft = 0, price = "\u20AC3.99"), taps)
        rule.onNodeWithText("Subscribe \u00B7 \u20AC3.99 / month").performClick()
        rule.onNodeWithText("Restore purchases").performClick()
        rule.onNodeWithText("Sign out").performClick()
        rule.onNodeWithText("Delete my account").performClick()
        assertEquals(listOf("subscribe", "restore", "signOut", "delete"), taps)
        rule.onNodeWithText("Manage subscription").assertDoesNotExist()
    }

    @Test fun subscribersCanManageTheirSubscription() {
        val taps = mutableListOf<String>()
        show("taps-subscribed", AccountView(email = me, premium = true), taps)
        rule.onNodeWithText("Manage subscription").performClick()
        assertEquals(listOf("manage"), taps)
    }
}