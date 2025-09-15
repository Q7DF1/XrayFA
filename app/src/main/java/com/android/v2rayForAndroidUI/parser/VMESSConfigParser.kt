package com.android.v2rayForAndroidUI.parser

import android.util.Log
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VMESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.stream.HeaderObject
import com.android.v2rayForAndroidUI.model.stream.HttpHeaderObject
import com.android.v2rayForAndroidUI.model.stream.HttpRequestObject
import com.android.v2rayForAndroidUI.model.stream.HttpResponseObject
import com.android.v2rayForAndroidUI.model.stream.RawSettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.google.gson.JsonParser
import java.util.Base64

class VMESSConfigParser: AbstractConfigParser() {

    companion object {
        const val TAG = "VMESSConfigParser"
    }

    override fun parseOutbound(link: String): OutboundObject {

        try {
            // 1. 去掉前缀
            val cleanLink = link.removePrefix("vmess://").trim()

            // 2. Base64 解码
            val decoded = String(Base64.getDecoder().decode(cleanLink))

            // 3. 转成 JSON
            val json = JsonParser.parseString(decoded).asJsonObject
            for ((key, value) in json.entrySet()) {
                //Log.i(TAG,"$key: $value")
                println("$key: $value")
            }

            return OutboundObject(
                protocol = "vmess",
                settings = VMESSOutboundConfigurationObject(
                    vnext = listOf(
                        ServerObject(
                            address = json.get("add").asString,
                            port = json.get("port").asInt,
                            users = listOf(
                                UserObject(
                                    id = json.get("id").asString,
                                    level = 8,
                                    security = "auto"
                                )
                            )
                        )
                    )
                ),
                streamSettings = StreamSettingsObject(
                    network = json.get("net").asString,
                    security = "", //check later
                    rawSettings = RawSettings(
                        header = HttpHeaderObject(
                            request = HttpRequestObject(),
                            type = "http"
                        )
                    )
                ),
                tag = "proxy"
            )

        }catch (e: Exception){
            throw RuntimeException(e)
        }
    }

}