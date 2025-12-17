package com.android.xrayfa.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.XrayBaseService
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.XrayBaseServiceManager
import com.android.xrayfa.XrayCoreManager
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.common.repository.DEFAULT_DELAY_TEST_URL
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.common.repository.dataStore
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.ui.DetailActivity
import com.android.xrayfa.ui.SubscriptionActivity
import com.android.xrayfa.utils.EventBus
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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.jvm.java

class XrayViewmodel(
    private val repository: NodeRepository,
    private val xrayBaseServiceManager: XrayBaseServiceManager,
    private val xrayCoreManager: XrayCoreManager,
    private val parserFactory: ParserFactory,
    private val okHttp: OkHttpClient,
    private val subscriptionParser: SubscriptionParser
): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"
        const val EXTRA_LINK = "com.android.xrayFA.EXTRA_LINK"
        const val EXTRA_PROTOCOL = "com.android.xrayFA.EXTRA_PROTOCOL"
        const val DELETE_ALL = -2
        const val DELETE_NONE = -1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes

    private val _upSpeed = MutableStateFlow(0.0)
    val upSpeed: StateFlow<Double> = _upSpeed.asStateFlow()

    private val _delay = MutableStateFlow(-1L)
    val delay = _delay.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()

    private val _downSpeed = MutableStateFlow(0.0)
    val downSpeed: StateFlow<Double> = _downSpeed.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(XrayBaseService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _qrcodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodeBitmap.asStateFlow()

    private val _deleteDialog = MutableStateFlow(false)
    val deleteDialog: StateFlow<Boolean> = _deleteDialog.asStateFlow()

    private val _notConfig = MutableStateFlow(false)
    val notConfig = _notConfig.asStateFlow()
    var deleteLinkId = DELETE_NONE

    private val _logList = MutableStateFlow<List<String>>(emptyList())
    val logList = _logList.asStateFlow()

    var shareUrl = ""


    init {

        xrayBaseServiceManager.viewmodelTrafficCallback  = { pair ->
            _upSpeed.value = pair.first
            _downSpeed.value = pair.second
        }
        viewModelScope.launch {
            EventBus.statusFlow.collect {
                _isServiceRunning.value = it
            }
        }

        viewModelScope.launch {
            repository.allLinks.flowOn(Dispatchers.IO).collect { _nodes.value = it }
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
            val link = repository.loadLinksById(id).first()
            val intent = Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_LINK, link.url)
                putExtra(EXTRA_PROTOCOL,link.protocolPrefix)
            }

            context.startActivity(intent)
        }
    }

    fun startSubscriptionActivity(context: Context) {
        val intent = Intent(context, SubscriptionActivity::class.java)
        context.startActivity(intent)
    }



    //link

    fun getAllLinks(): Flow<List<Node>> {
        return repository.allLinks
    }

    fun addLink(link: String) {
        // pre parse
        viewModelScope.launch {
            val protocolPrefix = link.substringBefore("://").lowercase()
            Log.i(TAG, "addLink: $protocolPrefix")
            if (protocolsPrefix.contains(protocolPrefix)) {
                val link0 =  Link(protocolPrefix = protocolPrefix, content = link, subscriptionId = 0)
                val node = parserFactory.getParser(protocolPrefix).preParse(link0)
                viewModelScope.launch {
                    Log.i(TAG, "addLink: $link0")
                    repository.addNode(node)
                }
            }else {
                //TODO
            }
        }
    }

    fun updateLinkById(id: Int, selected: Boolean) {
        viewModelScope.launch {
            repository.updateLinkById(id,selected)
        }
    }

    fun getSelectedNode(): Flow<Node?> {
        return repository.querySelectedLink()
    }



    fun setSelectedNode(id: Int) {
        viewModelScope.launch {

            repository.clearSelection()
            repository.updateLinkById(id,true)
        }
    }




    fun deleteNode(id: Int) = if (id == DELETE_ALL) deleteAllNodes() else deleteNodeById(id)

    fun deleteNodeById(id: Int) {
        viewModelScope.launch {
            repository.deleteLinkById(id)
        }
    }

    fun deleteAllNodes() {
        viewModelScope.launch {
            repository.deleteAllNodes()
        }
    }




    //barcode
    fun generateQRCode(id: Int) {
        viewModelScope.launch {
            val node = repository.loadLinksById(id).first()
            val barcodeEncoder = BarcodeEncoder()
            shareUrl = node.url
            val bitmap = barcodeEncoder.encodeBitmap(shareUrl, BarcodeFormat.QR_CODE,400,400)
            _qrcodeBitmap.value = bitmap
        }
    }
    //export clipboard
    fun exportConfigToClipboard(context: Context) {
        if (shareUrl == "") {
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText("label", shareUrl)
        clipboard.setPrimaryClip(clip)
        shareUrl == ""
    }

    //delete dialog
    fun showDeleteDialog(id: Int = DELETE_ALL) {
        _deleteDialog.value = true
        deleteLinkId = id
    }


    fun hideDeleteDialog() {
        _deleteDialog.value = false
        deleteLinkId = DELETE_NONE
    }

    fun deleteNodeFromDialog() {
        deleteNode(id = deleteLinkId)
        hideDeleteDialog()
    }

    fun dismissDialog() {
        _qrcodeBitmap.value = null
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
                Log.i(TAG, "getLogcatContent: ${log.size} ${log[0]}")
                _logList.value = log
            }catch (e: Exception) {
                Log.i(TAG, "getLogcatContent: ${e.message}")
            }
        }
    }

    fun exportLogcatToClipboard(context: Context) {
        val log = _logList.value.joinToString(separator = "\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("log",log)
        clipboard.setPrimaryClip(clip)
    }
}

class XrayViewmodelFactory
@Inject constructor(
    private val repository: NodeRepository,
    private val xrayBaseServiceManager: XrayBaseServiceManager,
    private val xrayCoreManager: XrayCoreManager,
    private val parserFactory: ParserFactory,
    @ShortTime private val okHttp: OkHttpClient,
    private val subscriptionParser: SubscriptionParser
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XrayViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XrayViewmodel(
                repository,
                xrayBaseServiceManager,
                xrayCoreManager,
                parserFactory,
                okHttp,
                subscriptionParser
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}