package com.android.v2rayForAndroidUI.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.v2rayForAndroidUI.V2rayBaseService
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.model.Link
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.protocol.protocols
import com.android.v2rayForAndroidUI.parser.ParserFactory
import com.android.v2rayForAndroidUI.parser.VLESSConfigParser
import com.android.v2rayForAndroidUI.repository.LinkRepository
import com.android.v2rayForAndroidUI.rpc.XrayStatsClient
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel.Companion.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class XrayViewmodel(
    private val linkRepository: LinkRepository
): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val node: StateFlow<List<Node>> = _nodes

    val _upSpeed = MutableStateFlow(0L)
    val upSpeed: StateFlow<Long> = _upSpeed.asStateFlow()
    val _downSpeed = MutableStateFlow(0L)
    val downSpeed: StateFlow<Long> = _downSpeed.asStateFlow()

    init {
        viewModelScope.launch {
            val links = linkRepository.allLinks.first() // 获取原始链接，不执行解析
            val parsedNodes = mutableListOf<Node>()

            // 后台线程逐条解析
            links.forEach { link ->
                val node = withContext(Dispatchers.Default) {
                    ParserFactory.getParser(link.protocol).preParse(link.content,link.id)
                }
                parsedNodes.add(node)
                _nodes.value = parsedNodes.toList() // 每条解析完就更新
            }
        }
    }


    fun getConfigFromClipboard(context: Context):String {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = clipboard.primaryClip

        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).coerceToText(context).toString()
        }else {
            ""
        }
    }

    fun addV2rayConfigFromClipboard(context: Context) {

        val link = getConfigFromClipboard(context)
        if (link == "") {
            return
        }
        Log.i(TAG, "addV2rayConfigFromClipboard: $link")
        addLink(link)

    }


    fun startV2rayService(context: Context) {

        val intent = Intent(context, V2rayBaseService::class.java).apply {
            action = "connect"
        }
        context.startForegroundService(intent)
        startTrafficDetection()
    }

    fun startTrafficDetection() {
        viewModelScope.launch {
            while (!isV2rayServiceRunning()) {
               delay(2000)
            }
            val client = XrayStatsClient()
            client.connect()
            var lastUp = 0L
            var lastDown = 0L
            while (XrayStatsClient.isConnect) {
                val (uplink, downlink) = client.getTraffic("proxy")
                _upSpeed.value = (uplink - lastUp) / 1024
                _downSpeed.value = (downlink - lastDown) / 1024
                lastUp = uplink
                lastDown = downlink
                delay(1000)

            }
        }
    }

    fun stopTrafficDetection() {
        val client = XrayStatsClient()
        client.shutdown()
    }

    fun stopV2rayService(context: Context) {

        stopTrafficDetection()

        val intent = Intent(context, V2rayBaseService::class.java).apply {
            action = "disconnect"
        }
        context.startService(intent)
    }


    fun isV2rayServiceRunning():Boolean {
        return V2rayBaseService.isRunning
    }


    //link

    fun getAllLinks(): Flow<List<Link>> {
        return linkRepository.allLinks
    }

    fun getAllNodes(): Flow<List<Node>> {
        val allLinks = linkRepository.allLinks
        val nodes = allLinks.map { links ->
            links.map { link ->
                return@map ParserFactory.getParser(link.protocol).preParse(link.content,link.id)
            }
        }.flowOn(Dispatchers.IO)

        return nodes
    }

    fun addLink(link: String) {
        // pre parse
        val protocolName = link.substringBefore("://").lowercase()
        if (protocols.contains(protocolName)) {
            val link0 =  Link(protocol = protocolName, content = link)
            viewModelScope.launch {
                Log.i(TAG, "addLink: $link0")
                linkRepository.addLink(link0)
            }
        }else {
            //TODO
        }
    }


    fun deleteLink(link: Link) {
        viewModelScope.launch {
            linkRepository.deleteLink(link)
        }
    }

    fun deleteLinkById(id: Int) {
        viewModelScope.launch {
            linkRepository.deleteLinkById(id)
        }
    }

    fun deleteLinkByIdWithCallback(id: Int, callback: () -> Unit) {
        callback()
        deleteLinkById(id)
    }

}

class XrayViewmodelFactory(private val repository: LinkRepository): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XrayViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XrayViewmodel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}