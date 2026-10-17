package com.android.xrayfa.core

import android.os.Parcel
import android.os.Parcelable
import com.android.xrayfa.common.core.CoreStartOptions

data class StartOptions(
    val url: String,
    var preUrl: String? = null,
    var nextUrl: String? = null,
    val jsonNodeId: Int = 0,
    val selectedNodeId: Int = 0,
    val nodeName: String? = null,
    val serverAddress: String? = null,
    val serverPort: Int = 0,
): Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        if (parcel.dataAvail() > 0) parcel.readInt() else 0,
        if (parcel.dataAvail() > 0) parcel.readInt() else 0,
        if (parcel.dataAvail() > 0) parcel.readString() else null,
        if (parcel.dataAvail() > 0) parcel.readString() else null,
        if (parcel.dataAvail() > 0) parcel.readInt() else 0,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(preUrl)
        parcel.writeString(nextUrl)
        parcel.writeInt(jsonNodeId)
        parcel.writeInt(selectedNodeId)
        parcel.writeString(nodeName)
        parcel.writeString(serverAddress)
        parcel.writeInt(serverPort)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<StartOptions> {
            override fun createFromParcel(parcel: Parcel): StartOptions {
                return StartOptions(parcel)
            }

            override fun newArray(size: Int): Array<StartOptions?> {
                return arrayOfNulls(size)
            }
        }

        const val EXTRA_START_OPTIONS = "com.android.XrayFA.EXTRA_START_OPTIONS"
    }
}

fun StartOptions.toCoreStartOptions(): CoreStartOptions =
    CoreStartOptions(url = url, preUrl = preUrl, nextUrl = nextUrl)
