package com.mmg.manahub.web.di

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.common.LocalStorageKeyValueStore
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.remote.createManaHubSupabaseClient
import com.mmg.manahub.web.auth.AuthViewModel
import com.mmg.manahub.web.auth.WebSessionManager
import com.mmg.manahub.web.config.WebAppConfig
import com.mmg.manahub.web.theme.ThemeShowcaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.engine.js.Js
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Root Koin module for `:webApp`. W1 registered only the [KeyValueStore] wasmJs actual + the
 * showcase screen's ViewModel. Web roadmap W2a adds the real [SupabaseClient] (guest-only auth
 * for now -- Google OAuth is a separate follow-up) built through the same shared
 * `createManaHubSupabaseClient` factory Android uses (`shared/core-data` commonMain), differing
 * only in [WebSessionManager] (localStorage-backed, not Android Keystore), the wasmJs/js Ktor
 * engine, and `oauthScheme = null` (web uses normal redirect URLs, not a custom URI scheme).
 */
val webAppKoinModule = module {
    single<KeyValueStore> { LocalStorageKeyValueStore() }

    single<CrashReporter> { provideCrashReporter() }

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

    viewModel { ThemeShowcaseViewModel(get()) }
    viewModel { AuthViewModel(get()) }
}
