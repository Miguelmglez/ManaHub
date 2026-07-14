package com.mmg.manahub.core.common

/**
 * wasmJs [KeyValueStore] actual.
 *
 * Minimal in-memory implementation so the web target compiles today. It is NOT yet persistent.
 *
 * TODO: back with window.localStorage once kotlinx-browser is wired (Phase 2/web work). The
 *  localStorage API is synchronous, so the suspend signatures will simply delegate to it.
 */
class LocalStorageKeyValueStore : KeyValueStore {

    private val store = mutableMapOf<String, String>()

    override suspend fun getString(key: String, default: String?): String? =
        store[key] ?: default

    override suspend fun putString(key: String, value: String) {
        store[key] = value
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        store[key]?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(key: String, value: Boolean) {
        store[key] = value.toString()
    }

    override suspend fun remove(key: String) {
        store.remove(key)
    }

    override suspend fun clear() {
        store.clear()
    }
}
