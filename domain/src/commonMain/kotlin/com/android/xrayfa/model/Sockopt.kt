package com.android.xrayfa.model

data class HappyEyeballs(
    val tryDelayMs: Int = 250,
    val prioritizeIPv6: Boolean = false,
    val interleave: Int = 1,
    val maxConcurrent: Int = 1,
)

data class Sockopt(
    val mark: Int = 0,
    val tcpMaxSeg: Int? = null,
    val tcpFastOpen: Any? = null,
    val tproxy: String = "off",
    val domainStrategy: String = "AsIs",
    val happyEyeballs: HappyEyeballs? = null,
    val dialerProxy: String = "",
    val acceptProxyProtocol: Boolean = false,
    val tcpKeepAliveInterval: Int = 0,
    val tcpKeepAliveIdle: Int = 300,
    val tcpUserTimeout: Int = 10000,
    val tcpCongestion: String = "bbr",
    val interfaceName: String = "",
    val v6only: Boolean = false,
    val tcpWindowClamp: Int = 600,
    val tcpMptcp: Boolean = false,
    val addressPortStrategy: String = "",
)
