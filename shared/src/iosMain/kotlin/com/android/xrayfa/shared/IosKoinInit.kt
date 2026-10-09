package com.android.xrayfa.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.android.xrayfa.di.iosDomainDiModule
import com.android.xrayfa.di.parserDiModule
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsKeys
import com.android.xrayfa.nativebridge.XrayBridge
import com.android.xrayfa.shared.di.KoinQualifiers
import com.android.xrayfa.shared.di.iosDataDiModule
import com.android.xrayfa.shared.di.iosNetworkDiModule
import com.android.xrayfa.shared.di.iosPlatformDiModule
import com.android.xrayfa.shared.di.sharedCoroutineDiModule
import com.android.xrayfa.shared.di.sharedServicesDiModule
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.Foundation.NSUUID
import platform.UIKit.UIDevice

/** Idempotent Koin bootstrap for iOS host app (Compose shell). */
object IosKoinInit {
    private var started = false

    fun ensureStarted() {
        if (started) {
            return
        }
        val application = startKoin {
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
        val vendorId = UIDevice.currentDevice.identifierForVendor?.UUIDString
        application.koin.get<CoroutineScope>(named(KoinQualifiers.BACKGROUND_SCOPE)).launch {
            try {
                application.koin.get<DataStore<Preferences>>().edit { preferences ->
                    // Keep a persisted fallback when IDFV is temporarily unavailable.
                    preferences[SettingsKeys.HWID] = vendorId?.takeIf { it.isNotBlank() }
                        ?: preferences[SettingsKeys.HWID]?.takeIf { it.isNotBlank() && it != "unknown" }
                        ?: NSUUID().UUIDString
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                application.koin.get<Logger>().e("IosKoinInit", "Failed to initialize HWID", error)
            }
        }
        // Refresh metadata independently of VPN/core creation, including on simulators.
        application.koin.get<CoroutineScope>(named(KoinQualifiers.BACKGROUND_SCOPE)).launch {
            try {
                val version = application.koin.get<XrayBridge>().checkVersion()
                check(version.isNotBlank()) { "Xray returned an empty version" }
                val settings = application.koin.get<SettingsRepository>()
                if (settings.settingsFlow.first().xrayCoreVersion != version) {
                    settings.setXrayCoreVersion(version)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                application.koin.get<Logger>().e("IosKoinInit", "Failed to read Xray core version", error)
            }
        }
    }
}
