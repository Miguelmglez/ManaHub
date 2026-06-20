package com.mmg.manahub.core.common

/**
 * Minimal suspend key/value persistence contract, shared by Android and Web.
 *
 * Backed by Android DataStore Preferences on Android and by browser `localStorage` on the web
 * (currently a stub until `kotlinx-browser` is wired). Deliberately small — it covers the simple
 * scalar preferences shared code needs; richer typed preferences stay in the Android-only
 * `UserPreferencesDataStore` until they are commonized.
 */
interface KeyValueStore {

    /** Returns the stored string for [key], or [default] (which may be null) when absent. */
    suspend fun getString(key: String, default: String? = null): String?

    /** Persists [value] under [key]. */
    suspend fun putString(key: String, value: String)

    /** Returns the stored boolean for [key], or [default] when absent. */
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean

    /** Persists [value] under [key]. */
    suspend fun putBoolean(key: String, value: Boolean)

    /** Removes the value stored under [key], if any. */
    suspend fun remove(key: String)

    /** Removes every stored value. */
    suspend fun clear()
}
