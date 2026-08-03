package com.mmg.manahub.core.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor client-config helper (KMP web roadmap W2b, master plan §2.2 Action 2).
 *
 * Replaces the Android-only OkHttp interceptor that used to inject Supabase PostgREST headers
 * (`apikey`, `Authorization: Bearer <token>`, `Content-Type`/`Accept: application/json`) with a
 * Ktor-level, engine-agnostic mechanism, so Android (OkHttp engine) and Web (Js engine) share
 * IDENTICAL header-injection logic — only the transport engine differs per platform.
 *
 * Install into an `HttpClient(<Engine>) { ... }` config block, e.g.:
 * ```
 * HttpClient(OkHttp) { // or Js on wasmJs
 *     engine { ... }
 *     installSupabaseAuthHeaders(supabaseClient = get(), anonKey = SUPABASE_ANON_KEY)
 * }
 * ```
 *
 * The Bearer token is read fresh on every outgoing request via
 * [io.github.jan.supabase.auth.Auth.currentSessionOrNull] — a plain (non-suspend) in-memory read
 * of `Auth.sessionStatus`, so no dispatcher hop or suspend call is needed inside the plugin's
 * `onRequest` hook. Falls back to [anonKey] when there is no active session, matching the
 * pre-existing Android interceptor behavior exactly. Every header is force-set (removed then
 * re-appended) so a value already present on the request (e.g. `Content-Type` set by a caller's
 * own `contentType(...)` call) is overridden rather than duplicated — the same "last write wins"
 * semantics OkHttp's `Request.Builder.header(name, value)` had.
 *
 * Also installs [ContentNegotiation] with the SAME kotlinx-json config
 * (`ignoreUnknownKeys = true`, `encodeDefaults = true`) both platforms used before this
 * extraction, plus `expectSuccess = true` (non-2xx responses throw), so serialization/error
 * behavior stays byte-identical across platforms too.
 *
 * NOT a substitute for engine-specific concerns: request/response body **logging** (OkHttp's
 * `HttpLoggingInterceptor`, BODY in debug / NONE in release) has no Ktor-portable equivalent used
 * here and stays configured directly on the Android OkHttp engine client — see
 * `AuthKoinModule.kt`'s `"supabaseKtor"` registration.
 *
 * Does NOT replace the separate `@Named("supabase")` OkHttpClient used by
 * `AuthRepositoryImpl` for two raw (non-Ktor) Edge Function calls
 * (`delete-current-user`/`set-google-account-password`) — those bypass Ktor entirely, so they
 * keep their own OkHttp interceptor for apikey/Authorization injection. This helper only affects
 * traffic that goes through a Ktor `HttpClient` (`UserProfileClient`/`FriendshipClient`).
 */
fun HttpClientConfig<*>.installSupabaseAuthHeaders(
    supabaseClient: SupabaseClient,
    anonKey: String,
) {
    val supabaseAuthHeadersPlugin = createClientPlugin("SupabaseAuthHeaders") {
        onRequest { request, _ ->
            val accessToken = supabaseClient.auth.currentSessionOrNull()?.accessToken
            request.headers.setSingle("apikey", anonKey)
            request.headers.setSingle(HttpHeaders.Authorization, "Bearer ${accessToken ?: anonKey}")
            request.headers.setSingle(HttpHeaders.ContentType, "application/json")
            request.headers.setSingle(HttpHeaders.Accept, "application/json")
        }
    }
    install(supabaseAuthHeadersPlugin)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
    expectSuccess = true
}

/** Removes any existing values for [name] then appends exactly one — a Ktor-side "set", not "add". */
private fun HeadersBuilder.setSingle(name: String, value: String) {
    remove(name)
    append(name, value)
}
