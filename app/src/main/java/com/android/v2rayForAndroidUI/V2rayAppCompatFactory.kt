package com.android.v2rayForAndroidUI

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AppComponentFactory
import com.android.v2rayForAndroidUI.di.DaggerV2rayComponent
import com.android.v2rayForAndroidUI.di.V2rayComponent
import java.io.File
import java.io.FileOutputStream

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


    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Activity {
        val activities = rootComponent.getActivities()
        val clazz = Class.forName(className)
        val activity = activities[clazz]
        if (activity != null) {
            return activity.get()
        }
        return super.instantiateActivityCompat(cl, className, intent)
    }

     override fun onContextAvailable(context: Context) {

         rootComponent = DaggerV2rayComponent.builder()
             .bindContext(context)
             .build()

         //init file
         val fileDir = context.getExternalFilesDir("assets")
         val geoipFile = File(fileDir, "geoip.dat")
         val geositeFile = File(fileDir, "geosite.dat")

         if (!geoipFile.exists()) {
             context.assets.open("geoip.dat").use { input ->
                 FileOutputStream(geoipFile).use { output ->
                     input.copyTo(output)
                 }
             }
         }

         if (!geositeFile.exists()) {

             context.assets.open("geosite.dat").use { input ->
                 FileOutputStream(geositeFile).use { output ->
                     input.copyTo(output)
                 }
             }
         }

    }



}
interface ContextAvailableCallback {
    fun onContextAvailable(context: Context)
}