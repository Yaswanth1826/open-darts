package com.opendarts.billing

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Google Play vouches that the app is the genuine one, on a genuine device; the backend can then refuse everything else. */
object AppCheckSetup {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}