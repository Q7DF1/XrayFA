package com.android.xrayfa.model

data class XrayConfiguration(
    val version: Version? = null,
    val log: LogObject? = null,
    val api: ApiObject? = null,
    val dns: DnsObject? = null,
    val routing: RoutingObject? = null,
    val policy: PolicyObject?= null,
    val inbounds: List<InboundObject>,
    val outbounds: List<OutboundObject>,

    val stats: Map<String,String>? = null,
    val reverse: ReverseObject? = null,
    val fakedns: FakeDNSObject? = null,
    val metrics: MetricsObject? = null,
    val observatory: ObservatoryObject? = null,
    val burstObservatory: BurstObservatoryObject? = null,
    val remark: String? = null
)