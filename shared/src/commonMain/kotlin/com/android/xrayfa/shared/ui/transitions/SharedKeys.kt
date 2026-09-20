package com.android.xrayfa.shared.ui.transitions

/**
 * 共享容器的配对 key。用 data class 而非裸字符串：字符串拼错会导致静默不匹配
 * （没有动画，也没有报错）。
 */
data class SharedContainerKey(val destination: String)

object TransitionDestinations {
    const val SETTINGS = "settings"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SEARCH = "search"
    const val APPS = "apps"
    const val LOGCAT = "logcat"
    const val ROUTE = "route"
    const val QR = "qr"
    const val NODE_EDIT_NEW = "node-edit-new"

    fun nodeEdit(nodeId: Int): String = if (nodeId > 0) "node-edit-$nodeId" else NODE_EDIT_NEW
}
