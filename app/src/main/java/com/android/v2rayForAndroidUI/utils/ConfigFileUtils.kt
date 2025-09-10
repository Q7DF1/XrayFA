package com.android.v2rayForAndroidUI.utils

import android.content.Context
import java.io.File

object ConfigFileUtils {


    fun createConfigFileDir(context: Context) {
        val configDir = File(context.getExternalFilesDir(null),"configs")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }

    fun configToFile(config: String) {

    }
}