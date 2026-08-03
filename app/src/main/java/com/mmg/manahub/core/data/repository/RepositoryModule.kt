package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource
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

    // bindDeckRepository was REMOVED (KMP migration — Hilt→Koin cutover batch 3): DeckRepositoryImpl
    // lost its @Inject constructor and is now a native Koin `single` in
    // com.mmg.manahub.app.di.coreBridgeKoinModule. DeckRepositoryImpl no longer uses
    // DeckRemoteDataSource directly.

    // bindDeckRemoteDataSource was REMOVED (KMP web roadmap W3c): SupabaseDeckDataSource moved to
    // :shared:core-data commonMain and lost its @Inject/@Singleton (Hilt is androidMain-only), so a
    // @Binds abstract fun can no longer target it. It is now provided via
    // SharedDomainUseCaseModule.provideDeckRemoteDataSource (mirrors the pre-existing
    // provideScryfallRemoteDataSource @Provides pattern) — still consumed by the still-Hilt
    // SyncManager, behavior unchanged.

    // bindStatsRepository was REMOVED (KMP migration — Hilt→Koin cutover batch 7): StatsRepositoryImpl
    // lost its @Inject constructor and is now a native Koin `single` in
    // com.mmg.manahub.app.di.coreBridgeKoinModule. This breaks the Hilt startup-ordering hazard
    // where ManaHubApp's own @Inject statsRepository field triggered GlobalContext.get()
    // before startKoin() had run.

    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(impl: com.mmg.manahub.core.data.local.UserPreferencesDataStore): UserPreferencesRepository
}
