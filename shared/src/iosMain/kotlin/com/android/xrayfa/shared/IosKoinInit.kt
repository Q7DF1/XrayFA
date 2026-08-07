package com.android.xrayfa.shared

import com.android.xrayfa.di.iosDomainDiModule
import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.VpnController
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val iosPlatformDiModule =
    module {
        single { IosVpnController() }
        single<VpnController> { get<IosVpnController>() }
    }

/** Idempotent Koin bootstrap for iOS host app (Compose shell). */
object IosKoinInit {
    private var started = false

    fun ensureStarted() {
        if (started) {
            return
        }
        startKoin {
            modules(iosDomainDiModule, iosPlatformDiModule)
        }
        started = true
    }
}
