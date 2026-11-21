package com.opendarts.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendarts.billing.FREE_CAMERA_GAMES
import com.opendarts.billing.PlayBilling
import com.opendarts.billing.RemoteEntitlements
import com.opendarts.ui.common.OutlineButton
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.common.findActivity
import com.opendarts.ui.common.panel
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal

/** What the account card shows. Kept apart from the account class so every state can be previewed and tested. */
data class AccountView(
    val available: Boolean = true,
    val signedIn: Boolean = true,
    val email: String? = null,
    val busy: Boolean = false,
    val premium: Boolean = false,
    val testBuild: Boolean = false,
    val known: Boolean = true,
    val freeLeft: Int = FREE_CAMERA_GAMES,
    val price: String? = null,
    val message: String? = null,
)

@Composable
fun AccountSection(account: RemoteEntitlements, billing: PlayBilling, onDelete: () -> Unit) {
    val context = LocalContext.current
    val plan = account.access()
    val view = AccountView(
        available = account.available, signedIn = account.signedIn, email = account.email, busy = account.busy,
        premium = plan.premium, testBuild = plan.premium && account.isDebugBuild, known = plan.known, freeLeft = plan.freeLeft,
        price = billing.price, message = account.message ?: billing.message,
    )
    AccountCard(
        view,
        onSignIn = { context.findActivity()?.let(account::signIn) },
        onRetry = { account.refresh() },
        onSubscribe = { context.findActivity()?.let(billing::purchase) },
        onRestore = { billing.restore(manual = true) },
        onManage = {
            val link = "https://play.google.com/store/account/subscriptions?sku=${PlayBilling.PRODUCT}&package=${context.packageName}"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        },
        onSignOut = { context.findActivity()?.let(account::signOut) },
        onDelete = onDelete,
    )
}

@Composable
fun AccountCard(
    account: AccountView, onSignIn: () -> Unit, onRetry: () -> Unit, onSubscribe: () -> Unit,
    onRestore: () -> Unit, onManage: () -> Unit, onSignOut: () -> Unit, onDelete: () -> Unit,
) {
    val plan = account
    val message = account.message
    if (!account.available) {
        Card {
            Text("Camera scoring is not available in this build", color = Pal.Text, fontFamily = Display, fontSize = 17.sp)
            Muted("The keypad works as always, with every game mode.")
        }
        return
    }

    if (!account.signedIn) {
        Card {
            Text("Camera scoring", color = Pal.Text, fontFamily = Display, fontSize = 20.sp)
            Muted("Sign in with a free Google account to get $FREE_CAMERA_GAMES free camera games. Keypad games and your history never need an account.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dots(left = FREE_CAMERA_GAMES)
                Text("$FREE_CAMERA_GAMES free camera games", color = Pal.Text, fontFamily = Display, fontSize = 14.sp)
            }
            PrimaryButton(
                if (account.busy) "Signing in\u2026" else "Continue with Google",
                onClick = onSignIn, enabled = !account.busy, modifier = Modifier.fillMaxWidth(),
            )
        }
        message?.let { Muted(it, Modifier.padding(start = 4.dp)) }
        return
    }

    val testBuild = account.testBuild
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Pal.Lime), contentAlignment = Alignment.Center) {
                Text(account.email?.firstOrNull()?.uppercase().orEmpty().ifEmpty { "?" }, color = Pal.OnLime, fontFamily = Display, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Signed in with Google", color = Pal.Text, fontFamily = Display, fontSize = 16.sp)
                Text(account.email.orEmpty(), color = Pal.Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                testBuild -> Badge("TEST BUILD", Pal.Lime)
                plan.premium -> Badge("SUBSCRIBED", Pal.Lime)
                !plan.known -> Badge("CHECKING", Pal.Muted)
                plan.freeLeft > 0 -> Badge("FREE TRIAL", Pal.Lime)
                else -> Badge("TRIAL USED", Pal.Muted)
            }
            if (!plan.premium && plan.known && plan.freeLeft > 0) Dots(plan.freeLeft)
        }
        Text(
            when {
                testBuild -> "Unlocked for testing. Turn on the free-plan test switch below to try the real limits."
                plan.premium -> "Camera scoring is unlocked. Thank you for supporting Open Darts."
                !plan.known -> "Could not check your plan. Connect to the internet and try again."
                plan.freeLeft > 0 -> "${plan.freeLeft} of $FREE_CAMERA_GAMES free camera games left. The keypad is always free."
                else -> "Your free camera games are used. Subscribe to keep scoring darts with the camera. The keypad is always free."
            },
            color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
        )
        val price = account.price
        val subscribe = if (price != null) "Subscribe \u00B7 $price / month" else "Subscribe (not available yet)"
        when {
            !plan.known -> PrimaryButton("Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
            !plan.premium && plan.freeLeft == 0 -> PrimaryButton(subscribe, onClick = onSubscribe, enabled = price != null, modifier = Modifier.fillMaxWidth())
            !plan.premium -> OutlineButton(subscribe, onSubscribe, Modifier.fillMaxWidth(), height = 48.dp)
        }
    }
    message?.let { Muted(it, Modifier.padding(start = 4.dp)) }

    Column(Modifier.fillMaxWidth().panel(18.dp)) {
        Entry("Restore purchases", "Already subscribed? Get it back on this phone.", onRestore)
        Divider(Modifier.padding(horizontal = 16.dp))
        if (account.premium && !account.testBuild) {
            Entry("Manage subscription", "Change or cancel in Google Play.", onManage)
            Divider(Modifier.padding(horizontal = 16.dp))
        }
        Entry("Sign out", "Camera scoring locks until you sign in again.", onSignOut)
    }
    Text(
        "Delete my account", color = Pal.Muted, fontFamily = Body, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDelete).padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().panel(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) = Text(text, color = Pal.Muted, fontFamily = Body, fontSize = 14.sp, modifier = modifier)

@Composable
private fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Pal.Line))

@Composable
private fun Badge(text: String, colour: Color) = Text(
    text, color = colour, fontFamily = Display, fontSize = 12.sp, letterSpacing = 1.5.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, colour, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
)

/** One dot per free camera game: filled while it is still available. */
@Composable
private fun Dots(left: Int) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(FREE_CAMERA_GAMES) { i ->
        val filled = i < left
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (filled) Pal.Lime else Pal.Raised).border(1.dp, if (filled) Pal.Lime else Pal.Line, CircleShape))
    }
}

@Composable
private fun Entry(title: String, subtitle: String, onClick: () -> Unit) = Column(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(title, color = Pal.Text, fontFamily = Display, fontSize = 16.sp)
    Text(subtitle, color = Pal.Muted, fontFamily = Body, fontSize = 13.sp)
}
