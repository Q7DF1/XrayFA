package com.android.xrayfa.common.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ring buffer for app logs (used by iOS log viewer and shared [Logger] sinks). */
object AppLogStore {
    private const val MAX_LINES = 1000

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun append(
        tag: String,
        level: String,
        message: String,
    ) {
        val line = "[$tag] $level: $message"
        _lines.update { current -> (current + line).takeLast(MAX_LINES) }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun snapshot(): String = _lines.value.joinToString(separator = "\n")
}
