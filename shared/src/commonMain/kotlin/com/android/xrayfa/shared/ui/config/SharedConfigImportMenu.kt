package com.android.xrayfa.shared.ui.config

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
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
    modifier: Modifier = Modifier,
    importFromClipboardLabel: String = "Import from clipboard",
) {
    var expanded by remember { mutableStateOf(false) }
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
                expanded = false
                onImportFromClipboard()
            },
        )
    }
}
