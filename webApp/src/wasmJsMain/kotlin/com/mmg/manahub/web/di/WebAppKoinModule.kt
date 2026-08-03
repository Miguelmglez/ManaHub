package com.mmg.manahub.web.di

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.common.LocalStorageKeyValueStore
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.createManaHubSupabaseClient
import com.mmg.manahub.core.data.remote.installSupabaseAuthHeaders
import com.mmg.manahub.core.data.repository.WebUserPreferencesRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.web.auth.AuthViewModel
import com.mmg.manahub.web.auth.WebSessionManager
import com.mmg.manahub.web.config.WebAppConfig
import com.mmg.manahub.web.theme.ThemeShowcaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Root Koin module for `:webApp`. W1 registered only the [KeyValueStore] wasmJs actual + the
 * showcase screen's ViewModel. Web roadmap W2a adds the real [SupabaseClient] (guest-only auth
 * for now -- Google OAuth is a separate follow-up) built through the same shared
 * `createManaHubSupabaseClient` factory Android uses (`shared/core-data` commonMain), differing
 * only in [WebSessionManager] (localStorage-backed, not Android Keystore), the wasmJs/js Ktor
 * engine, and `oauthScheme = null` (web uses normal redirect URLs, not a custom URI scheme).
 *
 * W2b adds the `@Named("supabaseKtor")` [HttpClient] (`Js` engine) that
 * [UserProfileClient]/[FriendshipClient] are built on -- the SAME shared
 * `installSupabaseAuthHeaders` plugin (`shared/core-data` commonMain,
 * `remote/SupabaseAuthHeaderPlugin.kt`) Android's `AuthKoinModule.kt` installs on its `OkHttp`
 * engine, so header injection and JSON (de)serialization behavior are byte-identical across
 * platforms; only the transport engine differs. The qualifier name matches Android's for
 * consistency, even though this is currently the only [HttpClient] registered here.
 *
 * W3a adds the [UserPreferencesRepository] binding, backed by [WebUserPreferencesRepository]
 * (`shared/core-data` wasmJsMain) — the first web data-layer repository slice. Bound against the
 * INTERFACE type (`single<UserPreferencesRepository> { ... }`), not the bare concrete class, per
 * this project's documented Koin gotcha: [ThemeShowcaseViewModel] declares its constructor
 * parameter as the interface, so a bare `single { WebUserPreferencesRepository(...) }` would
 * register under the concrete type and fail at runtime with `NoDefinitionFoundException` the first
 * time that graph path resolves.
 */
val webAppKoinModule = module {
    single<KeyValueStore> { LocalStorageKeyValueStore() }

    single<CrashReporter> { provideCrashReporter() }

    single<UserPreferencesRepository> { WebUserPreferencesRepository(keyValueStore = get()) }

    single<SupabaseClient> {
        createManaHubSupabaseClient(
            supabaseUrl = WebAppConfig.SUPABASE_URL,
            supabaseKey = WebAppConfig.SUPABASE_ANON_KEY,
            sessionManager = WebSessionManager(),
            httpEngine = Js.create(),
            oauthScheme = null,
            crashReporter = get(),
        )
    }

    single<HttpClient>(named("supabaseKtor")) {
        HttpClient(Js) {
            installSupabaseAuthHeaders(
                supabaseClient = get(),
                anonKey = WebAppConfig.SUPABASE_ANON_KEY,
            )
        }
    }

    single {
        UserProfileClient(
            httpClient = get(named("supabaseKtor")),
            baseUrl = "${WebAppConfig.SUPABASE_URL}/rest/v1/",
        )
    }
    single {
        FriendshipClient(
            httpClient = get(named("supabaseKtor")),
            baseUrl = "${WebAppConfig.SUPABASE_URL}/rest/v1/",
        )
    }

    viewModel { ThemeShowcaseViewModel(keyValueStore = get(), userPreferencesRepository = get()) }
    viewModel { AuthViewModel(get(), get()) }
}
