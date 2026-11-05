package com.android.xrayfa.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.common.routing.RegionRoutingPreset
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.decodeRules
import com.android.xrayfa.common.routing.defaultRouteList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RussiaRoutingRecommendationTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private val logger = object : Logger {
        override fun i(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Test fun requiresConsentAndPreservesChinaWhenAccepted() = runBlocking {
        val store = MemoryStore()
        val repository = SettingsRepository(store, logger)
        repository.initializeRussiaRoutingRecommendation("ru-RU")
        val before = repository.settingsFlow.first()
        assertTrue(before.showRussiaRoutingRecommendation)
        assertFalse(RegionRoutingPreset.RUSSIA.isEnabled(decodeRules(before.routingRules)))
        assertTrue(repository.resolveRussiaRoutingRecommendation(true))
        val after = SettingsRepository(store, logger).settingsFlow.first()
        assertFalse(after.showRussiaRoutingRecommendation)
        assertTrue(RegionRoutingPreset.RUSSIA.isEnabled(decodeRules(after.routingRules)))
        assertTrue(RegionRoutingPreset.CHINA.isEnabled(decodeRules(after.routingRules)))
        repository.initializeRussiaRoutingRecommendation("ru")
        assertFalse(repository.settingsFlow.first().showRussiaRoutingRecommendation)
    }

    @Test fun declineAndLanguageChangesDoNotAskAgain() = runBlocking {
        val store = MemoryStore()
        val repository = SettingsRepository(store, logger)
        repository.initializeRussiaRoutingRecommendation("ru")
        assertFalse(repository.resolveRussiaRoutingRecommendation(false))
        val recreated = SettingsRepository(store, logger)
        recreated.initializeRussiaRoutingRecommendation("en")
        recreated.initializeRussiaRoutingRecommendation("ru")
        assertFalse(recreated.settingsFlow.first().showRussiaRoutingRecommendation)
        assertEquals(defaultRouteList, decodeRules(recreated.settingsFlow.first().routingRules))
    }

    @Test fun laterLanguageChangeDoesNotTriggerRecommendation() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.initializeRussiaRoutingRecommendation("zh")
        repository.initializeRussiaRoutingRecommendation("ru")
        assertFalse(repository.settingsFlow.first().showRussiaRoutingRecommendation)
    }

    @Test fun savedRoutingChoicesAreNotPromptedOrOverwritten() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.setRoutingMode(RoutingMode.GLOBAL)
        repository.initializeRussiaRoutingRecommendation("ru")
        assertFalse(repository.resolveRussiaRoutingRecommendation(true))
        val settings = repository.settingsFlow.first()
        assertFalse(settings.showRussiaRoutingRecommendation)
        assertEquals(RoutingMode.GLOBAL.code, settings.routingMode)
        assertEquals(defaultRouteList, decodeRules(settings.routingRules))
    }

    @Test fun routingEditedWhilePendingWinsOverConsent() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.initializeRussiaRoutingRecommendation("ru")
        repository.setRoutingRules(emptyList())
        assertFalse(repository.settingsFlow.first().showRussiaRoutingRecommendation)
        assertFalse(repository.resolveRussiaRoutingRecommendation(true))
        assertTrue(decodeRules(repository.settingsFlow.first().routingRules).isEmpty())
    }

    @Test
    fun pendingConsentSurvivesRecreationAndInitializesOnlyOnce() = runBlocking {
        val store = MemoryStore()
        SettingsRepository(store, logger).initializeRussiaRoutingRecommendation("RU_ru")
        val recreated = SettingsRepository(store, logger)
        recreated.initializeRussiaRoutingRecommendation("en")
        assertTrue(recreated.settingsFlow.first().showRussiaRoutingRecommendation)
        assertTrue(recreated.resolveRussiaRoutingRecommendation(true))
    }

    @Test
    fun repeatedConcurrentConsentChangesRoutesOnlyOnce() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.initializeRussiaRoutingRecommendation("ru")
        val results = List(2) {
            async { repository.resolveRussiaRoutingRecommendation(true) }
        }.awaitAll()
        assertEquals(1, results.count { it })
        val rules = decodeRules(repository.settingsFlow.first().routingRules)
        assertEquals(defaultRouteList + RegionRoutingPreset.RUSSIA.rules, rules)
    }

    @Test
    fun changingRoutingModeCancelsPendingConsent() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.initializeRussiaRoutingRecommendation("ru")
        repository.setRoutingMode(RoutingMode.GLOBAL)
        assertFalse(repository.settingsFlow.first().showRussiaRoutingRecommendation)
        assertFalse(repository.resolveRussiaRoutingRecommendation(true))
        assertEquals(defaultRouteList, decodeRules(repository.settingsFlow.first().routingRules))
    }

    @Test
    fun savedCustomRulesAreNotPromptedOrOverwritten() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        repository.setRoutingRules(emptyList())
        repository.initializeRussiaRoutingRecommendation("ru")
        assertFalse(repository.settingsFlow.first().showRussiaRoutingRecommendation)
        assertFalse(repository.resolveRussiaRoutingRecommendation(true))
        assertTrue(decodeRules(repository.settingsFlow.first().routingRules).isEmpty())
    }
}
