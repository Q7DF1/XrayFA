package com.android.xrayfa.model

import kotlinx.serialization.Serializable

@Serializable
data class Version(
    val min: String,
    val max: String,
)
