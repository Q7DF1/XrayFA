package com.android.xrayfa.shared.di

import com.android.xrayfa.shared.vpn.AndroidVpnConnectCoordinator
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import org.koin.core.module.Module
import org.koin.dsl.module

/** Placeholder until Android adopts [SharedHomeSection] in `:androidApp`. */
val androidSharedDiModule: Module = module {
    single<VpnConnectCoordinator> { AndroidVpnConnectCoordinator() }
}
