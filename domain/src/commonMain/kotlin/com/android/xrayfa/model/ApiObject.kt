package com.android.xrayfa.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiObject(
    val tag: String = "api",
    val listen: String? = null,
    val services: List<String>,
)
