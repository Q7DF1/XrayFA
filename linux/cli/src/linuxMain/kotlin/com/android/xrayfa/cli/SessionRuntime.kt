@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.serialization.json.*
import platform.posix.*

internal fun awaitTunReadiness(
    core: RunningCore,
    lease: NetworkLease,
    log: LogSession,
    policy: TunPolicy,
): JsonObject {
    val deadline = xf_millis() + 10000
    var tun: JsonObject? = null
    var tunConfigured = false
    while (xf_millis() < deadline && xf_interrupted() == 0) {
        core.tick()
        if (core.exitCode != null)
            fail(ExitCode.EXECUTION, "Core exited before readiness; core_exit=${core.exitCode}")
        if (!tunConfigured && tunLinkIdentity() != null) {
            ensureTunNetwork(lease, log)
            tunConfigured = true
        }
        tun = tunIdentity()
        if (tun != null && policy.ready(tun, ipJson("route", "show").map { it.jsonObject })) break
        tun = null
        usleep(20000u)
    }
    if (xf_interrupted() != 0) throw RuntimeStop()
    if (tun == null) fail(ExitCode.EXECUTION, "TUN readiness deadline exceeded")
    if (!policy.ready(tun, ipJson("route", "show").map { it.jsonObject }))
        fail(ExitCode.EXECUTION, "Unexpected TUN parameters or routes")
    lease.advance(SessionPhase.TUN_READY, JournalField.TUN_AFTER to tun)
    lease.update(
        JournalField.ROUTES_AFTER to ipJson("route", "show", "table", "all"),
        JournalField.RULES_AFTER to ipJson("rule"),
    )
    return tun
}

internal fun verifyVirtualDnsPath() {
    val dns = command("/usr/bin/getent", "ahostsv4", "example.com")
    if (dns.isBlank()) fail(ExitCode.EXECUTION, "Virtual DNS data path returned no address")
}

internal fun configureSessionResolver(lease: NetworkLease, policy: TunPolicy) {
    lease.advance(SessionPhase.RESOLVER_INTENT, JournalField.RESOLVER_INTENT to JsonPrimitive(true))
    val backend = resolverSnapshot().getValue("backend").jsonPrimitive.content
    if (backend == "static-resolv.conf") setSessionDns(lease, "dns", policy.dns)
    else
        for ((kind, value) in
            listOf("dns" to policy.dns, "domain" to "~.", "default-route" to "true")) setSessionDns(
            lease,
            kind,
            value,
        )
    val resolver = resolverSnapshot()
    if (backend == "static-resolv.conf") {
        if (
            resolver.getValue("nameservers").jsonArray.none {
                it.jsonPrimitive.content == policy.dns
            }
        )
            fail(ExitCode.EXECUTION, "Static resolver did not select virtual DNS")
    } else {
        val tunForResolver =
            tunIdentity()
                ?: fail(ExitCode.EXECUTION, "TUN disappeared before resolver verification")
        val link =
            resolverStructured(resolver)
                .getValue("links")
                .jsonObject
                .getValue(tunForResolver.getValue("ifindex").jsonPrimitive.content)
                .jsonObject
        if (
            policy.dns !in link.getValue("dns").jsonArray.map { it.jsonPrimitive.content } ||
                "~." !in link.getValue("domains").jsonArray.map { it.jsonPrimitive.content }
        )
            fail(ExitCode.EXECUTION, "System resolver did not select virtual DNS")
    }
    lease.advance(SessionPhase.RUNNING)
}

internal fun superviseSession(
    core: RunningCore,
    lease: NetworkLease,
    log: LogSession,
    plan: NetworkPlan,
    tun: JsonObject,
) {
    var inspected = xf_millis()
    while (xf_interrupted() == 0) {
        core.tick()
        if (core.exitCode != null)
            fail(ExitCode.EXECUTION, "Core exited unexpectedly; core_exit=${core.exitCode}")
        if (log.failed) fail(ExitCode.LOG, "Runtime log persistence failed")
        if (xf_millis() - inspected >= 500) {
            if (
                normalizedNetwork(tunIdentity() ?: fail(ExitCode.EXECUTION, "TUN disappeared")) !=
                    normalizedNetwork(tun)
            )
                fail(ExitCode.EXECUTION, "TUN identity/parameters changed")
            if (nftState(plan.table) != lease.record["nftAfter"])
                fail(ExitCode.EXECUTION, "IPv6 protection changed")
            if (resolverSnapshot() != lease.record["resolverAfter"])
                fail(ExitCode.EXECUTION, "System resolver configuration changed")
            if (
                normalizedNetwork(ipJson("route", "show", "table", "all")) !=
                    normalizedNetwork(lease.record.getValue("routesAfter"))
            )
                fail(ExitCode.EXECUTION, "Routing configuration changed")
            if (ipJson("rule") != lease.record["rulesAfter"])
                fail(ExitCode.EXECUTION, "Routing rules changed")
            inspected = xf_millis()
        }
        usleep(10000u)
    }
}
