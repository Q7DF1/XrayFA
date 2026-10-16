package com.android.xrayfa.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.android.xrayfa.common.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScreenWidgetSettingsTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val logger = object : Logger {
        override fun i(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Test fun missingPreferenceKeepsWidgetsDisabled() = runBlocking {
        val repository = SettingsRepository(MemoryStore(), logger)
        assertFalse(SettingsState().homeScreenWidgetsEnabled)
        assertFalse(repository.settingsFlow.first().homeScreenWidgetsEnabled)
    }

    @Test fun disabledChoiceSurvivesRepositoryRecreationAndCanBeEnabledAgain() = runBlocking {
        val store = MemoryStore()
        SettingsRepository(store, logger).setHomeScreenWidgetsEnabled(false)
        val recreated = SettingsRepository(store, logger)
        assertFalse(recreated.settingsFlow.first().homeScreenWidgetsEnabled)
        recreated.setHomeScreenWidgetsEnabled(true)
        assertTrue(SettingsRepository(store, logger).settingsFlow.first().homeScreenWidgetsEnabled)
    }
}
