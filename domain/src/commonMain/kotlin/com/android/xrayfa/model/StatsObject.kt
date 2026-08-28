package com.android.xrayfa.model

import kotlinx.serialization.Serializable

@Serializable
data class StatsObject(
    val stats: Map<String, String> = emptyMap(),
)

@Serializable
class ReverseObject

@Serializable
class FakeDNSObject

@Serializable
class MetricsObject

@Serializable
class ObservatoryObject

@Serializable
class BurstObservatoryObject
