package com.android.xrayfa.config

import kotlinx.serialization.json.Json

/**
 * JSON codec for Xray runtime configs. Options mirror legacy Gson output used on Android.
 */
val XrayJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
