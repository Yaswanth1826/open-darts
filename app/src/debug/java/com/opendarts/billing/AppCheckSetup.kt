package com.opendarts.billing

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Test builds are not installed from Google Play, so they prove themselves with a debug token (see the App Check page of the Firebase console). */
object AppCheckSetup {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}