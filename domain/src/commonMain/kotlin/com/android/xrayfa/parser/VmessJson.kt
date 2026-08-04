package com.android.xrayfa.parser

import com.android.xrayfa.common.repository.AppJson
import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.dto.VMESSConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.stringOrEmpty(key: String): String =
    this[key]?.jsonPrimitive?.content ?: ""

fun JsonObject.optionalString(key: String): String? =
    this[key]?.jsonPrimitive?.content

internal fun JsonObject.intValue(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("Missing or invalid int field: $key")

internal fun JsonObject.copyWithUpdates(updates: Map<String, JsonPrimitive>): JsonObject =
    buildJsonObject {
        forEach { (k, v) -> put(k, v) }
        updates.forEach { (k, v) -> put(k, v) }
    }

internal fun decodeVmessJson(decoded: String): JsonObject =
    AppJson.parseToJsonElement(decoded).jsonObject

internal fun encodeVmessJson(json: JsonObject): String = AppJson.encodeToString(JsonObject.serializer(), json)
