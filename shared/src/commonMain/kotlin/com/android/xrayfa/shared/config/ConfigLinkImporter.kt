package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.ConfigFilterIds
import com.android.xrayfa.shared.platform.ClipboardReader

class ConfigLinkImporter(
    private val nodeRepository: NodeRepository,
    private val parserFactory: ParserFactory,
    private val clipboardReader: ClipboardReader,
    private val logger: Logger,
) {
    suspend fun importFromClipboard() {
        val clipboardText = clipboardReader.readText()
        if (clipboardText.isBlank()) {
            return
        }
        clipboardText
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .forEach { link ->
                addLink(link)
            }
    }

    suspend fun addLink(link: String) {
        val protocolPrefix = link.substringBefore("://").lowercase()
        if (!protocolsPrefix.contains(protocolPrefix)) {
            logger.i(TAG, "Unsupported protocol prefix: $protocolPrefix")
            return
        }
        val input =
            ParseLinkInput(
                protocolPrefix = protocolPrefix,
                content = link,
                subscriptionId = ConfigFilterIds.SUB_MANUAL,
            )
        val node = parserFactory.getParser(link).preParse(input)
        nodeRepository.addNode(node)
    }

    private companion object {
        const val TAG = "ConfigLinkImporter"
    }
}
