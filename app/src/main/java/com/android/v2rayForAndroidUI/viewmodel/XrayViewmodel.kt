package com.android.v2rayForAndroidUI.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import com.android.v2rayForAndroidUI.V2rayBaseService
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.parser.ParserFactory
import com.android.v2rayForAndroidUI.parser.VLESSConfigParser
import java.lang.ref.WeakReference

class XrayViewmodel(): ViewModel(){

    companion object {
        const val TAG = "XrayViewmodel"
    }



    private fun getConfigFromClipboard(context: Context):String {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = clipboard.primaryClip

        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).coerceToText(context).toString()
        }else {
            ""
        }
    }

    fun addV2rayConfigFromClipboard(context: Context):String {

        val link = getConfigFromClipboard(context)
        if (link == "") {
            return ""
        }

        val protocol = link.substringBefore("://").lowercase()
        val parser = ParserFactory.getParser(protocol)
        return parser.parse(link)
    }


    fun startV2rayService(context: Context) {

        val intent = Intent(context, V2rayBaseService::class.java).apply {
            action = "connect"
        }
        context.startForegroundService(intent)
    }

    fun stopV2rayService(context: Context) {

        val intent = Intent(context, V2rayBaseService::class.java).apply {
            action = "disconnect"
        }
        context.startService(intent)
    }

}