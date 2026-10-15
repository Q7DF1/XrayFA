package com.android.xrayfa.config

import com.android.xrayfa.model.XrayConfiguration
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSerializer
import kotlinx.serialization.json.JsonElement

class GsonXrayConfigEncoder(
    private val gson: Gson,
) : XrayConfigEncoder {
    private val configGson = gson.newBuilder()
        .registerTypeHierarchyAdapter(JsonElement::class.java,
            JsonSerializer<JsonElement> { value, _, _ -> JsonParser.parseString(value.toString()) })
        .create()

    override fun encode(config: XrayConfiguration): String = configGson.toJson(config)
}
