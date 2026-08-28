package com.android.xrayfa.shared.ui.qr

import androidx.compose.runtime.Composable

@Composable
expect fun SharedQrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    title: String = "Scan QR code",
    permissionRequiredMessage: String = "Camera permission is required to scan QR codes",
)
