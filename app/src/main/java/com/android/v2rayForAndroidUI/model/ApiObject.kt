package com.android.v2rayForAndroidUI.model

data class ApiObject(
    val tag: String = "api",
    val listen: String,
    val services: List<String>
)
