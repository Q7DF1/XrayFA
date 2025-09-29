package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.repository.LinkRepository
import javax.inject.Inject

class DetailViewmodel(
    val repository: LinkRepository
): ViewModel() {


}


class DetailViewmodelFactory
@Inject constructor(
    val repository: LinkRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewmodel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}