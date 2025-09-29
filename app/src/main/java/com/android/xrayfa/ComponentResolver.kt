package com.android.xrayfa

import android.app.Activity
import android.app.Service
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton


@Singleton
class ComponentResolver
@Inject constructor(
    private val activityProviders: Map<Class<*>, @JvmSuppressWildcards Provider<Activity>>,
    private val serviceProviders: Map<Class<*>, @JvmSuppressWildcards Provider<Service>>
) {


    fun resolveActivity(className: String): Activity? {
        return resolve(className, activityProviders)
    }

    fun resolveService(className: String): Service? {
        return resolve(className, serviceProviders)
    }

    fun <T> resolve(className: String,creators: Map<Class<*>,Provider<T>>): T? {
        val clazz = Class.forName(className)
        val provider = creators[clazz]
        return provider?.get()
    }
}