package com.android.v2rayForAndroidUI.model.stream

data class GrpcSettings(
    val authority: String? = null,
    val serviceName:String? = null,
    val multiMode: Boolean = false,
    val user_agent: String? = null,
    val idle_timeout: Long = 60,
    val health_check_timeout: Long = 60,
    val permit_without_stream: Boolean = false,
    val initial_windows_size: Long = 0,
)
