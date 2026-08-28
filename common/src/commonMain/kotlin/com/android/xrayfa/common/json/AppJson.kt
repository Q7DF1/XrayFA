package com.android.xrayfa.common.json

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Shared JSON codec for settings models migrating off Gson.
 *
 * Options mirror Gson defaults used in this project: nulls omitted, unknown keys ignored.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

private val stringListSerializer = ListSerializer(String.serializer())

fun encodeStringList(values: List<String>): String =
    AppJson.encodeToString(stringListSerializer, values)

fun decodeStringList(json: String): List<String> =
    AppJson.decodeFromString(stringListSerializer, json)
