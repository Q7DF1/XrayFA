package com.android.xrayfa.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.utils.BarcodeUtils
import com.android.xrayfa.utils.SubscriptionUserInfo
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.TAG
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.android.xrayfa.utils.LinkUtils
import com.android.xrayfa.utils.SubscriptionMeta

val emptySubscription = Subscription(0, "", "", -1, -1, false)

class SubscriptionViewmodel(
    val repository: SubscriptionRepository,
    val nodeRepository: NodeRepository,
) : ViewModel() {

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions = _subscriptions.asStateFlow()

    private val _selectSubscription = MutableStateFlow<Subscription>(emptySubscription)
    val selectSubscription = _selectSubscription.asStateFlow()

    private val _nodes: MutableStateFlow<Flow<List<Node>>> =
        MutableStateFlow(nodeRepository.allNodes)
    val nodes: StateFlow<Flow<List<Node>>> = _nodes.asStateFlow()

    private val _deleteDialog = MutableStateFlow(false)
    val deleteDialog: StateFlow<Boolean> = _deleteDialog.asStateFlow()

    private val _subscribeError = MutableStateFlow(false)
    val subscribeError: StateFlow<Boolean> = _subscribeError.asStateFlow()

    var deleteSubscription = emptySubscription

    private val _requestingSubscription = MutableStateFlow(false)
    val requesting = _requestingSubscription.asStateFlow()

    private val _qrcodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrcodeBitmap.asStateFlow()

    private val _subscriptionMeta = MutableStateFlow<SubscriptionMeta?>(null)
    val subscriptionMeta: StateFlow<SubscriptionMeta?> = _subscriptionMeta.asStateFlow()

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

    fun addOrUpdateSubscription(
        subscription: Subscription,
        onSuccess: () -> Unit = {}
    ) {
        if (subscription.id == 0) {
            refreshSubscription(
                url = subscription.url,
                subscriptionId = 0
            ) {
                val mark = if(subscription.mark.isNullOrEmpty()){
                    it.profileTitle
                } else {
                    subscription.mark
                }

                _subscriptionMeta.value?.profileTitle = mark

                val sub = Subscription(
                    id = subscription.id,
                    url = subscription.url,
                    mark = mark,
                    preNodeId = subscription.preNodeId,
                    nextNodeId = subscription.nextNodeId,
                    isAutoUpdate = subscription.isAutoUpdate
                )
                viewModelScope.launch(Dispatchers.IO) {
                    repository.addSubscription(sub)
                }
                onSuccess()
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
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

    fun getSubscriptionByIdWithCallback(id: Int, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val subscription = repository.getSubscriptionById(id).first()
            _selectSubscription.value = subscription ?: emptySubscription
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
            shareUrl = repository.getSubscriptionById(id).first()?.url?.let {
                LinkUtils.cleanUrlForSharing(it)
            }
            shareUrl?.let {
                val bitmap = BarcodeUtils.encodeBitmap(it, BarcodeFormat.QR_CODE, 400, 400)
                _qrcodeBitmap.value = bitmap
            }
        }
    }

    //export clipboard
    fun exportConfigToClipboard(context: Context) {
        if (shareUrl.isNullOrEmpty()) {
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", shareUrl)
        clipboard.setPrimaryClip(clip)
        shareUrl = ""
    }

    fun dismissQRCode() {
        _qrcodeBitmap.value = null
    }

    fun refreshSubscription(
        url: String,
        subscriptionId: Int,
        extraHeaders: Map<String, String> = emptyMap(),
        onSuccess: (SubscriptionMeta) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _requestingSubscription.value = true
            try {
                val subscriptionInfo =
                    repository.fetchAndSaveNodes(url, subscriptionId, extraHeaders)
                _subscriptionMeta.value = subscriptionInfo
                withContext(Dispatchers.Main) { onSuccess(subscriptionInfo) }
            } catch (e: Exception) {
                launch {
                    _subscribeError.value = true
                    delay(2000L)
                    _subscribeError.value = false
                }
            } finally {
                _requestingSubscription.value = false
            }
        }
    }
}

class SubscriptionViewmodelFactory
@Inject constructor(
    val repository: SubscriptionRepository,
    val nodeRepository: NodeRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewmodel::class.java)) {
            return SubscriptionViewmodel(
                repository,
                nodeRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
