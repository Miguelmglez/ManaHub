package com.mmg.manahub.feature.auth.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.data.repository.AuthRepositoryImpl
import com.mmg.manahub.core.domain.auth.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.Auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {

        /**
         * Provides a dedicated [OkHttpClient] for Supabase PostgREST calls.
         *
         * Each request is automatically decorated with:
         * - `apikey` header (Supabase anon key)
         * - `Authorization: Bearer <accessToken>` header (current session token,
         *   or anon key as fallback when unauthenticated)
         * - `Content-Type: application/json` and `Accept: application/json`
         *
         * [Auth.currentSessionOrNull] reads in-memory state from [Auth.sessionStatus] — it is
         * a plain (non-suspend) function, so no blocking or coroutine bridge is needed here.
         */
        @Provides
        @Singleton
        @Named("supabase")
        fun provideSupabaseOkHttpClient(supabaseAuth: Auth): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val accessToken = supabaseAuth.currentSessionOrNull()?.accessToken
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            .header(
                                "Authorization",
                                "Bearer ${accessToken ?: BuildConfig.SUPABASE_ANON_KEY}",
                            )
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .build()
                    )
                }
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BODY
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                    }
                )
                .build()
        }

        /**
         * Provides a Ktor [HttpClient] backed by the Supabase [OkHttpClient].
         *
         * This is the primary HTTP client for Supabase REST calls — used by
         * KMP-migrated API clients (e.g. [com.mmg.manahub.core.data.remote.FriendshipClient])
         * that need the same auth/apikey headers the OkHttp interceptor already provides.
         *
         * `encodeDefaults = true` mirrors the old Gson `serializeNulls()` behavior so that
         * fields with default values (e.g. `pLimit = 50`) are always serialized.
         */
        @Provides
        @Singleton
        @Named("supabaseKtor")
        fun provideSupabaseKtorClient(
            @Named("supabase") okHttpClient: OkHttpClient,
        ): HttpClient {
            return HttpClient(OkHttp) {
                engine {
                    preconfigured = okHttpClient
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
                expectSuccess = true
            }
        }

        /**
         * Provides the [UserProfileClient] for `user_profiles` table and RPC calls
         * using the Ktor [HttpClient] backed by the Supabase [OkHttpClient].
         */
        @Provides
        @Singleton
        fun provideUserProfileClient(
            @Named("supabaseKtor") httpClient: HttpClient,
        ): UserProfileClient = UserProfileClient(
            httpClient = httpClient,
            baseUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/",
        )

        /**
         * Provides the [UserProfileDataSource] using the Ktor-based client.
         */
        @Provides
        @Singleton
        fun provideUserProfileDataSource(
            client: UserProfileClient,
            @IoDispatcher dispatcher: CoroutineDispatcher,
        ): UserProfileDataSource = UserProfileDataSource(client, dispatcher)
    }
}
