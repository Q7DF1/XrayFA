package com.android.v2rayForAndroidUI.model

data class ApiObject(
    val tag: String = "api",
    val listen: String? = null,
    val services: List<String>
)
