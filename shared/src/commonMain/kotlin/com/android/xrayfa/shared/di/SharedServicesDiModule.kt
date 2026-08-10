package com.android.xrayfa.shared.di

import com.android.xrayfa.shared.config.ConfigLinkImporter
import com.android.xrayfa.shared.config.NodeEditor
import com.android.xrayfa.shared.config.NodeFormEditor
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
        single {
            NodeEditor(
                nodeRepository = get(),
                parserFactory = get(),
                vpnController = get(),
                logger = get(),
            )
        }
        single {
            NodeFormEditor(
                parserFactory = get(),
                nodeRepository = get(),
                vpnController = get(),
                logger = get(),
            )
        }
    }
