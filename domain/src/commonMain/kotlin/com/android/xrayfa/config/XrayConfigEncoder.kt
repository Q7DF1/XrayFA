package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration

/**
 * Encodes a full Xray runtime config to JSON consumed by Xray-core.
 *
 * Android uses Gson (legacy reflection output); iOS stub compiles until
 * kotlinx.serialization covers the full config graph.
 */
interface XrayConfigEncoder {
    fun encode(config: XrayConfiguration): String
}
