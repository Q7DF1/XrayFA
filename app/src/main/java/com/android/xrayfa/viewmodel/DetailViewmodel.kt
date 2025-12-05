package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ShadowSocksOutboundConfigurationObject
import com.android.xrayfa.model.TrojanOutboundConfigurationObject
import com.android.xrayfa.model.VMESSOutboundConfigurationObject
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.VLESSConfigParser
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
        return (parserFactory.getParser("vless") as VLESSConfigParser).decodeVLESS(content)
    }

    fun saveVLESSModify(id: Int,config: VLESSConfigParser.VLESSConfig) {
        val newUrl = (parserFactory.getParser(Protocol.VLESS.protocolName)
                as VLESSConfigParser).encodeVLESS(config)

        viewModelScope.launch(Dispatchers.IO) {
            nodeRepository.updateNodeUrlAndPort(id,newUrl,config.port)
        }
    }
    
    fun parseVMESSProtocol(content: String): OutboundObject<VMESSOutboundConfigurationObject> {
        return parseProtocol("vmess",content)
    }

    fun parseTROJANProtocol(content: String): OutboundObject<TrojanOutboundConfigurationObject> {
        return parseProtocol("trojan",content)
    }

    fun parseSHADOWSOCKSProtocol(
        content: String
    ): OutboundObject<ShadowSocksOutboundConfigurationObject> {
        return parseProtocol("ss",content)
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