package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.SupabaseDeckDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
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

    // bindDeckRepository was REMOVED (KMP migration — Hilt→Koin cutover batch 3): DeckRepositoryImpl
    // lost its @Inject constructor and is now a native Koin `single` in
    // com.mmg.manahub.app.di.coreBridgeKoinModule. DeckRemoteDataSource stays Hilt-bound below (still
    // consumed by the still-Hilt SyncManager); DeckRepositoryImpl no longer uses it directly.

    // bindStatsRepository was REMOVED (KMP migration — Hilt→Koin cutover batch 7): StatsRepositoryImpl
    // lost its @Inject constructor and is now a native Koin `single` in
    // com.mmg.manahub.app.di.coreBridgeKoinModule. This breaks the Hilt startup-ordering hazard
    // where ManaHubApp's own @Inject statsRepository field triggered GlobalContext.get()
    // before startKoin() had run.

    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(impl: com.mmg.manahub.core.data.local.UserPreferencesDataStore): UserPreferencesRepository
}
