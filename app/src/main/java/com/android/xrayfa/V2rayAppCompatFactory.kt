package com.android.xrayfa

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.AppComponentFactory
import com.android.xrayfa.di.DaggerV2rayComponent
import com.android.xrayfa.di.V2rayComponent
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class V2rayAppCompatFactory: AppComponentFactory(),ContextAvailableCallback {
    
    companion object {
        const val TAG = "V2rayAppCompatFactory"

        var rootComponent: V2rayComponent? = null
    }

    @set:Inject
    lateinit var resolver: ComponentResolver
    override fun instantiateServiceCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Service {
        rootComponent?.inject(this@V2rayAppCompatFactory)
        return resolver.resolveService(className)
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
        rootComponent?.inject(this@V2rayAppCompatFactory)
        return resolver.resolveActivity(className)
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