package com.android.xrayfa.core

import android.util.Log
import com.android.xrayfa.common.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLogger @Inject constructor() : Logger {

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
