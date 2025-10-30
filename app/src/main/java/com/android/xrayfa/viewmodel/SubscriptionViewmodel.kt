package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.repository.SubscriptionRepository
import javax.inject.Inject

class SubscriptionViewmodel(
    val repository: SubscriptionRepository
): ViewModel() {

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
