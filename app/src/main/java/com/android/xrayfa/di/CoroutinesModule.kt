package com.android.xrayfa.di

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xrayfa.tun2socks.qualifier.Application
import xrayfa.tun2socks.qualifier.Main
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
class CoroutinesModule {

    @Provides
    @Singleton
    @Application
    fun applicationScope(
        @Main dispatcherContext: CoroutineContext,
    ): CoroutineScope = CoroutineScope(dispatcherContext)

    @Provides
    @Singleton
    @Main
    fun mainCoroutineContext(): CoroutineContext {
        return Dispatchers.Main.immediate + SupervisorJob()
    }

}