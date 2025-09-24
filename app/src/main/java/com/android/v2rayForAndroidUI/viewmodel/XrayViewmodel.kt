package com.android.v2rayForAndroidUI.viewmodel

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.v2rayForAndroidUI.R
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
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
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
import java.util.concurrent.Executor

class XrayViewmodel(
    private val linkRepository: LinkRepository
): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"

        const val MSG_TRAFFIC_DETECTION = 1
        const val MSG_RUNNING_STATE_NOTIFY = 2
        const val EXTRA_LINK = "com.android.xrayFA.EXTRA_LINK"
        const val EXTRA_PROTOCOL = "com.android.xrayFA.EXTRA_PROTOCOL"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val node: StateFlow<List<Node>> = _nodes

    val _upSpeed = MutableStateFlow(0L)
    val upSpeed: StateFlow<Long> = _upSpeed.asStateFlow()

    val _downSpeed = MutableStateFlow(0L)
    val downSpeed: StateFlow<Long> = _downSpeed.asStateFlow()

    val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    val _qrcodebitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodebitmap.asStateFlow()
    val handlerThread = HandlerThread("XrayViewmodel").apply {
        start()
    }
    val H =object: Handler(handlerThread.looper) {
        override fun handleMessage(msg: Message) {
            when(msg.what) {
                MSG_TRAFFIC_DETECTION -> {
                    _upSpeed.value = msg.arg1.toLong()
                    _downSpeed.value = msg.arg2.toLong()
                }

                else -> throw RuntimeException("Unknown message type: ${msg.what}")
            }
            super.handleMessage(msg)
        }
    }
    init {
        viewModelScope.launch {
            val links = linkRepository.allLinks.first() // 获取原始链接，不执行解析
            val parsedNodes = mutableListOf<Node>()

            // 后台线程逐条解析
            links.forEach { link ->
                val node = withContext(Dispatchers.Default) {
                    ParserFactory.getParser(link.protocol).preParse(link)
                }
                parsedNodes.add(node)
                _nodes.value = parsedNodes.toList() // 每条解析完就更新
            }
        }
    }


    val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            val binder = (service as V2rayBaseService.LocalBinder).getService()
            binder.H = H
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
        viewModelScope.launch {
            val first = linkRepository.querySelectedLink().first()
            withContext(Dispatchers.Main) {
                if (first == null) {
                    //
                    Toast.makeText(context, R.string.config_not_ready, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val intent = Intent(context, V2rayBaseService::class.java).apply {
                    action = "connect"
                    putExtra(EXTRA_LINK,first.content)
                    putExtra(EXTRA_PROTOCOL, first.protocol)
                }
                context.startForegroundService(intent)
                Log.i(TAG, "startV2rayService: bind")
                context.bindService(
                    Intent(context, V2rayBaseService::class.java),
                    serviceConnection,
                    BIND_AUTO_CREATE
                )
                _isServiceRunning.value = true
            }
        }
    }

    fun stopV2rayService(context: Context) {

        val intent = Intent(context, V2rayBaseService::class.java).apply {
            action = "disconnect"
        }
        context.unbindService(serviceConnection)
        context.startService(intent)
        _isServiceRunning.value = false
    }


    fun isServiceRunning(): Boolean {
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
                return@map ParserFactory.getParser(link.protocol).preParse(link)
            }
        }.flowOn(Dispatchers.IO)

        return nodes
    }

    fun getNodeById(id: Int): Flow<Node> {
        val link = linkRepository.loadLinksById(id)
        return link.map {
            ParserFactory.getParser(it.protocol).preParse(it)
        }
    }

    fun addLink(link: String) {
        // pre parse
        val protocolName = link.substringBefore("://").lowercase()
        Log.i(TAG, "addLink: $protocolName")
        Log.i(TAG, "addLink: $protocols")
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

    fun updateLink(link: Link) {
        viewModelScope.launch {
            linkRepository.updateLink(link)
        }
    }

    fun updateLinkById(id: Int, selected: Boolean) {
        viewModelScope.launch {
            linkRepository.updateLinkById(id,selected)
        }
    }

    fun getSelectedNode(): Flow<Node?> {
        return linkRepository.querySelectedLink().map {
            it?.let {
                ParserFactory.getParser(it.protocol).preParse(it)
            }
        }
    }



    fun setSelectedNode(id: Int) {
        viewModelScope.launch {

            linkRepository.clearSelection()
            linkRepository.updateLinkById(id,true)
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


    //barcode
    fun generateQRCode(id: Int) {
        viewModelScope.launch {
            val link = linkRepository.loadLinksById(id).first()
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(link.content, BarcodeFormat.QR_CODE,400,400)
            _qrcodebitmap.value = bitmap
        }
    }

    fun dismissDialog() {
        _qrcodebitmap.value = null
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