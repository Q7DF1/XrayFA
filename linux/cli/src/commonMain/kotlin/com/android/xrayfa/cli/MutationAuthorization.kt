package com.android.xrayfa.cli

import kotlinx.serialization.json.*

/** Authorize exact argv from durable resource intent, never a partially matching command. */
internal fun authorizeMutation(
    journal: RecoveryJournal,
    helper: PendingTool,
    ip: String,
    nft: String,
    resolve: String,
    legacyResolve: Boolean,
) {
    val record = journal.json
    val policy = journal.policy
    val argv = helper.argv
    val allowed =
        when (helper.path) {
            ip -> {
                val management =
                    (record["managementRoutesAfter"] ?: record["managementRouteIntent"])
                        ?.jsonArray
                        .orEmpty()
                        .flatMap { element ->
                            val route = element.jsonObject
                            require(route.getValue("owned").jsonPrimitive.boolean)
                            val destination = route.getValue("destination").jsonPrimitive.content
                            val device = route.getValue("device").jsonPrimitive.content
                            val gateway = route["gateway"]?.jsonPrimitive?.content
                            listOf("add", "del").map { action ->
                                listOf(ip, "route", action, "$destination/32") +
                                    (gateway?.let { listOf("via", it) } ?: emptyList()) +
                                    listOf("dev", device)
                            }
                        }
                val tunIntent = record["tunIntent"]?.jsonObject
                val tun =
                    if (tunIntent == null) emptyList()
                    else {
                        require(
                            tunIntent.getValue("interface").jsonPrimitive.content ==
                                policy.interfaceName
                        )
                        require(
                            tunIntent.getValue("address").jsonPrimitive.content ==
                                policy.gatewayCidr
                        )
                        require(
                            tunIntent.getValue("routes").jsonArray.map {
                                it.jsonPrimitive.content
                            } == policy.routes
                        )
                        listOf(
                            listOf(
                                ip,
                                "address",
                                "add",
                                policy.gatewayCidr,
                                "dev",
                                policy.interfaceName,
                            )
                        ) +
                            policy.routes.map {
                                listOf(ip, "route", "add", it, "dev", policy.interfaceName)
                            }
                    }
                management + tun
            }
            nft ->
                listOf(
                    listOf(
                        nft,
                        "-f",
                        record
                            .getValue("configPath")
                            .jsonPrimitive
                            .content
                            .substringBeforeLast('/') + "/ipv6.nft",
                    ),
                    listOf(
                        nft,
                        "delete",
                        "table",
                        "inet",
                        record.getValue("nftTable").jsonPrimitive.content,
                    ),
                )
            resolve -> {
                val tun = record.getValue("tunAfter").jsonObject
                val index =
                    tun.getValue("ifindex").jsonPrimitive.int.also { require(it > 0) }.toString()
                val name = tun.getValue("ifname").jsonPrimitive.content
                require(name == policy.interfaceName)
                if (legacyResolve)
                    listOf(
                        listOf(resolve, "--set-dns=${policy.dns}", "--interface=$name"),
                        listOf(resolve, "--set-domain=~.", "--interface=$name"),
                        listOf(resolve, "--revert", "--interface=$name"),
                    )
                else
                    listOf(
                        listOf(resolve, "dns", index, policy.dns),
                        listOf(resolve, "domain", index, "~."),
                        listOf(resolve, "default-route", index, "true"),
                        listOf(resolve, "revert", index),
                    )
            }
            else -> emptyList()
        }
    require(argv in allowed) { "Helper argv does not match journal intent" }
}
