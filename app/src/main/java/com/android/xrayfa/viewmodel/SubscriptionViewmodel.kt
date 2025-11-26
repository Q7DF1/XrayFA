package com.android.xrayfa.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.TAG
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

val emptySubscription = Subscription(0,"","")


class SubscriptionViewmodel(
    val repository: SubscriptionRepository,
    val okHttp: OkHttpClient,
    val nodeRepository: NodeRepository,
    val subscriptionParser: SubscriptionParser,
    val parserFactory: ParserFactory
): ViewModel() {

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions = _subscriptions.asStateFlow()


    private val _selectSubscription = MutableStateFlow<Subscription>(emptySubscription)
    val selectSubscription = _selectSubscription.asStateFlow()

    private val _deleteDialog = MutableStateFlow(false)
    val deleteDialog: StateFlow<Boolean> = _deleteDialog.asStateFlow()

    private val _subscribeError = MutableStateFlow(false)
    val subscribeError: StateFlow<Boolean> = _subscribeError.asStateFlow()

    var deleteSubscription = emptySubscription

    private val _requestingSubscription = MutableStateFlow(false)
    val requesting = _requestingSubscription.asStateFlow()

    private val _qrcodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodeBitmap.asStateFlow()

    var shareUrl: String? = null
    init {

        viewModelScope.launch(Dispatchers.IO) {
            repository.allSubscriptions.collect {
                _subscriptions.value = it
            }
        }
    }

    fun addSubscription(subscription: Subscription) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSubscription(subscription)
        }
    }

    fun showDeleteDialog(subscription: Subscription) {
        deleteSubscription = subscription
        _deleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        deleteSubscription = emptySubscription
        _deleteDialog.value = false
    }


    fun addOrUpdateSubscription(subscription: Subscription) {
        viewModelScope.launch(Dispatchers.IO) {
            if (subscription.id == 0) {
                repository.addSubscription(subscription)
            } else {
                repository.updateSubscription(subscription)
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSubscription(subscription)
        }
    }

    fun deleteSubscriptionWithDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSubscription(deleteSubscription)
            dismissDeleteDialog()
        }
    }

    fun getSubscriptionById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectSubscription.value = repository.getSubscriptionById(id).first()
        }
    }

    fun getSubscriptionByIdWithCallback(id: Int, callback: () -> Unit) {

        viewModelScope.launch(Dispatchers.IO) {
            val subscription = repository.getSubscriptionById(id).first()
            _selectSubscription.value = subscription
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun setSelectSubscriptionEmpty() {
        _selectSubscription.value = emptySubscription
    }


    fun generateQRCode(id: Int) {
        viewModelScope.launch {
            shareUrl = repository.getSubscriptionById(id).first().url
            shareUrl?.let {
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(it, BarcodeFormat.QR_CODE,400,400)
                _qrcodeBitmap.value = bitmap
            }
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

    fun dismissQRCode() {
        _qrcodeBitmap.value = null
    }


    fun getSubscriptionWithCallback(url: String, subscriptionId: Int,callback: () -> Unit) {
        val request = Request.Builder()
            .get()
            .url(url)
            .build()
        viewModelScope.launch(Dispatchers.IO) {
            _requestingSubscription.value = true
            try {
                val response = okHttp.newCall(request)
                    .execute()

                if (response.isSuccessful) {
                    val content = response.body?.string() ?: ""
                    if (content != "") {
                        val urls = subscriptionParser.parseUrl(content)
                        nodeRepository.deleteLinkBySubscriptionId(subscriptionId)
                        val newNodes = urls.map {
                            Log.i(TAG, "getSubscription: ${it.substringBefore("://")}")
                            Log.i(TAG, "getSubscription: $it")
                            val link = Link(
                                protocolPrefix = it.substringBefore("://"),
                                content = it,
                                selected = false,
                                subscriptionId = subscriptionId,
                            )
                            val node =
                                parserFactory.getParser(link.protocolPrefix).preParse(link)
                            Log.i(TAG, "getSubscriptionWithCallback: $node")
                            node
                        }
                        nodeRepository.addNode(*newNodes.toTypedArray())
                    }
                    callback()
                }
            }catch (e: Exception) {
                launch {
                    _subscribeError.value = true
                    delay(2000L)
                    _subscribeError.value = false
                }

                Log.e(TAG, "getSubscription: ${e.message}", )
            }finally {
                _requestingSubscription.value = false
            }
        }
    }
}

class SubscriptionViewmodelFactory
@Inject constructor(
    val repository: SubscriptionRepository,
    @ShortTime val okHttp: OkHttpClient,
    val nodeRepository: NodeRepository,
    val subscriptionParser: SubscriptionParser,
    val parserFactory: ParserFactory
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewmodel::class.java)) {
            return SubscriptionViewmodel(repository,
                okHttp,
                nodeRepository,
                subscriptionParser,
                parserFactory
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
