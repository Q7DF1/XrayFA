@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.android.xrayfa.shared.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.platform.qr.IosQrCameraPermission
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun SharedQrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    title: String,
    permissionRequiredMessage: String,
) {
    var hasPermission by remember { mutableStateOf(false) }
    var permissionResolved by remember { mutableStateOf(false) }
    var processed by remember { mutableStateOf(false) }

    val handleResult: (String) -> Unit = { value ->
        if (!processed && value.isNotBlank()) {
            processed = true
            onResult(value)
        }
    }

    DisposableEffect(Unit) {
        IosQrCameraPermission.ensureAccess(
            onGranted = {
                hasPermission = true
                permissionResolved = true
            },
            onDenied = {
                hasPermission = false
                permissionResolved = true
            },
        )
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            hasPermission -> {
                UIKitView(
                    factory = {
                        QrScannerView(onBarcodeDetected = handleResult)
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        (view as? QrScannerView)?.stopSession()
                    },
                )
                QrScannerOverlay()
            }
            permissionResolved -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = permissionRequiredMessage,
                        color = Color.White,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Text(
            text = title,
            color = Color.White,
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .align(Alignment.TopCenter),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun QrScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scannerSize = 250.dp.toPx()
        val left = (size.width - scannerSize) / 2
        val top = (size.height - scannerSize) / 2
        val rect = Rect(left, top, left + scannerSize, top + scannerSize)

        clipPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = rect,
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    ),
                )
            },
            clipOp = ClipOp.Difference,
        ) {
            drawRect(Color.Black.copy(alpha = 0.6f))
        }

        drawPath(
            path =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = rect,
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        ),
                    )
                },
            color = Color.White,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Suppress("CONFLICTING_OVERLOADS")
private class QrScannerView(
    private val onBarcodeDetected: (String) -> Unit,
) : UIView(frame = CGRectZero.readValue()),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    private val captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    init {
        backgroundColor = UIColor.blackColor
        setupSession()
    }

    private fun setupSession() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input =
            AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
                as? AVCaptureDeviceInput ?: return
        if (!captureSession.canAddInput(input)) return
        captureSession.addInput(input)

        val metadataOutput = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(metadataOutput)) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
            metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }

        val layer = AVCaptureVideoPreviewLayer(session = captureSession)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        this.layer.addSublayer(layer)
        previewLayer = layer
        captureSession.startRunning()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer?.frame = bounds
        CATransaction.commit()
    }

    fun stopSession() {
        captureSession.stopRunning()
    }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val readable =
            didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
        val value = readable?.stringValue
        if (!value.isNullOrBlank()) {
            onBarcodeDetected(value)
        }
    }
}
