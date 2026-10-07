@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.shared.platform

import androidx.compose.runtime.*
import com.android.xrayfa.config.JsonVpnConfig
import com.android.xrayfa.config.JsonConfigException
import com.android.xrayfa.config.JsonConfigError
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

private class JsonDocumentDelegate(val scope: CoroutineScope, val result: (JsonConfigFile?, String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val access = url.startAccessingSecurityScopedResource()
        scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    val handle = NSFileHandle.fileHandleForReadingAtPath(url.path ?: "") ?: error("Unreadable document")
                    val data = try {
                        handle.readDataUpToLength((JsonVpnConfig.MAX_BYTES + 1).toULong(), error = null) ?: error("Unreadable document")
                    } finally { handle.closeAndReturnError(null) }
                    if (data.length > JsonVpnConfig.MAX_BYTES.toULong()) throw JsonConfigException(JsonConfigError.TOO_LARGE)
                    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: byteArrayOf()
                    JsonConfigFile(url.lastPathComponent ?: "config.json", bytes.decodeToString(throwOnInvalidSequence = true))
                }
                result(file, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                result(null, (e as? JsonConfigException)?.reason?.name ?: "READ")
            } finally {
                if (access) url.stopAccessingSecurityScopedResource()
            }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@Composable
actual fun rememberJsonConfigFilePicker(onResult: (JsonConfigFile?, String?) -> Unit): () -> Unit {
    val currentResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val delegate = remember(scope) { JsonDocumentDelegate(scope) { file, failed -> currentResult(file, failed) } }
    // Import mode asks the system to coordinate a local copy before delivering the URL.
    val picker = remember {
        UIDocumentPickerViewController(documentTypes = listOf("public.json", "public.text", "public.data"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport).also {
            it.delegate = delegate
            it.allowsMultipleSelection = false
        }
    }
    return {
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        presenter?.presentViewController(picker, animated = true, completion = null) ?: currentResult(null, "READ")
    }
}
