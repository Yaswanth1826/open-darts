package com.opendarts.billing

import android.content.Context
import android.content.pm.ApplicationInfo

/** Camera games anyone can play before a subscription is needed. */
const val FREE_CAMERA_GAMES = 3

/** What the player may do with the camera right now. */
data class Access(val premium: Boolean, val freeLeft: Int) {
    val canUseCamera: Boolean get() = premium || freeLeft > 0
}

/** Where the answer to "may this player use the camera?" comes from. The keypad is always free. */
interface EntitlementProvider {
    fun access(): Access

    /** Called once per game that starts using the camera; does nothing for a subscriber. */
    fun useFreeGame()
}

/**
 * Stand-in until the server-checked provider exists (accounts, Google Play Billing, server-side counter).
 * It only keeps a counter on the phone, which can be reset by clearing app data, so it must never be the last word
 * in a release. Debug builds count as subscribed so testing is never blocked, unless [simulateFreePlan] is on.
 */
class LocalEntitlements(context: Context) : EntitlementProvider {
    private val prefs = context.applicationContext.getSharedPreferences("opendarts_plan", Context.MODE_PRIVATE)
    val isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    var simulateFreePlan: Boolean
        get() = prefs.getBoolean("simulate_free", false)
        set(value) { prefs.edit().putBoolean("simulate_free", value).apply() }

    override fun access() = Access(
        premium = isDebugBuild && !simulateFreePlan,
        freeLeft = (FREE_CAMERA_GAMES - prefs.getInt("used", 0)).coerceAtLeast(0),
    )

    override fun useFreeGame() {
        if (access().premium) return
        prefs.edit().putInt("used", prefs.getInt("used", 0) + 1).apply()
    }

    fun resetFreeGames() { prefs.edit().putInt("used", 0).apply() }
}
