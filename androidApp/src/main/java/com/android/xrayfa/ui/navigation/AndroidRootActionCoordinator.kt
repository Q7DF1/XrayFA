package com.android.xrayfa.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

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

    suspend fun collectActions(handle: suspend (AndroidRootAction) -> Unit) {
        // Consuming an action emits null; that must not cancel its suspended work.
        pendingAction.collect { action ->
            if (action != null) handle(action)
        }
    }
}
