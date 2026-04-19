package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.SupabaseDeckDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindCardRepository(impl: CardRepositoryImpl): CardRepository

    @Binds @Singleton
    abstract fun bindUserCardRepository(impl: UserCardRepositoryImpl): UserCardRepository

    @Binds @Singleton
    abstract fun bindCollectionRemoteDataSource(impl: SupabaseCollectionDataSource): CollectionRemoteDataSource

    @Binds @Singleton
    abstract fun bindDeckRemoteDataSource(impl: SupabaseDeckDataSource): DeckRemoteDataSource

    @Binds @Singleton
    abstract fun bindDeckRepository(impl: DeckRepositoryImpl): DeckRepository

    @Binds @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds @Singleton
    abstract fun bindGameSessionRepository(impl: GameSessionRepositoryImpl): GameSessionRepository

    @Binds @Singleton
    abstract fun bindTournamentRepository(impl: TournamentRepositoryImpl): TournamentRepository

    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesDataStore): UserPreferencesRepository
}