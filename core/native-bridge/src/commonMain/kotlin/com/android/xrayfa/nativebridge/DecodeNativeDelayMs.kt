package com.android.xrayfa.nativebridge

/** Maps gomobile `(ok, int64)` delay results. Failure → `-1` (same as Android error path). */
internal fun decodeNativeDelayMs(ok: Boolean, delayMs: Long): Long = if (ok) delayMs else -1L
