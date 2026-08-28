package com.android.xrayfa.common.core

/**
 * Platform-neutral start parameters for Xray-core.
 * Android [Parcelable] [com.android.xrayfa.core.StartOptions] converts to this at the app boundary.
 */
data class CoreStartOptions(
    val url: String,
    val preUrl: String? = null,
    val nextUrl: String? = null,
)
