package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.ShadowSocksConfigParser
import com.android.xrayfa.parser.TrojanConfigParser
import com.android.xrayfa.parser.VLESSConfigParser
import com.android.xrayfa.parser.VMESSConfigParser
import com.android.xrayfa.repository.NodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailViewmodel(
    val parserFactory: ParserFactory,
    val nodeRepository: NodeRepository,
): ViewModel() {




    private fun <T: AbsOutboundConfigurationObject> parseProtocol(
        protocol: String,
        content: String
    ): OutboundObject<T> {
        @Suppress("UNCHECKED_CAST")
        return parserFactory.getParser(protocol).parseOutbound(content) as OutboundObject<T>
    }

    fun parseVLESSProtocol(content: String): VLESSConfigParser.VLESSConfig {
        return (parserFactory.getParser(Protocol.VLESS.protocolType) as VLESSConfigParser)
            .decodeVLESS(content)
    }

    fun parseVMESSProtocol(content: String): VMESSConfigParser.VMESSConfig {
        return (parserFactory.getParser(Protocol.VMESS.protocolType) as VMESSConfigParser)
            .decodeVMESS(content)
    }

    fun parseTrojanProtocol(content:String): TrojanConfigParser.TrojanConfig {
        return (parserFactory.getParser(Protocol.TROJAN.protocolType) as TrojanConfigParser)
            .decodeTrojan(content)
    }
    fun parseShadowSocks(content:String): ShadowSocksConfigParser.ShadowSocksConfig {
        return (parserFactory.getParser(Protocol.SHADOW_SOCKS.protocolType) as ShadowSocksConfigParser)
            .decodeShadowSocks(content)
    }

}


class DetailViewmodelFactory
@Inject constructor(
    val parserFactory: ParserFactory,
    val nodeRepository: NodeRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewmodel(parserFactory,nodeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}