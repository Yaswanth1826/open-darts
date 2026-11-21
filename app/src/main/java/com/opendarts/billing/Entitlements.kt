package com.opendarts.billing

/** Camera games anyone can play before a subscription is needed. The server holds the real counter (backend/functions). */
const val FREE_CAMERA_GAMES = 3

/**
 * What the player may do with the camera right now.
 * [signedIn] is false until there is an account; [known] is false when the account's plan has not been confirmed by the server yet.
 */
data class Access(val premium: Boolean, val freeLeft: Int, val signedIn: Boolean = true, val known: Boolean = true) {
    val canUseCamera: Boolean get() = signedIn && known && (premium || freeLeft > 0)
}

/** Where the answer to "may this player use the camera?" comes from. The keypad is always free. */
interface EntitlementProvider {
    fun access(): Access

    /** Called once per game that starts using the camera; does nothing for a subscriber. */
    fun useFreeGame()
}
