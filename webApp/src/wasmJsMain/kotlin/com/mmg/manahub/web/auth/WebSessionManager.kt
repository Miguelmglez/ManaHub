package com.mmg.manahub.web.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/**
 * Web `actual` of supabase-kt's own [SessionManager] contract (web roadmap W2a).
 *
 * Persists the [UserSession] (access token + refresh token) as JSON in `window.localStorage`,
 * keyed by [SESSION_STORAGE_KEY]. This deliberately implements supabase-kt's real session
 * persistence interface — the same one Android's `SecureSessionManager` implements — rather than
 * a hand-rolled ad-hoc token store outside that contract (flagged as a follow-up by
 * `android-security-auditor`'s W0/W1 review; see `project_kmp_spike_findings` memory).
 *
 * Unlike Android's Keystore-backed implementation, this has NO additional encryption layer — there
 * is no browser-side equivalent of the Android Keystore, and `localStorage` is the standard,
 * origin-scoped persistence primitive web SPAs use for session tokens (the same trust boundary a
 * cookie-based session would have). Data survives a page reload/close but does NOT survive a full
 * browser "clear site data" wipe, same caveat as [com.mmg.manahub.core.common.LocalStorageKeyValueStore].
 *
 * `localStorage.getItem`/`setItem`/`removeItem` are synchronous browser APIs (confirmed gotcha-free
 * in W0/W1) — the `suspend` signatures inherited from [SessionManager] simply delegate straight
 * through, no dispatcher hop needed.
 */
class WebSessionManager : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        val json = Json.encodeToString(UserSession.serializer(), session)
        localStorage.setItem(SESSION_STORAGE_KEY, json)
    }

    override suspend fun loadSession(): UserSession? {
        val json = localStorage.getItem(SESSION_STORAGE_KEY) ?: return null
        return try {
            Json.decodeFromString(UserSession.serializer(), json)
        } catch (e: Exception) {
            // A corrupted/stale blob (e.g. from a schema change in a future supabase-kt version)
            // must never crash the app on load -- treat it as "no session", same fail-open
            // behavior as Android's SecureSessionManager on a decrypt failure.
            null
        }
    }

    override suspend fun deleteSession() {
        localStorage.removeItem(SESSION_STORAGE_KEY)
    }

    private companion object {
        const val SESSION_STORAGE_KEY = "manahub_web_session"
    }
}
