package com.android.xrayfa.di

import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.di.qualifier.Main
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
class CoroutinesModule {

    @Provides
    @Singleton
    @Main
    fun mainScope(
        @Main dispatcherContext: CoroutineContext,
    ): CoroutineScope = CoroutineScope(dispatcherContext)

    @Provides
    @Singleton
    @Main
    fun mainCoroutineContext(): CoroutineContext {
        return Dispatchers.Main.immediate + SupervisorJob()
    }



    @Provides
    @Singleton
    @Background
    fun backgroundScope(
        @Background dispatcherContext: CoroutineContext,
    ): CoroutineScope = CoroutineScope(dispatcherContext)


    @Provides
    @Singleton
    @Background
    fun backgroundCoroutineContext(): CoroutineContext {
        return Dispatchers.IO + SupervisorJob()
    }



}