package com.android.v2rayForAndroidUI.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.v2rayForAndroidUI.V2rayBaseService
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.model.Link
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.protocol.protocols
import com.android.v2rayForAndroidUI.parser.ParserFactory
import com.android.v2rayForAndroidUI.parser.VLESSConfigParser
import com.android.v2rayForAndroidUI.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class XrayViewmodel(
    private val linkRepository: LinkRepository
): ViewModel(){

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

    fun addV2rayConfigFromClipboard(context: Context) {

        val link = getConfigFromClipboard(context)
        if (link == "") {
            return
        }
        Log.i(TAG, "addV2rayConfigFromClipboard: $link")
        addLink(link)

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


    fun isV2rayServiceRunning():Boolean {
        return V2rayBaseService.isRunning
    }


    //link

    fun getAllLinks(): Flow<List<Link>> {
        return linkRepository.allLinks
    }

    fun getAllNodes(): Flow<List<Node>> {
        val allLinks = linkRepository.allLinks
        val nodes = allLinks.map { links ->
            links.map { link ->
                return@map ParserFactory.getParser(link.protocol).preParse(link.content)
            }
        }

        return nodes
    }

    fun addLink(link: String) {
        // pre parse
        val protocolName = link.substringBefore("://").lowercase()
        if (protocols.contains(protocolName)) {
            val link0 =  Link(protocol = protocolName, content = link)
            viewModelScope.launch {
                Log.i(TAG, "addLink: $link0")
                linkRepository.addLink(link0)
            }
        }else {
            //TODO
        }
    }


    fun deleteLink(link: Link) {
        viewModelScope.launch {
            linkRepository.deleteLink(link)
        }
    }

}

class XrayViewmodelFactory(private val repository: LinkRepository): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XrayViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XrayViewmodel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}