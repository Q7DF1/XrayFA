package com.android.xrayfa.model

import com.android.xrayfa.model.serialization.DnsObjectSerializer
import com.android.xrayfa.model.serialization.InboundObjectSerializer
import com.android.xrayfa.model.serialization.OutboundObjectSerializer
import kotlinx.serialization.Serializable

@Serializable
data class XrayConfiguration(
    val version: Version? = null,
    val log: LogObject? = null,
    val api: ApiObject? = null,
    @Serializable(with = DnsObjectSerializer::class)
    val dns: DnsObject? = null,
    val routing: RoutingObject? = null,
    val policy: PolicyObject? = null,
    val inbounds: List<@Serializable(with = InboundObjectSerializer::class) InboundObject>,
    val outbounds: List<@Serializable(with = OutboundObjectSerializer::class) OutboundObject<*>>,
    val stats: Map<String, String>? = null,
    val reverse: ReverseObject? = null,
    val fakedns: FakeDNSObject? = null,
    val metrics: MetricsObject? = null,
    val observatory: ObservatoryObject? = null,
    val burstObservatory: BurstObservatoryObject? = null,
    val remark: String? = null,
)
