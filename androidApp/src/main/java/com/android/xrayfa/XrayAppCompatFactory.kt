package com.android.xrayfa

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AppComponentFactory
import org.koin.core.context.GlobalContext

/**
 * Inheriting from AppComponentFactory allows for custom component construction
 * during system-side component creation, enabling dependency injection via Koin.
 */
class XrayAppCompatFactory : AppComponentFactory() {

    companion object {
        const val TAG = "V2rayAppCompatFactory"
    }

    private val resolver: ComponentResolver
        get() = GlobalContext.get().get()

    override fun instantiateServiceCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Service {
        return resolver.resolveService(className)
            ?: super.instantiateServiceCompat(cl, className, intent)
    }

    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        return super.instantiateApplicationCompat(cl, className)
    }

    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        return resolver.resolveActivity(className)
            ?: super.instantiateActivityCompat(cl, className, intent)
    }

    override fun instantiateReceiverCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): BroadcastReceiver {
        return resolver.resolveReceiver(className)
            ?: super.instantiateReceiverCompat(cl, className, intent)
    }
}
