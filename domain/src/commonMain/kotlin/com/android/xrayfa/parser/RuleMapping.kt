package com.android.xrayfa.parser

import com.android.xrayfa.datastore.Rule
import com.android.xrayfa.model.RuleObject

fun Rule.toRuleObject(): RuleObject = RuleObject(
    domain = domain,
    ip = ip,
    port = port,
    sourcePort = sourcePort,
    localPort = localPort,
    network = network,
    source = source,
    sourceIP = sourceIP,
    user = user,
    vlessRoute = vlessRoute,
    inboundTag = inboundTag,
    protocol = protocol,
    attrs = attrs,
    outboundTag = outboundTag,
    balancerTag = balancerTag,
    ruleTag = ruleTag,
    domainMatcher = domainMatcher,
    type = type,
)

fun List<Rule>.toRuleObjects(): List<RuleObject> = map { it.toRuleObject() }
