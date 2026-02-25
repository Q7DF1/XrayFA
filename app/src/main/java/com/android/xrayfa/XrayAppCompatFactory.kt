package com.android.xrayfa

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AppComponentFactory
import com.android.xrayfa.ComponentResolver
import com.android.xrayfa.di.DaggerXrayFAComponent
import com.android.xrayfa.di.XrayFAComponent
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Inheriting from AppComponentFactory allows for custom component construction
 * during system-side component creation, enabling dependency injection.
 */
class XrayAppCompatFactory: AppComponentFactory(),ContextAvailableCallback {
    
    companion object {
        const val TAG = "V2rayAppCompatFactory"

        var rootComponent: XrayFAComponent? = null
        var xrayPATH: String? = null
    }

    @set:Inject
    lateinit var resolver: ComponentResolver
    override fun instantiateServiceCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Service {
        rootComponent?.inject(this@XrayAppCompatFactory)
        return resolver.resolveService(className)
            ?:super.instantiateServiceCompat(cl, className, intent)
    }
    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        val app  =  super.instantiateApplicationCompat(cl, className) as XrayFAApplication
        app.contextAvailableCallback = this
        return app
    }


    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Activity {
        rootComponent?.inject(this@XrayAppCompatFactory)
        return resolver.resolveActivity(className)
            ?:super.instantiateActivityCompat(cl, className, intent)
    }

     override fun onContextAvailable(context: Context) {

         rootComponent = DaggerXrayFAComponent.builder()
             .bindContext(context)
             .build()

    }



}
interface ContextAvailableCallback {
    fun onContextAvailable(context: Context)
}