package com.android.xrayfa.cli

import kotlinx.serialization.json.*

/**
 * Reject untrusted stored values before any process/network work, without logging parser errors.
 */
internal fun prepareStoredNode(
    record: JsonObject,
    parser: NodeParser,
    log: LogSession,
): ValidatedNode {
    val link = record.getValue("url").jsonPrimitive.content
    val outbound =
        try {
            parser.outbound(link)
        } catch (_: Exception) {
            fail(ExitCode.CONFIGURATION, "Stored node/link validation failed")
        }
    log.secrets = secretValues(outbound) + link
    return try {
        ValidatedNode.from(record, outbound)
    } catch (_: Exception) {
        fail(ExitCode.CONFIGURATION, "Stored protocol or endpoint does not match parsed outbound")
    }
}
