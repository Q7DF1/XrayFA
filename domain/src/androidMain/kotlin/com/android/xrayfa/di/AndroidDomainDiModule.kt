package com.android.xrayfa.di

import com.android.xrayfa.config.GsonXrayConfigEncoder
import com.android.xrayfa.config.XrayConfigEncoder
import com.google.gson.Gson
import org.koin.dsl.module

val androidDomainDiModule = module {
    single { Gson() }
    single<XrayConfigEncoder> { GsonXrayConfigEncoder(get()) }
}
