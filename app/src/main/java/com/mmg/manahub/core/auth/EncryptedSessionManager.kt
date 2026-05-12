package com.mmg.manahub.core.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyStoreException

private const val TAG = "EncryptedSessionManager"
private const val PREFS_FILE_NAME = "supabase_secure_session"
private const val SESSION_KEY = "supabase_session"

/**
 * A [SessionManager] implementation that stores the Supabase [UserSession] (access_token +
 * refresh_token) in [EncryptedSharedPreferences] backed by the Android Keystore (AES-256-GCM).
 *
 * The refresh token is long-lived (months) and must be protected at rest. Plain
 * SharedPreferences or DataStore without encryption are readable on rooted devices.
 *
 * If the Keystore is unavailable or corrupted, the implementation falls back to a volatile
 * in-memory store so the app remains functional for the current session without crashing.
 * The fallback is logged as an error so it is visible in crash reporting.
 */
class EncryptedSessionManager(private val context: Context) : SessionManager {

    /** Lazy so that Keystore initialisation only happens on first access. */
    private val encryptedPrefs by lazy { createEncryptedPrefs() }

    /**
     * In-memory fallback used when [EncryptedSharedPreferences] cannot be initialised.
     * Volatile to be safely read across coroutine threads (all suspend calls are serialised by
     * the Supabase SDK anyway, but defensive is better here).
     */
    @Volatile
    private var inMemorySession: UserSession? = null

    /** `true` after the first attempt to create prefs has conclusively failed. */
    @Volatile
    private var keystoreUnavailable = false

    // -------------------------------------------------------------------------
    // SessionManager interface
    // -------------------------------------------------------------------------

    override suspend fun loadSession(): UserSession? {
        if (keystoreUnavailable) return inMemorySession

        return try {
            val json = encryptedPrefs?.getString(SESSION_KEY, null) ?: return null
            Json.decodeFromString<UserSession>(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session from encrypted storage", e)
            inMemorySession
        }
    }

    override suspend fun saveSession(session: UserSession) {
        if (keystoreUnavailable) {
            inMemorySession = session
            return
        }

        try {
            val json = Json.encodeToString(session)
            encryptedPrefs?.edit()?.putString(SESSION_KEY, json)?.apply()
                ?: run {
                    // Prefs initialisation silently returned null — treat as unavailable
                    inMemorySession = session
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session to encrypted storage, using in-memory fallback", e)
            inMemorySession = session
        }
    }

    override suspend fun deleteSession() {
        inMemorySession = null

        if (keystoreUnavailable) return

        try {
            encryptedPrefs?.edit()?.remove(SESSION_KEY)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session from encrypted storage", e)
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds [EncryptedSharedPreferences] using an AES-256-GCM master key stored in the
     * Android Keystore. Returns `null` and sets [keystoreUnavailable] on any failure.
     *
     * minSdk = 29 (Android 10) — Keystore is always hardware-backed on API 29+, so the
     * StrongBox path is attempted first via [MasterKey.Builder] default behaviour.
     */
    private fun createEncryptedPrefs(): android.content.SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: KeyStoreException) {
            Log.e(TAG, "Keystore unavailable — falling back to in-memory session storage", e)
            keystoreUnavailable = true
            null
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Keystore security error — falling back to in-memory session storage", e)
            keystoreUnavailable = true
            null
        } catch (e: Exception) {
            // Catch-all for device-specific Keystore bugs on some OEM builds
            Log.e(TAG, "Unexpected error initialising EncryptedSharedPreferences", e)
            keystoreUnavailable = true
            null
        }
    }
}
