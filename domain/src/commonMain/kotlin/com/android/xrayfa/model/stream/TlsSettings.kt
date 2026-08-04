package com.android.xrayfa.model.stream

data class TlsSettings(
    val serverName: String? = null,
    val verifyPeerCertInNames: List<String>? = null,
    val rejectUnknownSni: Boolean? = null,
    val allowInsecure: Boolean = false,
    val alpn: List<String>? = null,
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val cipherSuites: String? = null,
    val certificates: List<CertificateObject>? = null,
    val disableSystemRoot: Boolean? = null,
    val enableSessionResumption: Boolean? = null,
    val fingerprint: String? = null,
    val pinnedPeerCertificateChainSha256: List<String>? = null,
    val curvePreferences: List<String>? = null,
    val masterKeyLog: String? = null,
    val echServerKeys: String? = null,
    val echConfigList: String? = null,
    val echForceQuery: String? = null
)

data class CertificateObject(
    val certificateFile: String? = null,
    val certificate: List<String>? = null,
    val keyFile: String? = null,
    val key: List<String>? = null,
    val usage: String? = "encipherment"
)