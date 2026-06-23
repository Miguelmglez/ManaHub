package com.mmg.manahub.feature.friends.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.feature.friends.data.repository.FriendRepositoryImpl
import com.mmg.manahub.core.domain.repository.FriendRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendModule {

    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    companion object {

        /**
         * Provides the [FriendshipClient] (Ktor-based) that replaces the old
         * Retrofit [FriendshipService] for all friendship/referral/stats PostgREST calls.
         */
        @Provides
        @Singleton
        fun provideFriendshipClient(
            @Named("supabaseKtor") httpClient: HttpClient,
        ): FriendshipClient = FriendshipClient(
            httpClient = httpClient,
            baseUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/",
        )

        /**
         * Provides the [FriendRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideFriendRemoteDataSource(
            client: FriendshipClient,
        ): FriendRemoteDataSource = FriendRemoteDataSource(client)
    }
}
