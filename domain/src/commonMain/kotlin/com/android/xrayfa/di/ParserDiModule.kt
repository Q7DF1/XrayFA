package com.android.xrayfa.di

import com.android.xrayfa.parser.HttpConfigParser
import com.android.xrayfa.parser.Hysteria2ConfigParser
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.ShadowSocksConfigParser
import com.android.xrayfa.parser.SocksConfigParser
import com.android.xrayfa.parser.TrojanConfigParser
import com.android.xrayfa.parser.VLESSConfigParser
import com.android.xrayfa.parser.VMESSConfigParser
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP-safe parser dependency graph.
 * Platform modules must bind [com.android.xrayfa.config.XrayConfigEncoder],
 * [com.android.xrayfa.common.repository.ConfigParserSettingsProvider], and
 * [com.android.xrayfa.common.core.GeoIpProvider] before this module is loaded.
 */
fun parserDiModule(): Module = module {
    single { VLESSConfigParser(get(), get(), get()) }
    single { VMESSConfigParser(get(), get(), get()) }
    single { TrojanConfigParser(get(), get(), get()) }
    single { ShadowSocksConfigParser(get(), get(), get()) }
    single { Hysteria2ConfigParser(get(), get(), get()) }
    single { SocksConfigParser(get(), get(), get()) }
    single { HttpConfigParser(get(), get(), get()) }
    single {
        ParserFactory(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
}
