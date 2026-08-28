package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration

class KotlinxXrayConfigEncoder : XrayConfigEncoder {
    override fun encode(config: XrayConfiguration): String =
        XrayJson.encodeToString(XrayConfiguration.serializer(), config)
}
