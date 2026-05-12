package com.mmg.manahub.feature.auth.di

import com.google.gson.GsonBuilder
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.SupabaseUserProfileService
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.data.repository.AuthRepositoryImpl
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
         * [runBlocking] is intentional here: OkHttp interceptors run on IO threads,
         * so blocking the IO thread to retrieve the session token is acceptable and
         * avoids callback complexity in the interceptor chain.
         */
        @Provides
        @Singleton
        @Named("supabase")
        fun provideSupabaseOkHttpClient(supabaseAuth: Auth): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val accessToken = runBlocking {
                        supabaseAuth.currentSessionOrNull()?.accessToken
                    }
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
         * Provides the [Retrofit] instance scoped to the Supabase REST API base URL.
         *
         * Uses the dedicated [OkHttpClient] tagged with `@Named("supabase")` to avoid
         * sharing authentication headers with the Scryfall client defined in [NetworkModule].
         */
        @Provides
        @Singleton
        @Named("supabase")
        fun provideSupabaseRetrofit(
            @Named("supabase") client: OkHttpClient,
        ): Retrofit {
            val gson = GsonBuilder()
                .serializeNulls()
                .create()

            return Retrofit.Builder()
                .baseUrl("${BuildConfig.SUPABASE_URL}/rest/v1/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }

        /**
         * Provides the [SupabaseUserProfileService] for `user_profiles` table and RPC calls.
         */
        @Provides
        @Singleton
        fun provideSupabaseUserProfileService(
            @Named("supabase") retrofit: Retrofit,
        ): SupabaseUserProfileService = retrofit.create(SupabaseUserProfileService::class.java)

        /**
         * Provides the [UserProfileDataSource] using the Retrofit-based service.
         */
        @Provides
        @Singleton
        fun provideUserProfileDataSource(
            service: SupabaseUserProfileService,
            @IoDispatcher dispatcher: CoroutineDispatcher,
        ): UserProfileDataSource = UserProfileDataSource(service, dispatcher)
    }
}
