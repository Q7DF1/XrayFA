package com.android.xrayfa.shared.di

import com.android.xrayfa.shared.config.ConfigLinkImporter
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedServicesDiModule: Module =
    module {
        single {
            ConfigLinkImporter(
                nodeRepository = get(),
                parserFactory = get(),
                clipboardReader = get(),
                logger = get(),
            )
        }
    }
