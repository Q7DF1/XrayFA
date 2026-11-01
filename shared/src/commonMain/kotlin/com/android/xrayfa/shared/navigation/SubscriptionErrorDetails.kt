package com.android.xrayfa.shared.navigation

import com.android.xrayfa.network.SubscriptionFetchException
import com.android.xrayfa.repository.SubscriptionFailure
import com.android.xrayfa.repository.SubscriptionParseFailure

internal enum class SubscriptionErrorKind { HTTP, TIMEOUT, DNS, TLS, NETWORK, PARSE, SAVE, DUPLICATE, UNKNOWN }

internal data class SubscriptionErrorDetails(
    val kind: SubscriptionErrorKind,
    val httpStatus: Int? = null,
    val exceptionTypes: String = "",
    val parseContext: String = "",
    val validationReason: String = "",
    val codeLocations: String = "",
)

/** Raw messages and stack traces may contain authenticated URLs; emit only allowlisted details. */
internal fun subscriptionErrorDetails(error: Exception): SubscriptionErrorDetails {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
    val names = causes.map { it::class.simpleName.orEmpty() }
    val httpStatus = causes.filterIsInstance<SubscriptionFetchException>().firstOrNull()
        ?.message?.let { Regex("HTTP error: ([0-9]{3})").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
    val stage = (error as? SubscriptionFailure)?.stage
    val kind = when {
        stage == SubscriptionFailure.Stage.PARSE -> SubscriptionErrorKind.PARSE
        stage == SubscriptionFailure.Stage.SAVE -> SubscriptionErrorKind.SAVE
        httpStatus != null -> SubscriptionErrorKind.HTTP
        names.any { it.contains("Timeout", true) } -> SubscriptionErrorKind.TIMEOUT
        names.any { it.contains("UnknownHost") || it.contains("UnresolvedAddress") } -> SubscriptionErrorKind.DNS
        names.any { it.contains("SSL") || it.contains("TLS") || it.contains("Certificate") } -> SubscriptionErrorKind.TLS
        stage == SubscriptionFailure.Stage.FETCH -> SubscriptionErrorKind.NETWORK
        else -> SubscriptionErrorKind.UNKNOWN
    }
    val parse = causes.filterIsInstance<SubscriptionParseFailure>().firstOrNull()
    val context = parse?.let { "step=${it.step}, node=${it.nodeIndex}, protocol=${it.protocol}" }.orEmpty()
    val reason = causes.firstNotNullOfOrNull { safeValidationReason(it.message) }.orEmpty()
    // Only JVM stack frames pass this grammar. Exception headers and arbitrary messages are excluded.
    val framePattern = Regex("""at [A-Za-z0-9_.$<>/]+\([A-Za-z0-9_.$ :\-]*\)""")
    val root = causes.last()
    val trace = root.stackTraceToString()
    // Remove the entire header, including any newlines in the message, before reading frames.
    val frames = if (trace.startsWith(root.toString())) trace.removePrefix(root.toString()).trimStart() else ""
    val locations = frames.lineSequence().map { it.trim() }
        .takeWhile { framePattern.matches(it) }.take(8).joinToString(" | ")
    return SubscriptionErrorDetails(kind, httpStatus, names.joinToString(" -> "), context, reason, locations)
}

private fun safeValidationReason(message: String?): String? {
    val fixedMessages = setOf(
        "Share link server is missing or invalid", "Share link server is missing",
        "Share link port must be between 1 and 65535", "Share link credential is missing",
        "VMess ID is required", "VMess server is required", "VMess port must be between 1 and 65535",
        "Shadowsocks server is required", "Shadowsocks port must be between 1 and 65535",
        "Invalid SS URL", "Invalid Shadowsocks IPv6 endpoint", "Invalid proxy URL: missing host",
        "REALITY public key (pbk) is required", "Hysteria2 obfs-password is required",
        "XHTTP extra must be a JSON object", "Unsupported share link scheme",
    )
    if (message in fixedMessages) return message
    return when {
        message?.startsWith("Illegal character") == true -> "Invalid URI character"
        message?.startsWith("Malformed escape pair") == true -> "Malformed URI escape"
        message?.startsWith("Unsupported protocol:") == true -> "Unsupported protocol"
        message?.startsWith("Unsupported transport:") == true -> "Unsupported transport"
        message?.startsWith("Unsupported security:") == true -> "Unsupported security"
        message?.startsWith("Unsupported Hysteria2 obfs:") == true -> "Unsupported Hysteria2 obfuscation"
        message?.startsWith("Missing or invalid int field:") == true -> "Missing or invalid integer field"
        else -> null
    }
}
