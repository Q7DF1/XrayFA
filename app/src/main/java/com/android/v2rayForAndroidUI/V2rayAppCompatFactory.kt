package com.android.v2rayForAndroidUI

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AppComponentFactory
import com.android.v2rayForAndroidUI.di.DaggerV2rayComponent
import com.android.v2rayForAndroidUI.di.V2rayComponent

class V2rayAppCompatFactory: AppComponentFactory(),ContextAvailableCallback {
    
    companion object {
        const val TAG = "V2rayAppCompatFactory"
    }
    
    lateinit var rootComponent: V2rayComponent
    override fun instantiateServiceCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Service {
        val vpnServices = rootComponent.getVpnServices()
        val clazz = Class.forName(className)
        val serviceProvider = vpnServices[clazz]
        if (serviceProvider != null) {
            Log.i(TAG, "instantiateServiceCompat: init service")
            return serviceProvider.get()
        }
        return super.instantiateServiceCompat(cl, className, intent)
    }
    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        val app  =  super.instantiateApplicationCompat(cl, className) as V2rayFAApplication
        app.setContextAvailableCallback(this)
        return app
    }

     override fun onContextAvailable(context: Context) {
         rootComponent = DaggerV2rayComponent.builder()
             .bindContext(context)
             .build()
    }



}
interface ContextAvailableCallback {
    fun onContextAvailable(context: Context)
}