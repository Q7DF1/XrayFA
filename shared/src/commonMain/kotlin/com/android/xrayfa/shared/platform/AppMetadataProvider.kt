package com.android.xrayfa.shared.platform

interface AppMetadataProvider {
    fun getAppVersion(): String

    fun openUrl(url: String)
}

const val REPO_URL = "https://github.com/Q7DF1/XrayFA"
