@file:Suppress("MagicNumber", "LongParameterList")

package app.openhealth.android.nutrition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openhealth.core.seam.BarcodeFrame
import app.openhealth.ui.R
import app.openhealth.ui.theme.OhTheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The camera half of the barcode screen (`pr.md` §6.3). The permission is requested the
 * first time this surface is opened, never at launch; a denial is a visible state with a
 * manual-entry path, not a dead screen.
 */
@Composable
fun BarcodeCameraPreview(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        denied = !allowed
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
                modifier = Modifier.padding(OhTheme.space.s6),
            ) {
                Text(
                    text = stringResource(
                        if (denied) {
                            R.string.nutrition_scan_permission_denied
                        } else {
                            R.string.nutrition_scan_permission_ask
                        },
                    ),
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label2,
                )
            }
        }
        return
    }
    val decoder = remember { AndroidBarcodeDecoder() }
    val handled = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            handled.set(true)
            executor.shutdown()
            decoder.close()
        }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                providerFuture.addListener(
                    {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            if (handled.get()) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val value = runCatching {
                                decoder.decode(proxy.toBarcodeFrame())
                            }.getOrNull()
                            if (value != null && handled.compareAndSet(false, true)) {
                                onBarcode(value)
                            }
                            proxy.close()
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    },
                    ContextCompat.getMainExecutor(viewContext),
                )
            }
        },
    )
}

/** Copies a camera frame into the one YUV layout both decoders read. */
private fun ImageProxy.toBarcodeFrame(): BarcodeFrame {
    val width = width
    val height = height
    val nv21 = ByteArray(width * height * 3 / 2)
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    copyPlane(
        buffer = yPlane.buffer,
        rowStride = yPlane.rowStride,
        pixelStride = yPlane.pixelStride,
        width = width,
        height = height,
        target = nv21,
        targetOffset = 0,
        targetRowStride = width,
    )
    val chromaOffset = width * height
    var vIndex = 0
    var uIndex = 0
    for (row in 0 until height / 2) {
        var vRow = vIndex
        var uRow = uIndex
        for (column in 0 until width / 2) {
            val index = chromaOffset + row * width + column * 2
            nv21[index] = vPlane.buffer[vRow]
            nv21[index + 1] = uPlane.buffer[uRow]
            vRow += vPlane.pixelStride
            uRow += uPlane.pixelStride
        }
        vIndex += vPlane.rowStride
        uIndex += uPlane.rowStride
    }
    return BarcodeFrame(nv21, width, height, imageInfo.rotationDegrees)
}

private fun copyPlane(
    buffer: java.nio.ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    target: ByteArray,
    targetOffset: Int,
    targetRowStride: Int,
) {
    var sourceIndex = 0
    var targetIndex = targetOffset
    for (row in 0 until height) {
        var source = sourceIndex
        for (column in 0 until width) {
            target[targetIndex + column] = buffer[source]
            source += pixelStride
        }
        sourceIndex += rowStride
        targetIndex += targetRowStride
    }
}
