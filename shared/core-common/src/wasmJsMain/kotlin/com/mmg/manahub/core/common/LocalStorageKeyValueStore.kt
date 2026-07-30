package com.mmg.manahub.core.common

import kotlinx.browser.localStorage

/**
 * wasmJs [KeyValueStore] actual, backed by real browser `window.localStorage` (web roadmap W1).
 *
 * `localStorage.getItem`/`setItem`/`removeItem`/`clear` are all SYNCHRONOUS browser APIs (no
 * Promise, no callback) — the `suspend` signatures inherited from [KeyValueStore] simply delegate
 * straight through, with no dispatcher hop needed (there is no blocking I/O here, unlike Android's
 * DataStore-backed [DataStoreKeyValueStore]). Data survives a page reload/close (this is the fix
 * for the previous in-memory stub, which lost everything on reload) but is scoped per-origin, like
 * all `localStorage` — it does NOT survive a full browser-data wipe or "clear site data".
 */
class LocalStorageKeyValueStore : KeyValueStore {

    override suspend fun getString(key: String, default: String?): String? =
        localStorage.getItem(key) ?: default

    override suspend fun putString(key: String, value: String) {
        localStorage.setItem(key, value)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        localStorage.getItem(key)?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(key: String, value: Boolean) {
        localStorage.setItem(key, value.toString())
    }

    override suspend fun remove(key: String) {
        localStorage.removeItem(key)
    }

    override suspend fun clear() {
        localStorage.clear()
    }
}
