package com.android.xrayfa.shared.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedCoroutineDiModule: Module = module {
    single(named(KoinQualifiers.MAIN_SCOPE)) {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }
    single(named(KoinQualifiers.BACKGROUND_SCOPE)) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}
