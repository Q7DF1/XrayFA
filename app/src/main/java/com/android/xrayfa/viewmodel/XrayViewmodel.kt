package com.android.xrayfa.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.XrayBaseService
import com.android.xrayfa.model.Link
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.LinkRepository
import com.android.xrayfa.XrayBaseServiceManager
import com.android.xrayfa.XrayCoreManager
import com.android.xrayfa.common.repository.DEFAULT_DELAY_TEST_URL
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.common.repository.dataStore
import com.android.xrayfa.ui.DetailActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.jvm.java
import kotlin.math.log

class XrayViewmodel(
    private val linkRepository: LinkRepository,
    private val xrayBaseServiceManager: XrayBaseServiceManager,
    private val xrayCoreManager: XrayCoreManager,
    private val parserFactory: ParserFactory
): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"
        const val EXTRA_LINK = "com.android.xrayFA.EXTRA_LINK"
        const val EXTRA_PROTOCOL = "com.android.xrayFA.EXTRA_PROTOCOL"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val node: StateFlow<List<Node>> = _nodes

    private val _upSpeed = MutableStateFlow(0L)
    val upSpeed: StateFlow<Long> = _upSpeed.asStateFlow()

    private val _delay = MutableStateFlow(-1L)
    val delay = _delay.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()

    private val _downSpeed = MutableStateFlow(0L)
    val downSpeed: StateFlow<Long> = _downSpeed.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(XrayBaseService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _qrcodebitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodebitmap.asStateFlow()

    private val _deleteDialog = MutableStateFlow(false)
    val deleteDialog: StateFlow<Boolean> = _deleteDialog.asStateFlow()

    private val _notConfig = MutableStateFlow(false)
    val notConfig = _notConfig.asStateFlow()
    var deleteLinkId = -1

    private val _logList = MutableStateFlow<List<String>>(emptyList())
    val logList = _logList.asStateFlow()


    init {

        xrayBaseServiceManager.viewmodelTrafficCallback  = { pair ->
            _upSpeed.value = pair.first
            _downSpeed.value = pair.second
        }
        xrayBaseServiceManager.viewmodelStateCallback = { running ->
            _isServiceRunning.value = running
        }
        viewModelScope.launch {
            val links = linkRepository.allLinks.first() // 获取原始链接，不执行解析
            val parsedNodes = mutableListOf<Node>()

            // 后台线程逐条解析
            links.forEach { link ->
                val node = withContext(Dispatchers.Default) {
                    parserFactory.getParser(link.protocolPrefix).preParse(link)
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
        viewModelScope.launch {
            xrayBaseServiceManager.startXrayBaseService(context)
        }
    }

    fun stopV2rayService(context: Context) {

        xrayBaseServiceManager.stopXrayBaseService(context)
    }


    fun isServiceRunning(): Boolean {
        return XrayBaseService.isRunning
    }

    fun startDetailActivity(context: Context,id: Int) {
        viewModelScope.launch {
            val link = linkRepository.loadLinksById(id).first()
            val intent = Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_LINK, link.content)
                putExtra(EXTRA_PROTOCOL,link.protocolPrefix)
            }

            context.startActivity(intent)
        }
    }



    //link

    fun getAllLinks(): Flow<List<Link>> {
        return linkRepository.allLinks
    }

    fun getAllNodes(): Flow<List<Node>> {
        val allLinks = linkRepository.allLinks
        val nodes = allLinks.map { links ->
            links.map { link ->
                return@map parserFactory.getParser(link.protocolPrefix).preParse(link)
            }
        }.flowOn(Dispatchers.IO)

        return nodes
    }

    fun getNodeById(id: Int): Flow<Node> {
        val link = linkRepository.loadLinksById(id)
        return link.map {
            parserFactory.getParser(it.protocolPrefix).preParse(it)
        }
    }


    fun addLink(link: String) {
        // pre parse
        val protocolPrefix = link.substringBefore("://").lowercase()
        Log.i(TAG, "addLink: $protocolPrefix")
        if (protocolsPrefix.contains(protocolPrefix)) {
            val link0 =  Link(protocolPrefix = protocolPrefix, content = link)
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
                parserFactory.getParser(it.protocolPrefix).preParse(it)
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

    //delete dialog
    fun showDeleteDialog(id: Int) {
        _deleteDialog.value = true
        deleteLinkId = id
    }

    fun hideDeleteDialog() {
        _deleteDialog.value = false
        deleteLinkId = -1
    }

    fun deleteLinkByIdWithDialog() {
        deleteLinkById(deleteLinkId)
        hideDeleteDialog()
    }

    fun dismissDialog() {
        _qrcodebitmap.value = null
    }

    fun measureDelay(context: Context) {
        if (isServiceRunning()) {
            _testing.value = true
            viewModelScope.launch(Dispatchers.IO) {
            val url =
                context.dataStore.data.first()[SettingsKeys.DELAY_TEST_URL]?: DEFAULT_DELAY_TEST_URL
                _delay.value = xrayCoreManager.measureDelaySync(url)
                _testing.value = false
                Log.i(TAG, "measureDelay: ${_delay.value}")
            }
        }
    }

    /**
     * Logcat
     */
    fun getLogcatContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,tun2socks,AndroidRuntime,System.err")
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
                val log = process.inputStream.bufferedReader().readText().lines()
                val error = process.errorStream.bufferedReader().readText()
                if (error.isNotEmpty()) {
                    Log.e(TAG, "Logcat error: $error")
                }
                Log.i(TAG, "getLogcatContent: ${log.size}")
                _logList.value = log
            }catch (e: Exception) {
                Log.i(TAG, "getLogcatContent: ${e.message}")
            }
        }

    }
}

class XrayViewmodelFactory
@Inject constructor(
    private val repository: LinkRepository,
    private val xrayBaseServiceManager: XrayBaseServiceManager,
    private val xrayCoreManager: XrayCoreManager,
    private val parserFactory: ParserFactory
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XrayViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XrayViewmodel(
                repository,
                xrayBaseServiceManager,
                xrayCoreManager,
                parserFactory
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}