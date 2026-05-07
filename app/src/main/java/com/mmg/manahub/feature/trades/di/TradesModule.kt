package com.mmg.manahub.feature.trades.di

import com.mmg.manahub.feature.trades.data.repository.OpenForTradeRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.SharedListsRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.TradeSuggestionsRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.TradesRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.WishlistRepositoryImpl
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.SharedListsRepository
import com.mmg.manahub.feature.trades.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}
