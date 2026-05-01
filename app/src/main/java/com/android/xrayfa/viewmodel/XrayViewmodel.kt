package com.android.xrayfa.viewmodel

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.core.XrayBaseServiceManager
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.common.repository.DEFAULT_DELAY_TEST_URL
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.common.repository.dataStore
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.utils.BarcodeUtils
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.jvm.java
import androidx.core.net.toUri
import android.widget.Toast
import com.android.xrayfa.BuildConfig
import com.android.xrayfa.R
import kotlinx.coroutines.withContext

import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.ui.navigation.NavigateDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class XrayViewmodel(
    private val repository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val xrayBaseServiceManager: XrayBaseServiceManager,
    private val xrayCoreManager: XrayCoreManager,
    private val parserFactory: ParserFactory,
    private val okHttp: OkHttpClient,
    private val subscriptionParser: SubscriptionParser
): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"
        const val EXTRA_URL = "com.android.xrayFA.EXTRA_URL"
        const val EXTRA_PRE_URL = "com.android.xrayFA.EXTRA_PRE_URL"
        const val EXTRA_NEXT_URL = "com.android.xrayFA.EXTRA_NEXT_URL"

        @Deprecated("parse url instead")
        const val EXTRA_PROTOCOL = "com.android.xrayFA.EXTRA_PROTOCOL"
        const val DELETE_ALL = -2
        const val DELETE_NONE = -1

        const val SUB_ALL = 0
        const val SUB_MANUAL = -1
        const val FAVORITE = -2
    }


    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSubscriptionId = MutableStateFlow(SUB_ALL)
    val selectedSubscriptionId: StateFlow<Int> = _selectedSubscriptionId.asStateFlow()

    private val _pendingRoute: MutableStateFlow<NavigateDestination?> = MutableStateFlow(null)
    val pendingRoute = _pendingRoute.asStateFlow()

    var measureJob: Job? = null
    val subscriptions: StateFlow<List<Subscription>> = subscriptionRepository.allSubscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val nodes: StateFlow<List<Node>> = combine(
        repository.allNodes,
        _searchQuery,
        _selectedSubscriptionId,
        repository.favorites
    ) { allNodes, query, subId,favorites ->
        val filteredBySub = if (subId == SUB_ALL) {
            allNodes
        } else if (subId == FAVORITE) {
            favorites
        } else allNodes.filter { it.subscriptionId == subId }

        if (query.isBlank()) {
            filteredBySub.reversed()
        } else {
            filteredBySub.reversed().filter { node ->
                node.remark?.contains(query, ignoreCase = true)?: false ||
                        node.url.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val queryNodes: StateFlow<List<Node>> = combine(
        repository.allNodes,
        _searchQuery,
        _selectedSubscriptionId
    ) { allNodes, query, subId ->
        if (!query.isBlank()) {
            val filteredBySub = if (subId == SUB_ALL) {
                allNodes
            } else {
                allNodes.filter { it.subscriptionId == subId }
            }
            filteredBySub.reversed().filter { node ->
                node.remark?.contains(query, ignoreCase = true)?: false ||
                        node.url.contains(query, ignoreCase = true)
            }
        } else emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allNodes: MutableList<Node> = mutableListOf()

    private val _upSpeed = MutableStateFlow(0.0)
    val upSpeed: StateFlow<Double> = _upSpeed.asStateFlow()

    private val _delay = MutableStateFlow(-1L)
    val delay = _delay.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()

    private val _downSpeed = MutableStateFlow(0.0)
    val downSpeed: StateFlow<Double> = _downSpeed.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(XrayBaseService.statusFlow.value)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _qrcodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodeBitmap.asStateFlow()

    private val _deleteDialog = MutableStateFlow(false)
    val deleteDialog: StateFlow<Boolean> = _deleteDialog.asStateFlow()

    private val _showNavigationBar = MutableStateFlow(true)
    val showNavigationBar  = _showNavigationBar.asStateFlow()

    private val _notConfig = MutableStateFlow(false)
    val notConfig = _notConfig.asStateFlow()
    var deleteLinkId = DELETE_NONE

    private val _logList = MutableStateFlow<List<String>>(emptyList())
    val logList = _logList.asStateFlow()

    private val _isLogcatRecording = MutableStateFlow(false)
    val isLogcatRecording = _isLogcatRecording.asStateFlow()

    private val _logcatDuration = MutableStateFlow(30L) // default 30s
    val logcatDuration = _logcatDuration.asStateFlow()

    private val _logcatCountdown = MutableStateFlow(0L)
    val logcatCountdown = _logcatCountdown.asStateFlow()

    private var logcatJob: Job? = null
    private var logcatProcess: Process? = null

    // Pre-compiled regex for log sanitization
    private val ipv4Pattern = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    private val ipv6Pattern = Regex("\\b(?:[A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}\\b")
    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    // Enhanced pattern for JSON and connection info:
    // Matches keys like "address", "pass", "user", "publicKey", "serverName", "id", "host", etc.
    // Handles both quoted ("key":"value") and unquoted (key: value) formats.
    private val sensitivePattern = Regex("(?i)\"?(address|pass|user|id|publicKey|serverName|host|dest|connecting to|vnext|servers)\"?[\\s:]+[\"\\[\\s]*([^\"\\s,\\]{}]+)[\"\\s]*", RegexOption.IGNORE_CASE)

    private fun sanitizeLog(line: String): String {
        var sanitized = line

        // 1. Mask Sensitive Key-Value pairs (especially for JSON config dumps)
        sanitized = sensitivePattern.replace(sanitized) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]

            // Whitelist for common local/system values
            if (value == "127.0.0.1" || value == "0.0.0.0" || value == "localhost" || 
                value.startsWith("com.android.xrayfa") || value.startsWith("android.") ||
                key.lowercase() == "vnext" // keep the key but value will be handled by nested patterns if needed
            ) {
                match.value
            } else {
                // Reconstruct the masked part preserving quotes if possible
                match.value.replace(value, "***.***.***")
            }
        }

        // 2. Mask IPv4 (excluding localhost) - catch any IP missed by key-value matching
        sanitized = ipv4Pattern.replace(sanitized) { match ->
            if (match.value == "127.0.0.1" || match.value == "0.0.0.0") match.value 
            else "***.***.***.***"
        }
        // 3. Mask IPv6
        sanitized = ipv6Pattern.replace(sanitized) { match ->
            if (match.value == "0:0:0:0:0:0:0:1" || match.value == "::1") match.value 
            else "****:****:****:****:****:****:****:****"
        }
        // 4. Mask UUIDs (Xray keys)
        sanitized = uuidPattern.replace(sanitized, "[REDACTED-UUID]")

        return sanitized
    }

    var shareUrl = ""


    init {

        xrayBaseServiceManager.viewmodelTrafficCallback  = { pair ->
            _upSpeed.value = pair.first
            _downSpeed.value = pair.second
        }
        viewModelScope.launch {
            XrayBaseService.statusFlow.collect {
                _isServiceRunning.value = it
            }
        }
    }


    suspend fun onSearch(query: String) {
        _searchQuery.value = query
    }

    fun selectSubscription(id: Int) {
        _selectedSubscriptionId.value = id
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

    fun addXrayConfigFromClipboard(context: Context) {

        val clipboardText  = getConfigFromClipboard(context)
        if (clipboardText.isBlank()) {
            return
        }
        Log.i(TAG, "addV2rayConfigFromClipboard: $clipboardText")
        // Split the text by comma or any whitespace (including spaces and newlines).
        // The Regex "[,\s]+" matches one or more commas or whitespace characters.
        // The filter function removes any empty strings from the resulting list.
        val urls = clipboardText.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        // Iterate through the valid links and add them individually
        for (url in urls) {
            Log.i(TAG, "addXrayConfigFromClipboard processing link: $url")
            addLink(url)
        }

    }


    fun startXrayService(context: Context) {
        viewModelScope.launch {
            xrayBaseServiceManager.startXrayBaseService()
        }
    }

    fun stopXrayService(context: Context) {
        xrayBaseServiceManager.stopXrayBaseService()
    }


    fun isServiceRunning(): Boolean {
        return XrayBaseService.statusFlow.value
    }



    //link

    fun getAllLinks(): Flow<List<Node>> {
        return repository.allNodes
    }

    fun addLink(link: String) {
        // pre parse
        viewModelScope.launch {
            val protocolPrefix = link.substringBefore("://").lowercase()
            Log.i(TAG, "addLink: ${protocolPrefix}")
            if (protocolsPrefix.contains(protocolPrefix)) {
                val link0 =  Link(protocolPrefix = protocolPrefix, content = link, subscriptionId = SUB_MANUAL)
                val node = parserFactory.getParser(link).preParse(link0)
                viewModelScope.launch {
                    Log.i(TAG, "addLink: ${link0}")
                    repository.addNode(node)
                }
            }else {
                //TODO
            }
        }
    }

    fun updateLinkById(id: Int, selected: Boolean) {
        viewModelScope.launch {
            repository.updateSelectById(id,selected)
        }
    }

    fun updateFavoriteById(id: Int, favorite: Boolean) {
        viewModelScope.launch {
            repository.updateFavoriteById(id, favorite)
        }
    }

    fun getSelectedNode(): Flow<Node?> {
        return repository.querySelectedNode()
    }



    fun setSelectedNode(id: Int) {
        viewModelScope.launch {
            if (id == repository.querySelectedNode().first()?.id) return@launch
            repository.clearSelection()
            repository.updateSelectById(id,true)
            onConfigChanged()
        }
    }

    suspend fun onConfigChanged() {
        xrayBaseServiceManager.restartXrayBaseServiceIfNeed()
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
            shareUrl = node?.url ?: ""
            val bitmap = BarcodeUtils.encodeBitmap(shareUrl, BarcodeFormat.QR_CODE,400,400)
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

    fun showNavigationBar() {
        _showNavigationBar.value = true
    }

    fun hideNavigationBar() {
        _showNavigationBar.value = false
    }

    fun deleteNodeFromDialog() {
        deleteNode(id = deleteLinkId)
        hideDeleteDialog()
    }

    fun dismissDialog() {
        _qrcodeBitmap.value = null
    }

    fun measureDelay(context: Context) {
        if (!isServiceRunning()) return
        
        measureJob?.cancel()
        _testing.value = true
        _delay.value = -1L // Reset display
        
        measureJob = viewModelScope.launch {
            val url = context.dataStore.data.first()[SettingsKeys.DELAY_TEST_URL] ?: DEFAULT_DELAY_TEST_URL
            val resultDeferred = CompletableDeferred<Long>()

            // 1. Start the actual test job
            val testJob = launch(Dispatchers.IO) {
                val res = try { xrayCoreManager.measureDelaySync(url) } catch (e: Exception) { -1L }
                resultDeferred.complete(res)
            }

            // 2. Start the 5s timer job
            val timeoutJob = launch {
                delay(5000L)
                resultDeferred.complete(-2L) // Force timeout result
            }

            // 3. Wait for the winner (first one to call complete())
            val finalResult = resultDeferred.await()
            
            // Clean up both jobs
            timeoutJob.cancel()
            testJob.cancel()

            _testing.value = false
            _delay.value = if (finalResult <= 0L) -2L else finalResult
        }
    }

    /**
     * Logcat
     */
    fun startLogcatRecording(context: Context) {
        if (_isLogcatRecording.value) return
        _isLogcatRecording.value = true
        _logList.value = emptyList()

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            val currentLogs = mutableListOf<String>()
            var lastUpdate = System.currentTimeMillis()

            try {
                val packageName = "com.android.xrayfa"
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningProcesses = am.runningAppProcesses
                var targetPid: Int? = null
                if (runningProcesses != null) {
                    for (processInfo in runningProcesses) {
                        if (processInfo.processName == packageName) {
                            targetPid = processInfo.pid
                            break
                        }
                    }
                }

                val lst = mutableListOf("logcat", "-v", "time")
                if (targetPid != null) {
                    lst.add("--pid")
                    lst.add(targetPid.toString())
                }
                // Removed restrictive tag filtering (-s) to ensure all app logs are captured
                
                val process = Runtime.getRuntime().exec(lst.toTypedArray())
                logcatProcess = process
                val reader = process.inputStream.bufferedReader()

                val duration = _logcatDuration.value
                var timerJob: Job? = null
                if (duration > 0) {
                    timerJob = launch {
                        for (i in duration downTo 1) {
                            _logcatCountdown.value = i
                            delay(1000)
                        }
                        stopLogcatRecording()
                    }
                }

                try {
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val sanitizedLine = sanitizeLog(line)
                            currentLogs.add(sanitizedLine)
                            if (currentLogs.size > 2000) currentLogs.removeAt(0)
                            
                            // Batch updates every 500ms to save CPU/UI
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val batch = currentLogs.toList()
                                withContext(Dispatchers.Main) {
                                    _logList.value = batch
                                }
                                lastUpdate = now
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed
                } finally {
                    timerJob?.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat recording error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    _logList.value = currentLogs.toList()
                    _isLogcatRecording.value = false
                    _logcatCountdown.value = 0
                    logcatProcess?.destroy()
                    logcatProcess = null
                }
            }
        }
    }

    fun stopLogcatRecording() {
        _isLogcatRecording.value = false
        logcatProcess?.destroy()
        logcatProcess = null
        logcatJob?.cancel()
        logcatJob = null
    }

    fun setLogcatDuration(duration: Long) {
        _logcatDuration.value = duration
    }

    override fun onCleared() {
        super.onCleared()
        stopLogcatRecording()
    }

    fun exportLogcatToClipboard(context: Context) {
        val log = _logList.value.joinToString(separator = "\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("log",log)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    fun setPaddingRoute(navigation: NavigateDestination?) {
        _pendingRoute.value = navigation
    }

    /**
     * @description: Bug report function
     * @param context context from Activity or Application
     */
    fun bugReport(context: Context) {
        val appVersion = BuildConfig.VERSION_NAME
        val androidVersion = Build.VERSION.RELEASE
        val deviceModel = Build.MODEL
        val issueBody = """
        ### Describe the bug
        ### Environment
        - **App Version:** $appVersion
        - **Android Version:** $androidVersion
        - **Device Model:** $deviceModel
        
        ### Additional Context(Bug description)
        """.trimIndent()

        try {
            val encodedBody = URLEncoder.encode(issueBody, "UTF-8")
            val repoUrl = "https://github.com/Q7DF1/XrayFA/issues/new"
            val fullUrl = "$repoUrl?title=[Bug]%20&body=$encodedBody&labels=bug"
            val intent = Intent(Intent.ACTION_VIEW, fullUrl.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class XrayViewmodelFactory
@Inject constructor(
    private val repository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
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
                subscriptionRepository,
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