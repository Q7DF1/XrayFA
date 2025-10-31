package com.android.xrayfa.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

val emptySubscription = Subscription(-1,"","")


class SubscriptionViewmodel(
    val repository: SubscriptionRepository
): ViewModel() {

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions= _subscriptions.asStateFlow()


    private val _selectSubscription = MutableStateFlow<Subscription>(emptySubscription)
    val selectSubscription = _selectSubscription.asStateFlow()
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

    fun updateSubscription(subscription: Subscription) {

    }

    fun getSubscriptionById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectSubscription.value = repository.getSubscriptionById(id).first()
        }
    }

    fun getSubscriptionByIdWithCallback(id: Int,callback: ()->Unit) {

        viewModelScope.launch(Dispatchers.IO) {
            _selectSubscription.value = repository.getSubscriptionById(id).first()
            Log.i("test", "getSubscriptionByIdWithCallback: ${_selectSubscription.value.hashCode()}")
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }
    fun setSelectSubscriptionEmpty() {
        _selectSubscription.value = emptySubscription
    }



}


class SubscriptionViewmodelFactory
@Inject constructor(
    val repository: SubscriptionRepository
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewmodel::class.java)) {
            return SubscriptionViewmodel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
