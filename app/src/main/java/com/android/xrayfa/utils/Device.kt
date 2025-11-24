package com.android.xrayfa.utils

import android.content.Context
import android.net.InetAddresses
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.Patterns
import androidx.annotation.RequiresApi
import com.maxmind.geoip2.DatabaseReader
import java.io.File
import java.net.InetAddress

object Device {
    const val TAG = "Device"
    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device ID", e)
            ""
        }
    }


    fun getCountryISOFromIp(geoPath: String, ip: String):String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!InetAddresses.isNumericAddress(ip)) {
                return ""
            }
        }else "" //todo
        return try {
            val file = File(geoPath)
            val reader = DatabaseReader.Builder(file).build()
            val address = InetAddress.getByName(ip)
            val res = reader.country(address)
            res.country.isoCode ?: ""
        }catch (e: Exception) {
            Log.e(TAG, "getCountryFromIp: parse ip failed: ${e.message}")
            ""
        }
    }



    fun countryCodeToEmoji(countryCode: String): String {
        val code = countryCode.uppercase()
        if (code.length != 2) return "❓"
        val flagOffset = 0x1F1E6
        val asciiOffset = 0x41
        val firstChar = Character.codePointAt(code, 0) - asciiOffset + flagOffset
        val secondChar = Character.codePointAt(code, 1) - asciiOffset + flagOffset
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}