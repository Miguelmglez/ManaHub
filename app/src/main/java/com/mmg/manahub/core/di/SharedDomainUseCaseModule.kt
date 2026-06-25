package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.data.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RemoveCardUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.tagging.createStrategyAnalyzer
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetCardByNameUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import com.mmg.manahub.feature.draft.domain.usecase.LookupCardIdUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.domain.engine.DraftEngine
import kotlinx.coroutines.Dispatchers
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.survey.domain.usecase.CompleteSurveyUseCase
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt providers for the pure domain use cases that moved to `:shared:core-domain` `commonMain`
 * during the KMP migration (Phase 2+).
 *
 * These classes used to carry an `@Inject` constructor, but `javax.inject` is JVM-only and cannot be
 * imported in a KMP `commonMain` source set. They now expose plain constructors and Hilt builds them
 * here instead. Behaviour is unchanged: every prior construction site still receives a Hilt-owned
 * singleton -- the still-Hilt `AdvancedSearchViewModel` and the `ManaHubApp` field-injection bridge
 * that re-exposes these singletons to the Koin islands keep working as before.
 *
 * As each consuming feature finishes its Hilt->Koin cutover, the corresponding provider here is
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

    @Provides
    @Singleton
    fun provideRefreshCollectionPricesUseCase(
        userCardRepository: UserCardRepository,
        cardRepository: CardRepository,
        scryfallRemoteDataSource: ScryfallRemoteDataSource,
    ): RefreshCollectionPricesUseCase = RefreshCollectionPricesUseCase(
        userCardRepository, cardRepository, scryfallRemoteDataSource, DispatcherProvider(),
    )

    @Provides
    @Singleton
    fun provideSyncManaSymbolsUseCase(
        api: ScryfallClient,
        store: ManaSymbolStore,
        requestQueue: ScryfallRequestQueue,
    ): SyncManaSymbolsUseCase = SyncManaSymbolsUseCase(api, store, requestQueue)

    @Provides
    @Singleton
    fun provideStrategyAnalyzer(): StrategyAnalyzer = createStrategyAnalyzer()

    @Provides
    @Singleton
    fun provideSuggestTagsUseCase(
        strategyAnalyzer: StrategyAnalyzer,
    ): SuggestTagsUseCase = SuggestTagsUseCase(strategyAnalyzer)

    @Provides
    @Singleton
    fun provideScryfallCache(): ScryfallCache = ScryfallCache()

    @Provides
    @Singleton
    fun provideScryfallRemoteDataSource(
        api: ScryfallClient,
        requestQueue: ScryfallRequestQueue,
        cache: ScryfallCache,
    ): ScryfallRemoteDataSource = ScryfallRemoteDataSource(api, requestQueue, cache, DispatcherProvider())

    // -- Draft use cases (consumed by Hilt-owned DraftSimRepositoryImpl + ManaHubApp bridge). --

    @Provides
    @Singleton
    fun provideGetCardByNameUseCase(
        draftRepository: DraftRepository,
    ): GetCardByNameUseCase = GetCardByNameUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetDraftableSetsUseCase(
        draftRepository: DraftRepository,
    ): GetDraftableSetsUseCase = GetDraftableSetsUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetSetCardsPageUseCase(
        draftRepository: DraftRepository,
    ): GetSetCardsPageUseCase = GetSetCardsPageUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetSetCardsUseCase(
        draftRepository: DraftRepository,
    ): GetSetCardsUseCase = GetSetCardsUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetSetGuideUseCase(
        draftRepository: DraftRepository,
    ): GetSetGuideUseCase = GetSetGuideUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetSetTierListUseCase(
        draftRepository: DraftRepository,
    ): GetSetTierListUseCase = GetSetTierListUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideGetSetVideosUseCase(
        draftRepository: DraftRepository,
    ): GetSetVideosUseCase = GetSetVideosUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideLookupCardIdUseCase(
        draftRepository: DraftRepository,
    ): LookupCardIdUseCase = LookupCardIdUseCase(draftRepository)

    @Provides
    @Singleton
    fun provideObserveDraftUseCase(
        draftSimRepository: DraftSimRepository,
    ): ObserveDraftUseCase = ObserveDraftUseCase(draftSimRepository)

    @Provides
    @Singleton
    fun provideGetDraftableSimSetUseCase(
        draftSimRepository: DraftSimRepository,
    ): GetDraftableSimSetUseCase = GetDraftableSimSetUseCase(draftSimRepository, Dispatchers.IO)

    @Provides
    @Singleton
    fun provideStartDraftUseCase(
        draftSimRepository: DraftSimRepository,
        engine: DraftEngine,
    ): StartDraftUseCase = StartDraftUseCase(draftSimRepository, engine, Dispatchers.IO, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideMakePickUseCase(
        draftSimRepository: DraftSimRepository,
        engine: DraftEngine,
    ): MakePickUseCase = MakePickUseCase(draftSimRepository, engine, Dispatchers.IO, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAutoPickUseCase(
        draftSimRepository: DraftSimRepository,
        engine: DraftEngine,
    ): AutoPickUseCase = AutoPickUseCase(draftSimRepository, engine, Dispatchers.IO, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideCompleteDraftUseCase(
        draftSimRepository: DraftSimRepository,
        deckBuilder: DraftDeckBuilder,
    ): CompleteDraftUseCase = CompleteDraftUseCase(draftSimRepository, deckBuilder, Dispatchers.IO, Dispatchers.Default)

    // -- Trades use cases (consumed by still-Hilt ScannerViewModel + ManaHubApp field injection). --

    @Provides
    @Singleton
    fun provideAddToWishlistUseCase(
        wishlistRepository: WishlistRepository,
        authRepository: AuthRepository,
    ): AddToWishlistUseCase = AddToWishlistUseCase(wishlistRepository, authRepository)

    @Provides
    @Singleton
    fun provideMigrateLocalTradeListsUseCase(
        wishlistRepository: WishlistRepository,
        openForTradeRepository: OpenForTradeRepository,
    ): MigrateLocalTradeListsUseCase = MigrateLocalTradeListsUseCase(wishlistRepository, openForTradeRepository)

    // -- News use cases (consumed by ManaHubApp Hilt field injection). --

    @Provides
    @Singleton
    fun provideGetNewsFeedUseCase(
        newsRepository: NewsRepository,
    ): GetNewsFeedUseCase = GetNewsFeedUseCase(newsRepository)

    @Provides
    @Singleton
    fun provideManageSourcesUseCase(
        newsRepository: NewsRepository,
    ): ManageSourcesUseCase = ManageSourcesUseCase(newsRepository)

    @Provides
    @Singleton
    fun provideRefreshNewsFeedUseCase(
        newsRepository: NewsRepository,
    ): RefreshNewsFeedUseCase = RefreshNewsFeedUseCase(newsRepository)

    // -- Collection / survey use cases (moved from :app to :shared:core-domain, Phase 4). --

    @Provides
    @Singleton
    fun provideCompleteSurveyUseCase(
        progressionEventBus: ProgressionEventBus,
    ): CompleteSurveyUseCase = CompleteSurveyUseCase(progressionEventBus)

    @Provides
    @Singleton
    fun provideAddCardToCollectionUseCase(
        cardRepository: CardRepository,
        userCardRepository: UserCardRepository,
        progressionEventBus: ProgressionEventBus,
    ): AddCardToCollectionUseCase = AddCardToCollectionUseCase(
        cardRepository, userCardRepository, progressionEventBus,
    )

    @Provides
    @Singleton
    fun provideCommitScannedCardsUseCase(
        addCardToCollectionUseCase: AddCardToCollectionUseCase,
        progressionEventBus: ProgressionEventBus,
    ): CommitScannedCardsUseCase = CommitScannedCardsUseCase(
        addCardToCollectionUseCase, progressionEventBus,
    )
}
