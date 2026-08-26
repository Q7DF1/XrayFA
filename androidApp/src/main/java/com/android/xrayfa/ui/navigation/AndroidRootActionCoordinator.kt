package com.android.xrayfa.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidRootActionCoordinator {
    private val _pendingAction = MutableStateFlow<AndroidRootAction?>(null)
    val pendingAction: StateFlow<AndroidRootAction?> = _pendingAction.asStateFlow()

    fun dispatch(action: AndroidRootAction) {
        _pendingAction.value = action
    }

    fun consume(): AndroidRootAction? {
        val action = _pendingAction.value
        _pendingAction.value = null
        return action
    }
}
