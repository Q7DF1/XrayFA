package com.android.xrayfa.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

/** Only explicit, immutable PendingIntents created by our own widgets can reach this receiver. */
class ConnectionWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONNECT && intent.action != ACTION_DISCONNECT &&
            intent.action != ACTION_REFRESH_EXIT_IP) return
        val pending = goAsync()
        val updater = GlobalContext.get().get<ConnectionWidgetUpdater>()
        if (intent.action == ACTION_REFRESH_EXIT_IP) {
            updater.refreshExitIp { pending.finish() }
        } else {
            updater.performAction(intent.action == ACTION_CONNECT) { pending.finish() }
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.android.xrayfa.widget.CONNECT"
        const val ACTION_DISCONNECT = "com.android.xrayfa.widget.DISCONNECT"
        const val ACTION_REFRESH_EXIT_IP = "com.android.xrayfa.widget.REFRESH_EXIT_IP"
    }
}
