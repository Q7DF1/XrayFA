package com.android.xrayfa.shared.di

/** Koin qualifier names for coroutine scopes (mirrors `:androidApp` di). */
object KoinQualifiers {
    const val MAIN_SCOPE = "MainScope"
    const val BACKGROUND_SCOPE = "BackgroundScope"
}
