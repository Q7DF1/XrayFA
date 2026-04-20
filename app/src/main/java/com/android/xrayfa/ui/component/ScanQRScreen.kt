package com.android.xrayfa.ui.component

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.xrayfa.R
import com.android.xrayfa.utils.BarcodeUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import java.util.EnumMap
import java.util.concurrent.Executors
import kotlin.collections.set

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(onBack: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    BackHandler() {
        onBack()
    }

    var isTorchOn by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val result = BarcodeUtils.decodeQRCodeFromUri(context, it)
                if (result != null) {
                    onResult(result)
                } else {
                    Toast.makeText(context, R.string.decode_qr_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = Color.Black, // 设置背景为黑色，防止白闪
        floatingActionButton = {
            FloatingActionButton(
                onClick = { galleryLauncher.launch("image/*") },
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 32.dp, end = 8.dp) // 额外增加底部间距以避开导航栏
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Select from Gallery")
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (hasCameraPermission) {
                CameraPreview(onResult = onResult, isTorchOn = isTorchOn)
                ScannerOverlay()
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.scan_qr_permission_desc),
                        color = Color.White
                    )
                }
            }


            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // 闪光灯按钮
            IconButton(
                onClick = { isTorchOn = !isTorchOn },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Torch",
                    tint = Color.White
                )
            }


            Text(
                text = stringResource(R.string.scan_qr_title),
                color = Color.White,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .align(Alignment.TopCenter),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun CameraPreview(onResult: (String) -> Unit, isTorchOn: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val reader = remember {
        MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            hints[DecodeHintType.TRY_HARDER] = true
            setHints(hints)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val result = BarcodeUtils.decodeQRCodeFromImageProxy(reader, imageProxy)
            if (result != null) {
                onResult(result)
            }
            imageProxy.close()
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 处理闪光灯切换
    LaunchedEffect(isTorchOn) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            implementationMode = androidx.camera.viewfinder.core.ImplementationMode.EMBEDDED,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scannerSize = 250.dp.toPx()
        val left = (size.width - scannerSize) / 2
        val top = (size.height - scannerSize) / 2
        val rect = Rect(left, top, left + scannerSize, top + scannerSize)

        clipPath(Path().apply {
            addRoundRect(
                RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
            )
        }, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.6f))
        }

        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = rect,
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                )
            },
            color = Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}