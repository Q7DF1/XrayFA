package com.android.xrayfa.dto

fun Link.toParseLinkInput(): ParseLinkInput = ParseLinkInput(
    id = id,
    protocolPrefix = protocolPrefix,
    content = content,
    subscriptionId = subscriptionId,
    selected = selected,
)

fun ParsedNode.toNode(): Node = Node(
    id = id,
    protocolPrefix = protocolPrefix,
    address = address,
    port = port,
    selected = selected,
    isPreNode = isPreNode,
    isNextNode = isNextNode,
    remark = remark,
    subscriptionId = subscriptionId,
    favorite = favorite,
    jsonData = jsonData,
    url = url,
    countryISO = countryISO,
)
