package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Subscription
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultSubscriptionComponent(
    componentContext: ComponentContext,
    private val subscriptionRepository: SubscriptionRepository,
    private val nodeRepository: NodeRepository,
) : SubscriptionComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()

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
        if (subscription.id == 0) {
            scope.launch {
                _state.update { it.copy(requesting = true) }
                val sub =
                    Subscription(
                        id = 0,
                        url = subscription.url,
                        mark = subscription.mark,
                        preNodeId = subscription.preNodeId,
                        nextNodeId = subscription.nextNodeId,
                        isAutoUpdate = subscription.isAutoUpdate,
                    )
                var newId = 0
                try {
                    newId = subscriptionRepository.addSubscription(sub).toInt()
                    val meta = subscriptionRepository.fetchAndSaveNodes(subscription.url, newId)
                    val mark = subscription.mark.ifEmpty { meta.profileTitle.orEmpty() }
                    if (mark.isNotEmpty() && isMarkDuplicate(mark, newId)) {
                        throw IllegalStateException("Duplicate subscription mark: $mark")
                    }
                    if (mark.isNotEmpty() && mark != sub.mark) {
                        subscriptionRepository.updateSubscription(
                            Subscription(
                                id = newId,
                                url = sub.url,
                                mark = mark,
                                preNodeId = sub.preNodeId,
                                nextNodeId = sub.nextNodeId,
                                isAutoUpdate = sub.isAutoUpdate,
                            ),
                        )
                    }
                    closeSheet()
                    onSuccess(newId)
                } catch (_: Exception) {
                    if (newId > 0) {
                        subscriptionRepository.deleteSubscription(
                            Subscription(
                                id = newId,
                                url = sub.url,
                                mark = sub.mark,
                                preNodeId = sub.preNodeId,
                                nextNodeId = sub.nextNodeId,
                                isAutoUpdate = sub.isAutoUpdate,
                            ),
                        )
                    }
                    showSubscribeError()
                } finally {
                    _state.update { it.copy(requesting = false) }
                }
            }
        } else {
            scope.launch {
                if (isMarkDuplicate(subscription.mark, subscription.id)) {
                    showSubscribeError()
                    return@launch
                }
                subscriptionRepository.updateSubscription(subscription)
                closeSheet()
                onSuccess(subscription.id)
            }
        }
    }

    override fun refreshSubscription(
        subscription: Subscription,
        onSuccess: (Int) -> Unit,
    ) {
        scope.launch {
            _state.update { it.copy(requesting = true) }
            try {
                subscriptionRepository.fetchAndSaveNodes(subscription.url, subscription.id)
                onSuccess(subscription.id)
            } catch (_: Exception) {
                showSubscribeError()
            } finally {
                _state.update { it.copy(requesting = false) }
            }
        }
    }

    private fun showSubscribeError() {
        scope.launch {
            _state.update { it.copy(subscribeError = true) }
            delay(2_000L)
            _state.update { it.copy(subscribeError = false) }
        }
    }
}
