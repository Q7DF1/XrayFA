package com.android.xrayfa.shared

import com.android.xrayfa.di.iosDomainDiModule
import com.android.xrayfa.di.parserDiModule
import com.android.xrayfa.shared.di.iosDataDiModule
import com.android.xrayfa.shared.di.iosNetworkDiModule
import com.android.xrayfa.shared.di.iosPlatformDiModule
import com.android.xrayfa.shared.di.sharedCoroutineDiModule
import com.android.xrayfa.shared.di.sharedServicesDiModule
import org.koin.core.context.startKoin

/** Idempotent Koin bootstrap for iOS host app (Compose shell). */
object IosKoinInit {
    private var started = false

    fun ensureStarted() {
        if (started) {
            return
        }
        startKoin {
            modules(
                sharedCoroutineDiModule,
                sharedServicesDiModule,
                iosPlatformDiModule,
                iosDataDiModule,
                iosNetworkDiModule,
                iosDomainDiModule,
                parserDiModule(),
            )
        }
        started = true
    }
}
