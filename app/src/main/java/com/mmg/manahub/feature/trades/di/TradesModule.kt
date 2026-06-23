package com.mmg.manahub.feature.trades.di

import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.SharedListsRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.TradeSuggestionsRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.feature.trades.data.repository.OpenForTradeRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.SharedListsRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.TradeSuggestionsRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.TradesRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.WishlistRepositoryImpl
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.SharedListsRepository
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TradesModule {

    @Binds @Singleton
    abstract fun bindTradesRepository(impl: TradesRepositoryImpl): TradesRepository

    @Binds @Singleton
    abstract fun bindWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository

    @Binds @Singleton
    abstract fun bindOpenForTradeRepository(impl: OpenForTradeRepositoryImpl): OpenForTradeRepository

    @Binds @Singleton
    abstract fun bindTradeSuggestionsRepository(impl: TradeSuggestionsRepositoryImpl): TradeSuggestionsRepository

    @Binds @Singleton
    abstract fun bindSharedListsRepository(impl: SharedListsRepositoryImpl): SharedListsRepository

    companion object {

        /**
         * Provides the [OpenForTradeRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideOpenForTradeRemoteDataSource(
            supabaseClient: SupabaseClient,
        ): OpenForTradeRemoteDataSource = OpenForTradeRemoteDataSource(supabaseClient)

        /**
         * Provides the [SharedListsRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideSharedListsRemoteDataSource(
            supabaseClient: SupabaseClient,
        ): SharedListsRemoteDataSource = SharedListsRemoteDataSource(supabaseClient)

        /**
         * Provides the [TradeSuggestionsRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideTradeSuggestionsRemoteDataSource(
            supabaseClient: SupabaseClient,
        ): TradeSuggestionsRemoteDataSource = TradeSuggestionsRemoteDataSource(supabaseClient)

        /**
         * Provides the [TradesRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideTradesRemoteDataSource(
            supabaseClient: SupabaseClient,
        ): TradesRemoteDataSource = TradesRemoteDataSource(supabaseClient)

        /**
         * Provides the [WishlistRemoteDataSource] now that it lives in `:shared:core-data`
         * `commonMain` (stripped of `@Inject`/`@IoDispatcher`; uses [DispatcherProvider]).
         */
        @Provides
        @Singleton
        fun provideWishlistRemoteDataSource(
            supabaseClient: SupabaseClient,
        ): WishlistRemoteDataSource = WishlistRemoteDataSource(supabaseClient)
    }
}
