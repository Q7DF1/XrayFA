package com.android.xrayfa.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import org.koin.core.context.GlobalContext

class ConnectionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        refresh()
    }

    private fun refresh() {
        val pending = goAsync()
        GlobalContext.get().get<ConnectionWidgetUpdater>().refresh { pending.finish() }
    }
}
