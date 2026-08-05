package com.android.xrayfa

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver

/**
 * Replaces system-constructed Activity/Service/Receiver instances so constructor
 * injection is satisfied via Koin instead of Dagger multibindings.
 */
class ComponentResolver(
    private val activityProviders: Map<Class<*>, () -> Activity>,
    private val serviceProviders: Map<Class<*>, () -> Service>,
    private val receiverProviders: Map<Class<*>, () -> BroadcastReceiver>,
) {

    fun resolveActivity(className: String): Activity? {
        return resolve(className, activityProviders)
    }

    fun resolveService(className: String): Service? {
        return resolve(className, serviceProviders)
    }

    fun resolveReceiver(className: String): BroadcastReceiver? {
        return resolve(className, receiverProviders)
    }

    private fun <T> resolve(className: String, creators: Map<Class<*>, () -> T>): T? {
        val clazz = Class.forName(className)
        return creators[clazz]?.invoke()
    }
}
