package com.android.xrayfa.shared.ui.config

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun SharedConfigImportMenu(
    onImportFromClipboard: () -> Unit,
    onManageSubscriptions: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    importFromClipboardLabel: String = "Import from clipboard",
    manageSubscriptionsLabel: String = "Manage subscriptions",
    scanQrLabel: String = "Scan QR code",
    additionalMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(Icons.Default.Add, contentDescription = importFromClipboardLabel)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(importFromClipboardLabel) },
            leadingIcon = {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
            },
            onClick = {
                dismiss()
                onImportFromClipboard()
            },
        )
        if (onScanQr != null) {
            DropdownMenuItem(
                text = { Text(scanQrLabel) },
                leadingIcon = {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                },
                onClick = {
                    dismiss()
                    onScanQr()
                },
            )
        }
        if (onManageSubscriptions != null) {
            DropdownMenuItem(
                text = { Text(manageSubscriptionsLabel) },
                leadingIcon = {
                    Icon(Icons.Outlined.Subscriptions, contentDescription = null)
                },
                onClick = {
                    dismiss()
                    onManageSubscriptions()
                },
            )
        }
        additionalMenuItems?.invoke(dismiss)
    }
}
