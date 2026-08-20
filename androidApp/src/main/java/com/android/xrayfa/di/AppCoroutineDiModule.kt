package com.android.xrayfa.di

import com.android.xrayfa.shared.di.KoinQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appCoroutineDiModule: Module = module {
    single(named(KoinQualifiers.MAIN_SCOPE)) {
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }
    single(named(KoinQualifiers.BACKGROUND_SCOPE)) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}
