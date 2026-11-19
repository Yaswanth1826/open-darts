package com.opendarts.ui.camera

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opendarts.ui.common.PrimaryButton
import com.opendarts.ui.theme.Body
import com.opendarts.ui.theme.Display
import com.opendarts.ui.theme.Pal
import java.util.concurrent.Executors

/** Asks for the camera permission, then shows [content]. After a permanent "don't allow" it sends the player to the app settings. */
@Composable
fun CameraPermissionGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allowed = { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(allowed()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    // Coming back from the system settings: pick up a permission that was switched on there.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = allowed() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (granted) { content(); return }
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    val blocked = asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera access", color = Pal.Text, fontFamily = Display, fontSize = 20.sp)
        Text(
            if (blocked) "Camera access is switched off for Open Darts. Turn it on in the app settings to score darts automatically. The keypad works without it."
            else "Open Darts looks at the dartboard through the camera to score each dart. Pictures are analysed on this phone and are not sent anywhere.",
            color = Pal.Muted, fontFamily = Body, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        if (blocked) PrimaryButton("Open app settings", onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }, modifier = Modifier.fillMaxWidth())
        else PrimaryButton("Allow camera", onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Live preview plus an optional per-frame callback (called on a background thread with an upright
 * greyscale frame, roughly 960x720). The preview is letter-boxed (FIT) so view coordinates map linearly to frame coordinates.
 */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    onZoomRange: (min: Float, max: Float) -> Unit = { _, _ -> },
    onFrame: ((CameraFrame) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            keepScreenOn = true
        }
    }
    val frameCallback by rememberUpdatedState(onFrame)
    val rangeCallback by rememberUpdatedState(onZoomRange)
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    LaunchedEffect(camera, zoom) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        val range = camera?.cameraInfo?.zoomState?.value
        val ratio = if (range != null) zoom.coerceIn(range.minZoomRatio, range.maxZoomRatio) else zoom
        runCatching { control.setZoomRatio(ratio) }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        var analysisUseCase: ImageAnalysis? = null
        future.addListener({
            val provider = future.get()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .setResolutionStrategy(ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysisUseCase = analysis
            analysis.setAnalyzer(executor) { image: ImageProxy ->
                try {
                    frameCallback?.let { callback -> callback(CameraFrame({ image.toUprightGray() }) { image.toUprightColor() }) }
                } catch (e: Exception) {
                    android.util.Log.w("CameraView", "frame skipped", e)
                } finally {
                    image.close()
                }
            }
            runCatching {
                provider.unbindAll()
                val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                bound.cameraInfo.zoomState.observe(lifecycleOwner) { rangeCallback(it.minZoomRatio, it.maxZoomRatio) }
                camera = bound
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            // Stop the frames first: a frame offered to an executor that is already shut down is an exception inside CameraX.
            runCatching { analysisUseCase?.clearAnalyzer() }
            runCatching { if (future.isDone) future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}