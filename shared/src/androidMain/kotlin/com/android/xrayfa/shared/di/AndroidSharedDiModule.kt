package com.android.xrayfa.shared.di

import com.android.xrayfa.shared.platform.AndroidAppMetadataProvider
import com.android.xrayfa.shared.platform.AndroidClipboardReader
import com.android.xrayfa.shared.platform.AndroidClipboardWriter
import com.android.xrayfa.shared.platform.AppMetadataProvider
import com.android.xrayfa.shared.platform.ClipboardReader
import com.android.xrayfa.shared.platform.ClipboardWriter
import com.android.xrayfa.shared.vpn.AndroidVpnConnectCoordinator
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val androidSharedDiModule: Module =
    module {
        single<ClipboardReader> { AndroidClipboardReader(androidContext()) }
        single<ClipboardWriter> { AndroidClipboardWriter(androidContext()) }
        single<AppMetadataProvider> { AndroidAppMetadataProvider(androidContext()) }
        single { VpnStartOptionsResolver(get(), get()) }
        single<VpnConnectCoordinator> {
            AndroidVpnConnectCoordinator(
                vpnController = get(),
                startOptionsResolver = get(),
            )
        }
    }
