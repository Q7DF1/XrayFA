package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultSubscriptionComponent(
    componentContext: ComponentContext,
    private val subscriptionRepository: SubscriptionRepository,
    private val nodeRepository: NodeRepository,
    private val logger: Logger,
) : SubscriptionComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private var errorJob: Job? = null

    private val _state = MutableValue(SubscriptionState())
    override val state: Value<SubscriptionState> = _state

    init {
        scope.launch {
            combine(
                subscriptionRepository.allSubscriptions,
                nodeRepository.allNodes,
            ) { subscriptions, nodes ->
                Pair(subscriptions, nodes)
            }.collect { (subscriptions, nodes) ->
                _state.update { current ->
                    current.copy(
                        subscriptions = subscriptions,
                        allNodes = nodes,
                    )
                }
            }
        }
    }

    override fun isMarkDuplicate(
        mark: String,
        excludeSubscriptionId: Int,
    ): Boolean {
        val trimmed = mark.trim()
        if (trimmed.isEmpty()) return false
        return _state.value.subscriptions.any { subscription ->
            subscription.id != excludeSubscriptionId && subscription.mark.trim() == trimmed
        }
    }

    override fun openAddSheet() {
        _state.update { it.copy(sheetSubscription = EmptySubscription) }
    }

    override fun openEditSheet(subscriptionId: Int) {
        scope.launch {
            val subscription =
                subscriptionRepository.getSubscriptionById(subscriptionId).first()
                    ?: return@launch
            _state.update { it.copy(sheetSubscription = subscription) }
        }
    }

    override fun closeSheet() {
        _state.update { it.copy(sheetSubscription = null) }
    }

    override fun showDeleteDialog(subscription: Subscription) {
        _state.update { it.copy(deleteTarget = subscription) }
    }

    override fun dismissDeleteDialog() {
        _state.update { it.copy(deleteTarget = null) }
    }

    override fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        scope.launch {
            subscriptionRepository.deleteSubscription(target)
            dismissDeleteDialog()
        }
    }

    override fun addOrUpdateSubscription(
        subscription: Subscription,
        onSuccess: (Int) -> Unit,
    ) {
        if (!beginRequest()) return
        if (subscription.id == 0) {
            scope.launch {
                val sub = subscription.copy(id = 0)
                var newId = 0
                try {
                    newId = subscriptionRepository.addSubscription(sub).toInt()
                    val meta = subscriptionRepository.fetchAndSaveNodes(subscription.url, newId)
                    val mark = subscription.mark.ifEmpty { meta.profileTitle.orEmpty() }
                    if (mark.isNotEmpty() && isMarkDuplicate(mark, newId)) {
                        throw DuplicateSubscriptionMarkException()
                    }
                    if (mark.isNotEmpty() && mark != sub.mark) {
                        subscriptionRepository.updateSubscription(
                            sub.copy(id = newId, mark = mark),
                        )
                    }
                    closeSheet()
                    onSuccess(newId)
                } catch (e: Exception) {
                    if (newId > 0) {
                        withContext(NonCancellable) {
                            try {
                                subscriptionRepository.deleteSubscription(sub.copy(id = newId))
                            } catch (cleanupError: Exception) {
                                logSubscriptionFailure(cleanupError, "Rollback failed")
                            }
                        }
                    }
                    if (e is CancellationException) throw e
                    showSubscribeError(e)
                } finally {
                    _state.update { it.copy(requesting = false) }
                }
            }
        } else {
            scope.launch {
                try {
                    if (isMarkDuplicate(subscription.mark, subscription.id)) {
                        throw DuplicateSubscriptionMarkException()
                    }
                    subscriptionRepository.updateSubscription(subscription)
                    closeSheet()
                    onSuccess(subscription.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    showSubscribeError(e)
                } finally {
                    _state.update { it.copy(requesting = false) }
                }
            }
        }
    }

    override fun refreshSubscription(
        subscription: Subscription,
        onSuccess: (Int) -> Unit,
    ) {
        if (!beginRequest()) return
        scope.launch {
            try {
                subscriptionRepository.fetchAndSaveNodes(subscription.url, subscription.id)
                onSuccess(subscription.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showSubscribeError(e)
            } finally {
                _state.update { it.copy(requesting = false) }
            }
        }
    }

    private fun beginRequest(): Boolean {
        if (_state.value.requesting) return false
        errorJob?.cancel()
        _state.update { it.copy(requesting = true, subscribeError = false, subscribeErrorCode = null, subscribeHttpStatus = null) }
        return true
    }

    private fun logSubscriptionFailure(
        error: Exception,
        operation: String,
        details: SubscriptionErrorDetails = subscriptionErrorDetails(error),
    ) {
        logger.e(
            "Subscription",
            "$operation: reason=${details.kind}, http=${details.httpStatus}, types=${details.exceptionTypes}, " +
                "${details.parseContext}, validation=${details.validationReason}, locations=${details.codeLocations}",
        )
    }

    private fun showSubscribeError(error: Exception) {
        val details = if (error is DuplicateSubscriptionMarkException) {
            SubscriptionErrorDetails(SubscriptionErrorKind.DUPLICATE)
        } else subscriptionErrorDetails(error)
        logSubscriptionFailure(error, "Subscription failed", details)
        errorJob?.cancel()
        _state.update {
            it.copy(subscribeError = true, subscribeErrorCode = details.kind.name, subscribeHttpStatus = details.httpStatus)
        }
        errorJob = scope.launch {
            delay(8_000L)
            _state.update { it.copy(subscribeError = false) }
        }
    }
}

private class DuplicateSubscriptionMarkException : Exception()
