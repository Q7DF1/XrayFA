package com.android.xrayfa.dto

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription

fun NodeEntity.toDomain(): Node = Node(
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

fun Node.toEntity(): NodeEntity = NodeEntity(
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

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    mark = mark,
    url = url,
    preNodeId = preNodeId,
    nextNodeId = nextNodeId,
    isAutoUpdate = isAutoUpdate,
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    mark = mark,
    url = url,
    preNodeId = preNodeId,
    nextNodeId = nextNodeId,
    isAutoUpdate = isAutoUpdate,
)
