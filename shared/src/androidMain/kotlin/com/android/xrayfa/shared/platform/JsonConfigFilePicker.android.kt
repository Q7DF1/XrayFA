package com.android.xrayfa.shared.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import com.android.xrayfa.config.JsonVpnConfig
import kotlinx.coroutines.*

@Composable
actual fun rememberJsonConfigFilePicker(onResult: (JsonConfigFile?, String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "config.json"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > JsonVpnConfig.MAX_BYTES) throw com.android.xrayfa.config.JsonConfigException(com.android.xrayfa.config.JsonConfigError.TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Unreadable document")
                    JsonConfigFile(name, bytes.decodeToString(throwOnInvalidSequence = true))
                }
            }
            currentResult(result.getOrNull(), result.exceptionOrNull()?.let { (it as? com.android.xrayfa.config.JsonConfigException)?.reason?.name ?: "READ" })
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
}
