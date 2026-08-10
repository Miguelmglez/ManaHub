package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.common.CrashReporter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Shared `SupabaseClient` builder (KMP web roadmap W2a, master plan §2.2 Action 1).
 *
 * Single source of truth for how ManaHub constructs its Supabase client (Auth + Postgrest +
 * Realtime + the WS7 call-counter telemetry plugin) so Android and Web stay behaviorally
 * identical and never drift. Platform callers supply the platform-specific pieces:
 *
 * - [sessionManager]: Android passes a Keystore-backed `SecureSessionManager`; web passes a
 *   `kotlinx-browser` `localStorage`-backed implementation of supabase-kt's own [SessionManager]
 *   contract (never a hand-rolled ad-hoc token store — see the web `SessionManager` actual).
 * - [httpEngine]: Android passes the OkHttp-backed `Android.create()`; web passes the wasmJs/js
 *   Ktor engine (`Js.create()`).
 * - [oauthScheme]: Android passes `"manahub"` (a custom URI scheme is required for the
 *   OAuth/deep-link redirect flow on a native app); web passes `null` since browser auth flows
 *   redirect through normal `https://` URLs, not a custom URI scheme — when `null`, `scheme`/
 *   `host` are left unset on the Auth plugin entirely.
 * - [crashReporter]: platform `CrashReporter` (Firebase Crashlytics on Android, a no-op on web) —
 *   feeds the same per-session Supabase call-count custom key on every platform.
 *
 * IMPORTANT: any change here affects BOTH platforms. This is live production auth code on
 * Android — keep call-site behavior (Auth config, plugin install order, call-counter semantics)
 * byte-for-byte equivalent to what `SupabaseModule.kt` used to construct inline.
 */
@OptIn(SupabaseInternal::class)
fun createManaHubSupabaseClient(
    supabaseUrl: String,
    supabaseKey: String,
    sessionManager: SessionManager,
    httpEngine: HttpClientEngine,
    oauthScheme: String?,
    crashReporter: CrashReporter,
): SupabaseClient {
    // WS7 telemetry (backend-performance-optimization-plan.md, 2026-07-29): there is exactly ONE
    // SupabaseClient/Ktor HttpClient construction site per platform (every Postgrest/Auth/Realtime
    // call funnels through it), so a call counter belongs here as a Ktor client plugin rather than
    // scattered across the many repositories that talk to Supabase. Non-atomic `var` is an
    // intentional, accepted approximation for telemetry -- a rare lost increment under a genuine
    // race is not worth a Mutex/AtomicInt here.
    var callCount = 0L
    val callCounterPlugin = createClientPlugin("SupabaseCallCounter") {
        onRequest { _, _ ->
            callCount++
            crashReporter.setCustomKey("supabase_calls_session", callCount.toString())
        }
    }
    return createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            this.sessionManager = sessionManager
            if (oauthScheme != null) {
                scheme = oauthScheme
                host = "auth"
            }
        }
        install(Postgrest)
        install(Realtime)
        this.httpEngine = httpEngine
        httpConfig {
            install(callCounterPlugin)
        }
    }
}
