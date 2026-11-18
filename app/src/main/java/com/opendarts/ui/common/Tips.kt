package com.opendarts.ui.common

import androidx.compose.runtime.Composable

/** The camera setup guide, opened from the camera menu, the camera card and the home screen. */
@Composable
fun SetupTipsDialog(onDismiss: () -> Unit) = CameraGuideDialog(onDismiss)
