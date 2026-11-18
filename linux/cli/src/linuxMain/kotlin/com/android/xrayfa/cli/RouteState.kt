package com.android.xrayfa.cli

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Route dump enumeration order is not forwarding order; nested nexthop order is retained. */
internal fun normalizedRoutes(routes: JsonElement): JsonArray {
    fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.sortedBy { it.key }.associate { it.key to canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
    return JsonArray(routes.jsonArray.map { canonical(normalizedNetwork(it)) }.sortedBy { it.toString() })
}

/** Preserve duplicate counts and every route attribute; show bounded actionable differences. */
internal fun routeDifference(expected: JsonElement, observed: JsonElement): String? {
    val before = normalizedRoutes(expected).groupingBy { it }.eachCount()
    val after = normalizedRoutes(observed).groupingBy { it }.eachCount()
    if (before == after) return null
    fun changes(left: Map<JsonElement, Int>, right: Map<JsonElement, Int>) =
        left.mapValues { (route, count) -> count - (right[route] ?: 0) }.filterValues { it > 0 }
    val removed = changes(before, after)
    val added = changes(after, before)
    fun describe(items: Map<JsonElement, Int>): String = items.entries.take(3).joinToString("; ") {
        val row = it.key as? JsonObject
        val raw = (row?.get("raw") as? JsonPrimitive)?.content ?: it.key.toString()
        val family = (row?.get("family") as? JsonPrimitive)?.content ?: "route"
        "$family ${clean(raw).take(240)} (count=${it.value})"
    }
    return "added=${added.values.sum()} [${describe(added)}]; removed=${removed.values.sum()} [${describe(removed)}]"
}

/**
 * NetworkManager connectivity checks add/remove a 20000 default-route metric penalty.
 * With one IPv4 default its metric does not change the underlay path. Accept only
 * that exact DHCP-default delta, anchored to the startup metric; all other rows,
 * gateways, devices, attributes and duplicate counts must remain identical.
 */
internal fun connectivityMetricChange(
    baseline: JsonElement,
    expected: JsonElement,
    observed: JsonElement,
    underlay: String,
): Boolean {
    fun defaultRoute(routes: JsonElement): JsonObject? {
        val defaults = routes.jsonArray.map { it.jsonObject }.filter {
            it["family"] == JsonPrimitive("inet") && it["dst"] == JsonPrimitive("default") &&
                (it["table"]?.jsonPrimitive?.content ?: "main") == "main"
        }
        return defaults.singleOrNull()?.takeIf {
            it["dev"] == JsonPrimitive(underlay) && it["protocol"] == JsonPrimitive("dhcp")
        }
    }
    val original = defaultRoute(baseline) ?: return false
    val before = defaultRoute(expected) ?: return false
    val after = defaultRoute(observed) ?: return false
    val startMetric = original["metric"]?.jsonPrimitive?.longOrNull ?: return false
    val beforeMetric = before["metric"]?.jsonPrimitive?.longOrNull ?: return false
    val afterMetric = after["metric"]?.jsonPrimitive?.longOrNull ?: return false
    val allowed = setOf(startMetric, startMetric + 20000, startMetric - 20000).filter { it >= 0 }
    if (beforeMetric !in allowed || afterMetric !in allowed || kotlin.math.abs(afterMetric - beforeMetric) != 20000L)
        return false
    fun withoutPenalty(route: JsonObject, metric: Long): JsonObject? {
        val raw = route["raw"]?.jsonPrimitive?.content ?: return null
        val token = Regex("(?<=metric )$metric(?=\\s|$)")
        if (token.findAll(raw).count() != 1) return null
        return JsonObject(route + mapOf("metric" to JsonPrimitive(startMetric),
            "raw" to JsonPrimitive(raw.replace(token, startMetric.toString()))))
    }
    val stableBefore = withoutPenalty(before, beforeMetric) ?: return false
    val stableAfter = withoutPenalty(after, afterMetric) ?: return false
    if (stableBefore != original || stableAfter != original) return false
    val adjusted = JsonArray(observed.jsonArray.map { if (it == after) before else it })
    return routeDifference(expected, adjusted) == null
}

/** Recognize only the delete/add gap of the same DHCP-default metric transaction. */
internal fun connectivityMetricTransition(
    baseline: JsonElement,
    expected: JsonElement,
    observed: JsonElement,
    underlay: String,
): Boolean {
    fun isDefault(row: JsonElement): Boolean = row.jsonObject.let {
        it["family"] == JsonPrimitive("inet") && it["dst"] == JsonPrimitive("default") &&
            (it["table"]?.jsonPrimitive?.content ?: "main") == "main"
    }
    val original = baseline.jsonArray.singleOrNull(::isDefault) ?: return false
    val before = expected.jsonArray.singleOrNull(::isDefault) ?: return false
    val row = before.jsonObject
    if (row["dev"] != JsonPrimitive(underlay) || row["protocol"] != JsonPrimitive("dhcp")) return false
    if (before != original) {
        val anchored = JsonArray(baseline.jsonArray.map { if (it == original) before else it })
        if (!connectivityMetricChange(baseline, baseline, anchored, underlay)) return false
    }
    val defaults = observed.jsonArray.filter(::isDefault)
    if (defaults.size !in setOf(0, 2)) return false
    if (routeDifference(JsonArray(expected.jsonArray.filterNot(::isDefault)),
            JsonArray(observed.jsonArray.filterNot(::isDefault))) != null) return false
    if (defaults.isEmpty()) return true
    if (defaults.distinct().size != 2 || before !in defaults) return false
    return defaults.all { candidate ->
        candidate == before || connectivityMetricChange(baseline, expected,
            JsonArray(expected.jsonArray.map { if (it == before) candidate else it }), underlay)
    }
}
