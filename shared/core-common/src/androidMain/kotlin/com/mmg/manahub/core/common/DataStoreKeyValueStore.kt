package com.mmg.manahub.core.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Android [KeyValueStore] actual backed by DataStore Preferences.
 *
 * Constructed with the caller's `DataStore<Preferences>` so it can share the app's existing
 * preferences file rather than opening a second one (which would diverge state). This is a plain
 * class (not an `expect/actual` of [KeyValueStore]) because the Android constructor needs a
 * DataStore while the web one does not — the shared contract is the [KeyValueStore] interface.
 */
class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    override suspend fun getString(key: String, default: String?): String? =
        dataStore.data.first()[stringPreferencesKey(key)] ?: default

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        dataStore.data.first()[booleanPreferencesKey(key)] ?: default

    override suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit {
            it.remove(stringPreferencesKey(key))
            it.remove(booleanPreferencesKey(key))
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
