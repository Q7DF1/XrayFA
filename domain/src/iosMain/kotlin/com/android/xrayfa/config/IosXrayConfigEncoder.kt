package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration

/**
 * Placeholder until the full Xray config graph is kotlinx-serializable on iOS.
 * VPN start on iOS is not implemented in phase C.
 */
class IosXrayConfigEncoder : XrayConfigEncoder {
    override fun encode(config: XrayConfiguration): String {
        throw UnsupportedOperationException("Xray config encoding on iOS is not implemented yet")
    }
}
