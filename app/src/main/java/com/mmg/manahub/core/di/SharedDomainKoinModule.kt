package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.tagging.StrategyAnalyzer
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.data.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.tagging.createStrategyAnalyzer
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetCardsPageUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.survey.domain.usecase.CompleteSurveyUseCase
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 2. Koin-native replacement for the bulk of the deleted Hilt
 * `SharedDomainUseCaseModule`: the cross-cutting hub of pure domain use cases consumed by the News,
 * Draft, Trades, Collection, Stats, AddCard, CardDetail, Decks and Survey Koin islands.
 *
 * ## Why this exists as its own module (not folded into a feature island)
 * Every use case here has consumers in MULTIPLE Koin islands (or is a natural cross-cutting concern —
 * e.g. [SyncManaSymbolsUseCase] is only called once, from `ManaHubApp` itself). Homing them in any one
 * feature module would be arbitrary. This mirrors `coreBridgeKoinModule`'s "shared singletons, one
 * place" rationale, just for use cases instead of repositories.
 *
 * ## Dependencies resolved via `get()` from already-loaded modules (NOT re-registered here)
 * `CardRepository`, `StatsRepository`, `UserCardRepository` (single in `cardDetailKoinModule`),
 * `DraftRepository`, `DraftSimRepository`, `WishlistRepository`, `OpenForTradeRepository`,
 * `AuthRepository`, `GameSessionRepository`, `ScryfallRemoteDataSource`, `ProgressionEventBus` and
 * `DispatcherProvider` are all already `single`s (`coreBridgeKoinModule` or an earlier island) —
 * resolved below via `get()`, never re-registered (a second `single<T>` for the same type across two
 * loaded modules throws `DefinitionOverrideException`). As of batch 3, `NewsRepository`
 * (`newsKoinModule`) and `DraftEngine`/`DraftDeckBuilder` (`draftKoinModule`) are ALSO natively
 * Koin-built and resolved the same way — no forward-bridge ctor param needed for them anymore.
 *
 * ## Newly forward-bridged singletons (ctor params — Hilt-built, feature-private modules KEPT this batch)
 * These three types have NO Koin presence yet because their owning Hilt module (`NetworkModule`,
 * `DatabaseModule`) is intentionally left untouched this batch: `ManaHubApp` `@Inject`s the
 * already-constructed Hilt singletons and passes them in here, exactly like every other bridge in this
 * migration.
 *
 * @param scryfallClient the Hilt-owned [ScryfallClient] (`NetworkModule`) — needed to build
 *   [SyncManaSymbolsUseCase].
 * @param scryfallRequestQueue the Hilt-owned [ScryfallRequestQueue] (`NetworkModule`) — the ONE shared
 *   rate-limit queue; needed to build [SyncManaSymbolsUseCase]. (`ScryfallRemoteDataSource`'s OWN queue
 *   usage is untouched — it keeps resolving this exact singleton through its existing Hilt provider.)
 * @param manaSymbolStore the Hilt-owned [ManaSymbolStore] (`DatabaseModule`) — needed to build
 *   [SyncManaSymbolsUseCase].
 * @return a Koin [Module] exposing every migrated use case as a `single`.
 */
fun sharedDomainKoinModule(
    scryfallClient: ScryfallClient,
    scryfallRequestQueue: ScryfallRequestQueue,
    manaSymbolStore: ManaSymbolStore,
): Module = module {
    // ── Hilt → Koin forward bridge: infra singletons with no Koin presence yet. ──
    single { scryfallClient }
    single { scryfallRequestQueue }
    single { manaSymbolStore }

    // ── AddCard / Collection / Stats use cases. ──
    single { SearchCardsUseCase(repository = get()) }
    single { BuildScryfallQueryUseCase() }
    single { GetCollectionSetCodesUseCase(repository = get()) }
    single { GetCollectionStatsUseCase(repository = get()) }
    single { GetCollectionUseCase(repository = get()) }
    single {
        RefreshCollectionPricesUseCase(
            userCardRepository = get(),
            cardRepository = get(),
            scryfallDataSource = get(),
            dispatcherProvider = get(),
        )
    }

    // ── Mana symbol sync (called once, from ManaHubApp, after startKoin()). ──
    single { SyncManaSymbolsUseCase(api = get(), store = get(), requestQueue = get()) }

    // ── Card tagging (Koin-native copy for the Decks island; CardRepositoryImpl keeps its OWN
    //    self-contained Hilt-built instance — see SharedDomainUseCaseModule. Both are stateless
    //    wrappers, so the two independent instances are behaviourally identical.) ──
    single<StrategyAnalyzer> { createStrategyAnalyzer() }
    single { SuggestTagsUseCase(strategyAnalyzer = get()) }

    // ── Draft use cases. GetSetCardsPageUseCase is ALSO consumed by DraftSimRepositoryImpl (now
    //    natively Koin-built, batch 3) — the residual Hilt SharedDomainUseCaseModule copies of these
    //    three use cases were DELETED this batch (DraftSimRepositoryImpl no longer needs Hilt to
    //    build them; it resolves the SAME singles below via get()).
    single { GetDraftableSetsUseCase(repository = get()) }
    single { GetSetGuideUseCase(repository = get()) }
    single { GetSetTierListUseCase(repository = get()) }
    single { GetSetVideosUseCase(repository = get()) }
    single { GetSetCardsPageUseCase(repository = get()) }
    single { ObserveDraftUseCase(repository = get()) }
    single { GetDraftableSimSetUseCase(repository = get(), ioDispatcher = Dispatchers.IO) }
    single {
        StartDraftUseCase(
            repository = get(),
            engine = get(),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )
    }
    single {
        MakePickUseCase(
            repository = get(),
            engine = get(),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )
    }
    single {
        AutoPickUseCase(
            repository = get(),
            engine = get(),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )
    }
    single {
        CompleteDraftUseCase(
            repository = get(),
            deckBuilder = get(),
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default,
        )
    }

    // ── Trades use cases. AddToWishlistUseCase is ALSO consumed by the still-Hilt (excluded)
    //    ScannerViewModel via the reverse KoinToHiltBridgeModule — same singleton instance either way. ──
    single { AddToWishlistUseCase(repo = get(), authRepo = get()) }
    single { MigrateLocalTradeListsUseCase(wishlistRepo = get(), openForTradeRepo = get()) }

    // ── News use cases. ──
    single { GetNewsFeedUseCase(repository = get()) }
    single { ManageSourcesUseCase(repository = get()) }
    single { RefreshNewsFeedUseCase(repository = get()) }

    // ── Collection / survey use cases. ──
    single { CompleteSurveyUseCase(progressionEventBus = get()) }
    single {
        AddCardToCollectionUseCase(
            cardRepository = get(),
            userCardRepository = get(),
            progressionEventBus = get(),
        )
    }
    // Also consumed by the still-Hilt (excluded) ScannerViewModel via the reverse
    // KoinToHiltBridgeModule — same singleton instance either way.
    single {
        CommitScannedCardsUseCase(
            addCardToCollection = get(),
            progressionEventBus = get(),
        )
    }

    // ── Deck use cases. ──
    single { GetDeckGameStatsUseCase(gameSessionRepository = get(), cardRepository = get()) }
}
