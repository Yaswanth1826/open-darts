package com.opendarts.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity behind a Compose context, needed for sign-in and the purchase sheet. */
fun Context.findActivity(): Activity? = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
