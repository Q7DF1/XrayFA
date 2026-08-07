package com.android.xrayfa.shared.di

import com.android.xrayfa.shared.vpn.AndroidVpnConnectCoordinator
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import org.koin.core.module.Module
import org.koin.dsl.module

val androidSharedDiModule: Module =
    module {
        single { VpnStartOptionsResolver(get(), get()) }
        single<VpnConnectCoordinator> {
            AndroidVpnConnectCoordinator(
                vpnController = get(),
                startOptionsResolver = get(),
            )
        }
    }
