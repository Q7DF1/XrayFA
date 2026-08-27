package com.android.xrayfa.shared.di

import com.android.xrayfa.network.FileDownloader
import com.android.xrayfa.network.createStandardFileDownloader
import com.android.xrayfa.network.createSubscriptionFetcher
import org.koin.core.module.Module
import org.koin.dsl.module

private const val IOS_USER_AGENT = "xrayFA/0.1.0-kmp"

val iosNetworkDiModule: Module = module {
    single { createSubscriptionFetcher(userAgent = IOS_USER_AGENT) }
    single<FileDownloader> { createStandardFileDownloader(userAgent = IOS_USER_AGENT) }
}
