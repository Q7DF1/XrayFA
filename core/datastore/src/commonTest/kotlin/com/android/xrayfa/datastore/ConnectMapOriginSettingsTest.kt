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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConnectMapOriginSettingsTest {
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

    @Test
    fun originSurvivesRestartAndCanBeMovedOrCleared() = runBlocking {
        val store = MemoryStore()
        val repository = SettingsRepository(store, logger)
        assertNull(repository.settingsFlow.first().connectMapOrigin)
        val first = ConnectMapOrigin(31.23, 121.47)
        repository.setConnectMapOrigin(first)
        assertEquals(first, SettingsRepository(store, logger).settingsFlow.first().connectMapOrigin)
        val moved = ConnectMapOrigin(-33.87, 151.21)
        repository.setConnectMapOrigin(moved)
        assertEquals(moved, repository.settingsFlow.first().connectMapOrigin)
        repository.setConnectMapEnabled(false)
        assertEquals(moved, repository.settingsFlow.first().connectMapOrigin)
        repository.setConnectMapOrigin(null)
        assertNull(SettingsRepository(store, logger).settingsFlow.first().connectMapOrigin)
    }

    @Test
    fun incompleteOrCorruptSavedOriginIsIgnored() = runBlocking<Unit> {
        val store = MemoryStore()
        val repository = SettingsRepository(store, logger)
        store.updateData { it.toMutablePreferences().apply { this[SettingsKeys.CONNECT_MAP_LATITUDE] = 30.0 } }
        assertNull(repository.settingsFlow.first().connectMapOrigin)
        store.updateData { it.toMutablePreferences().apply { this[SettingsKeys.CONNECT_MAP_LONGITUDE] = 999.0 } }
        assertNull(repository.settingsFlow.first().connectMapOrigin)
        assertFailsWith<IllegalArgumentException> { ConnectMapOrigin(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { ConnectMapOrigin(0.0, 181.0) }
    }
}
