package com.mmg.manahub.core.di

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt providers for the pure domain use cases that moved to `:shared:core-domain` `commonMain`
 * during the KMP migration (Phase 2 — first use-case batch).
 *
 * These classes used to carry an `@Inject` constructor, but `javax.inject` is JVM-only and cannot be
 * imported in a KMP `commonMain` source set. They now expose plain constructors and Hilt builds them
 * here instead. Behaviour is unchanged: every prior construction site still receives a Hilt-owned
 * singleton — the still-Hilt `AdvancedSearchViewModel` and the `ManaHubApp` field-injection bridge
 * that re-exposes these singletons to the Koin islands (AddCard / Stats) keep working as before.
 *
 * As each consuming feature finishes its Hilt→Koin cutover, the corresponding provider here is
 * dropped and the use case is built directly in that feature's Koin module via its plain constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedDomainUseCaseModule {

    @Provides
    @Singleton
    fun provideSearchCardUseCase(
        cardRepository: CardRepository,
    ): SearchCardUseCase = SearchCardUseCase(cardRepository)

    @Provides
    @Singleton
    fun provideSearchCardsUseCase(
        cardRepository: CardRepository,
    ): SearchCardsUseCase = SearchCardsUseCase(cardRepository)

    @Provides
    @Singleton
    fun provideBuildScryfallQueryUseCase(): BuildScryfallQueryUseCase =
        BuildScryfallQueryUseCase()

    @Provides
    @Singleton
    fun provideGetCollectionSetCodesUseCase(
        statsRepository: StatsRepository,
    ): GetCollectionSetCodesUseCase = GetCollectionSetCodesUseCase(statsRepository)

    @Provides
    @Singleton
    fun provideGetCollectionStatsUseCase(
        statsRepository: StatsRepository,
    ): GetCollectionStatsUseCase = GetCollectionStatsUseCase(statsRepository)

    @Provides
    @Singleton
    fun provideGetCollectionUseCase(
        userCardRepository: UserCardRepository,
    ): GetCollectionUseCase = GetCollectionUseCase(userCardRepository)

    @Provides
    @Singleton
    fun provideRemoveCardUseCase(
        userCardRepository: UserCardRepository,
    ): RemoveCardUseCase = RemoveCardUseCase(userCardRepository)
}
