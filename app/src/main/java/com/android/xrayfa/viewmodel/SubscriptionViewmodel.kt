package com.android.xrayfa.viewmodel

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.LinkRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.TAG
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
    val linkRepository: LinkRepository,
    val subscriptionParser: SubscriptionParser
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
                        linkRepository.deleteLinkBySubscriptionId(subscriptionId)
                        val newLinks = urls.map {
                            Log.i(TAG, "getSubscription: ${it.substringBefore("://")}")
                            Log.i(TAG, "getSubscription: $it")
                            Link(
                                protocolPrefix = it.substringBefore("://"),
                                content = it,
                                selected = false,
                                subscriptionId = subscriptionId
                            )
                        }
                        linkRepository.addLink(*newLinks.toTypedArray())
                    }
                    //todo show success
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
    val linkRepository: LinkRepository,
    val subscriptionParser: SubscriptionParser
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewmodel::class.java)) {
            return SubscriptionViewmodel(repository,okHttp,linkRepository,subscriptionParser) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
