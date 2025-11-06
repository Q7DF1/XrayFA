package com.android.xrayfa.model

data class DnsObject(
    val hosts:                  Map<String, Any>? = null, // Map<String, String> or Map<String, List<String>>
    val servers:                List<Any>? = null, // List<String> or List<DnsServerObject>
    val clientIp:               String? = null,
    val queryStrategy:          String? = null,
    val disableCache:           Boolean? = null,
    val disableFallback:        Boolean? = null,
    val disableFallbackIfMatch: Boolean? = null,
    val useSystemHosts:         Boolean? = null,
    val tag:                    String = "dns_inbound"

)

data class DnsServerObject(
    val tag: String = "dns-tag",
    val address: String? = null,
    val port: Int? = null,
    val domains: List<String>? = null,
    val expectedIps: List<String>? = null,
    val unexpectedIps: List<String>? = null,
    val skipFallback: Boolean? = null,
    val clientIp: String? = null,
    val queryStrategy: String? = null,
    val timeoutMs: Int? = null,
    val disableCache: Boolean? = null,
    val finalQuery: Boolean? = null
)