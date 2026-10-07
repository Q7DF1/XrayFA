package com.android.xrayfa.shared.config

import com.android.xrayfa.config.*
import com.android.xrayfa.model.*
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.ConfigFilterIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class JsonConfigEditor(private val repository: NodeRepository) {
    private val mutex = Mutex()

    suspend fun save(id: Int, name: String, text: String, inboundTag: String?) = mutex.withLock {
        JsonVpnConfig.inspect(text)
        val existing = if (id > 0) repository.loadLinksById(id).first() else null
        if (id > 0 && existing?.isJsonConfig != true) throw JsonConfigException(JsonConfigError.INVALID_CONFIG)
        val key = existing?.url?.substringAfter("://")?.substringBefore("?") ?: Random.nextLong().toULong().toString(16)
        val identifier = jsonConfigIdentifier(key, inboundTag)
        if (existing == null) repository.addNode(Node(
            protocolPrefix = JSON_CONFIG_PREFIX, address = "", port = 0,
            remark = name.trim(), subscriptionId = ConfigFilterIds.SUB_MANUAL,
            jsonData = text, url = identifier,
        )) else repository.updateJsonConfig(id, identifier, name.trim(), text)
    }
}
