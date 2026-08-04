package com.android.xrayfa.di

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.config.GsonXrayConfigEncoder
import com.android.xrayfa.config.XrayConfigEncoder
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
    fun provideXrayConfigEncoder(gson: Gson): XrayConfigEncoder = GsonXrayConfigEncoder(gson)

    @Provides
    @Singleton
    fun provideConfigParserSettingsProvider(
        settingsRepository: SettingsRepository,
    ): ConfigParserSettingsProvider = settingsRepository

    @Provides
    @Singleton
    fun provideVLESSConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): VLESSConfigParser = VLESSConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideVMESSConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): VMESSConfigParser = VMESSConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideTrojanConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): TrojanConfigParser = TrojanConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideShadowSocksConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): ShadowSocksConfigParser = ShadowSocksConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideHysteria2ConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): Hysteria2ConfigParser = Hysteria2ConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideSocksConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): SocksConfigParser = SocksConfigParser(settingsProvider, geoIpProvider, configEncoder)

    @Provides
    @Singleton
    fun provideHttpConfigParser(
        settingsProvider: ConfigParserSettingsProvider,
        geoIpProvider: GeoIpProvider,
        configEncoder: XrayConfigEncoder,
    ): HttpConfigParser = HttpConfigParser(settingsProvider, geoIpProvider, configEncoder)

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
