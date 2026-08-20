package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration

/**
 * Encodes Xray runtime configs with kotlinx.serialization for iOS (and future commonMain use).
 */
class IosXrayConfigEncoder : XrayConfigEncoder by KotlinxXrayConfigEncoder()
