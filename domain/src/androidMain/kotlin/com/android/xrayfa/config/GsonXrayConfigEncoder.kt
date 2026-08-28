package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration
import com.google.gson.Gson

class GsonXrayConfigEncoder(
    private val gson: Gson,
) : XrayConfigEncoder {
    override fun encode(config: XrayConfiguration): String = gson.toJson(config)
}
