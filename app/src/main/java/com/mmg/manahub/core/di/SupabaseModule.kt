package com.mmg.manahub.core.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.auth.SecureSessionManager
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.createManaHubSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.ktor.client.engine.android.Android
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
        crashReporter: CrashReporter,
    ): SupabaseClient {
        // KMP web roadmap W2a (master plan §2.2 Action 1): client construction (Auth/Postgrest/
        // Realtime install, the WS7 call-counter plugin) now lives in the shared
        // createManaHubSupabaseClient factory (shared/core-data, commonMain) so Android and the
        // web target build an identical client. Android supplies only its platform-specific
        // pieces: the Keystore-backed session manager, the OkHttp Ktor engine, and the custom
        // "manahub" URI scheme used for the OAuth/deep-link redirect flow.
        return createManaHubSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
            sessionManager = SecureSessionManager(context),
            httpEngine = Android.create(),
            oauthScheme = "manahub",
            crashReporter = crashReporter,
        )
    }

    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth = client.auth
}
