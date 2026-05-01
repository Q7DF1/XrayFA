package com.android.xrayfa.core

import android.os.Parcel
import android.os.Parcelable

data class StartOptions(
    val url: String,
    var preUrl: String? = null,
    var nextUrl: String? = null
): Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(preUrl)
        parcel.writeString(nextUrl)
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