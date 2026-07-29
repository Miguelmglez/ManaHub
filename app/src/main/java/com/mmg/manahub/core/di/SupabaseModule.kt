package com.mmg.manahub.core.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.auth.SecureSessionManager
import com.mmg.manahub.core.common.CrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.api.createClientPlugin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
        crashReporter: CrashReporter,
    ): SupabaseClient {
        // WS7 telemetry (backend-performance-optimization-plan.md, 2026-07-29): there is exactly ONE
        // SupabaseClient/Ktor HttpClient construction site app-wide (every Postgrest/Auth/Realtime
        // call funnels through it), so a call counter belongs here as a Ktor client plugin rather than
        // scattered across the ~10 repositories that talk to Supabase. Non-atomic `var` is an
        // intentional, accepted approximation for telemetry (see the WS7 audit's design notes) -- a
        // rare lost increment under a genuine race is not worth a Mutex/AtomicInt here.
        var callCount = 0L
        val callCounterPlugin = createClientPlugin("SupabaseCallCounter") {
            onRequest { _, _ ->
                callCount++
                crashReporter.setCustomKey("supabase_calls_session", callCount.toString())
            }
        }
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                scheme = "manahub"
                host = "auth"
                sessionManager = SecureSessionManager(context)
            }
            install(Postgrest)
            install(Realtime)
            httpEngine = Android.create()
            httpConfig {
                install(callCounterPlugin)
            }
        }
    }

    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth = client.auth
}
