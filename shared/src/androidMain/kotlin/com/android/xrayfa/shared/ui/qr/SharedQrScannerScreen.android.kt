package com.android.xrayfa.shared.ui.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Android uses Navigation3 [QRCodeScannerScreen]; this stub satisfies the expect/actual contract. */
@Composable
actual fun SharedQrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    title: String,
    permissionRequiredMessage: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("QR scanner is handled by Android Navigation3")
    }
}
