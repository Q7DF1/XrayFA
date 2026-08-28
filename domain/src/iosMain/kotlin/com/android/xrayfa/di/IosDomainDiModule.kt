package com.android.xrayfa.di

import com.android.xrayfa.config.IosXrayConfigEncoder
import com.android.xrayfa.config.XrayConfigEncoder
import org.koin.dsl.module

val iosDomainDiModule = module {
    single<XrayConfigEncoder> { IosXrayConfigEncoder() }
}
