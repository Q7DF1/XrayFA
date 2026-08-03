package com.android.xrayfa.di

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.parser.HttpConfigParser
import com.android.xrayfa.parser.Hysteria2ConfigParser
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.ShadowSocksConfigParser
import com.android.xrayfa.parser.SocksConfigParser
import com.android.xrayfa.parser.TrojanConfigParser
import com.android.xrayfa.parser.VLESSConfigParser
import com.android.xrayfa.parser.VMESSConfigParser
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object ParserModule {

    @Provides
    @Singleton
    fun provideVLESSConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): VLESSConfigParser = VLESSConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideVMESSConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): VMESSConfigParser = VMESSConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideTrojanConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): TrojanConfigParser = TrojanConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideShadowSocksConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): ShadowSocksConfigParser = ShadowSocksConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideHysteria2ConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): Hysteria2ConfigParser = Hysteria2ConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideSocksConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): SocksConfigParser = SocksConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideHttpConfigParser(
        settingsRepo: SettingsRepository,
        geoIpProvider: GeoIpProvider,
        gson: Gson,
    ): HttpConfigParser = HttpConfigParser(settingsRepo, geoIpProvider, gson)

    @Provides
    @Singleton
    fun provideParserFactory(
        vlessConfigParser: VLESSConfigParser,
        vmessConfigParser: VMESSConfigParser,
        trojanConfigParser: TrojanConfigParser,
        shadowSocksConfigParser: ShadowSocksConfigParser,
        hysteria2ConfigParser: Hysteria2ConfigParser,
        socksConfigParser: SocksConfigParser,
        httpConfigParser: HttpConfigParser,
    ): ParserFactory = ParserFactory(
        vlessConfigParser,
        vmessConfigParser,
        trojanConfigParser,
        shadowSocksConfigParser,
        hysteria2ConfigParser,
        socksConfigParser,
        httpConfigParser,
    )
}
