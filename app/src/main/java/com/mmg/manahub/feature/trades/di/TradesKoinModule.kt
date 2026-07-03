package com.mmg.manahub.feature.trades.di

import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.SharedListsRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.TradeSuggestionsRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.data.repository.SharedListsRepositoryImpl
import com.mmg.manahub.core.data.repository.TradeSuggestionsRepositoryImpl
import com.mmg.manahub.core.domain.repository.SharedListsRepository
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CounterProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CreateTradeProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.EditProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetActiveTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalOpenForTradeUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetLocalWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MarkCompletedUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.feature.trades.domain.usecase.SyncTradeListsFromRemoteUseCase
import com.mmg.manahub.feature.trades.domain.usecase.UpdateTradeCollectionUseCase
import com.mmg.manahub.feature.trades.presentation.TradeNegotiationViewModel
import com.mmg.manahub.feature.trades.presentation.TradeProposalViewModel
import com.mmg.manahub.feature.trades.presentation.TradesHistoryViewModel
import com.mmg.manahub.feature.trades.presentation.TradesSharedListViewModel
import com.mmg.manahub.feature.trades.presentation.TradesViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 3. The **Trades "Koin island"**: all FIVE trades
 * `*ViewModel`s are resolved via `koinViewModel()`. This is the most repo-entangled island so far —
 * the trades data layer is intentionally split across five repositories by concern, three of which
 * are also consumed by other islands and so live in [com.mmg.manahub.app.di.coreBridgeKoinModule].
 *
 * ## Everything below is now natively Koin-built (the feature-private Hilt `TradesModule` was DELETED)
 * `TradesRepositoryImpl`/`WishlistRepositoryImpl`/`OpenForTradeRepositoryImpl` had their
 * `@Inject`/`@Singleton` annotations stripped this batch (a full-codebase consumer audit found no
 * remaining Hilt-only consumer of any of the five trades repositories or their remote data sources —
 * the excluded trio touches none of them). `SharedListsRemoteDataSource`/`TradeSuggestionsRemoteDataSource`/
 * `TradesRemoteDataSource`/`WishlistRemoteDataSource`/`OpenForTradeRemoteDataSource` and
 * `SharedListsRepositoryImpl`/`TradeSuggestionsRepositoryImpl` were ALREADY plain classes (an earlier
 * KMP-migration slice moved them to `:shared:core-data` `commonMain`) — only their Hilt `@Provides`
 * wiring needed replacing.
 *
 * ## Shared-repository ownership (the entangled part)
 * Per CLAUDE.md, the five trades repositories own distinct behaviours and must NOT be merged. Their
 * Koin ownership:
 * - **TradesRepository** / **WishlistRepository** / **OpenForTradeRepository** — natively Koin-built in
 *   `coreBridgeKoinModule` (shared with Home/CardDetail/Collection/Decks); resolved here via `get()`.
 * - **SharedListsRepository** / **TradeSuggestionsRepository** — trades-only → natively Koin-built here.
 *
 * ## `SupabaseClient` forward bridge
 * The five trades remote data sources all need `SupabaseClient`, which had NO Koin presence before
 * this batch. It is now forward-bridged once in `coreBridgeKoinModule` (new `ManaHubApp` field,
 * shared cross-island) and resolved here via `get()`.
 *
 * @param tradeCollectionSyncDao the Room/`DatabaseModule`-owned [TradeCollectionSyncDao] (trades-only;
 *   used by [UpdateTradeCollectionUseCase] inside [TradeNegotiationViewModel]).
 * @param localWishlistDao the Room/`DatabaseModule`-owned [LocalWishlistDao] (needed to build
 *   [com.mmg.manahub.feature.trades.data.repository.WishlistRepositoryImpl] in `coreBridgeKoinModule`).
 * @param localOpenForTradeDao the Room/`DatabaseModule`-owned [LocalOpenForTradeDao] (needed to build
 *   [com.mmg.manahub.feature.trades.data.repository.OpenForTradeRepositoryImpl] in `coreBridgeKoinModule`).
 * @return a Koin [Module] providing the trades-only data layer, use-case factories, and the five
 *   ViewModel factories.
 */
fun tradesKoinModule(
    tradeCollectionSyncDao: TradeCollectionSyncDao,
    localWishlistDao: LocalWishlistDao,
    localOpenForTradeDao: LocalOpenForTradeDao,
): Module = module {
    // ── Hilt → Koin bridge: the Room-owned DAOs (Room stays androidMain / Hilt/DatabaseModule). ──
    single { tradeCollectionSyncDao }
    single { localWishlistDao }
    single { localOpenForTradeDao }

    // ── Trades remote data sources (natively Koin-built; TradesModule's Hilt sibling was DELETED).
    //    All five share the SupabaseClient forward-bridged in coreBridgeKoinModule. ──
    single { OpenForTradeRemoteDataSource(supabaseClient = get()) }
    single { SharedListsRemoteDataSource(supabaseClient = get()) }
    single { TradeSuggestionsRemoteDataSource(supabaseClient = get()) }
    single { TradesRemoteDataSource(supabaseClient = get()) }
    single { WishlistRemoteDataSource(supabaseClient = get()) }

    // ── Trades-only repositories (not shared with any other island). ──
    single<SharedListsRepository> { SharedListsRepositoryImpl(remote = get()) }
    single<TradeSuggestionsRepository> { TradeSuggestionsRepositoryImpl(remote = get()) }

    // ── Trades use cases (stateless; built over the bridged repositories). ──
    single { GetLocalWishlistUseCase(get()) }
    single { GetLocalOpenForTradeUseCase(get()) }
    single { SyncTradeListsFromRemoteUseCase(get(), get()) }
    single { CreateTradeProposalUseCase(get()) }
    single { EditProposalUseCase(get()) }
    single { CounterProposalUseCase(get()) }
    single { AcceptProposalUseCase(get()) }
    single { DeclineProposalUseCase(get()) }
    single { CancelProposalUseCase(get()) }
    single { RevokeAcceptanceUseCase(get()) }
    single { MarkCompletedUseCase(get()) }
    single { GetTradeThreadUseCase(get()) }
    single { RefreshTradeThreadUseCase(get()) }
    single { GetActiveTradesUseCase(get()) }
    single { GetTradeHistoryUseCase(get()) }
    single { RefreshTradesUseCase(get()) }
    single {
        UpdateTradeCollectionUseCase(
            userCardRepository = get(),
            wishlistRepository = get(),
            openForTradeRepository = get(),
            syncDao = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    // Friends use case consumed by TradesViewModel. Friends builds its OWN copies of its use cases
    // (over the bridged FriendRepository); GetFriendsUseCase is not among them, so register it here.
    single { GetFriendsUseCase(get()) }

    // ── ViewModels (one factory per trades ViewModel; nav args flow via the Koin SavedStateHandle). ──
    viewModel {
        TradesViewModel(
            authRepo = get(),
            getLocalWishlist = get(),
            getLocalOpenForTrade = get(),
            getFriends = get(),
            syncTradeListsFromRemote = get(),
        )
    }
    viewModel {
        TradeProposalViewModel(
            savedStateHandle = get(),
            authRepository = get(),
            tradesRepository = get(),
            createProposal = get(),
            editProposal = get(),
            counterProposal = get(),
            cardRepository = get(),
            userCardRepository = get(),
            wishlistRepository = get(),
            openForTradeRepository = get(),
            friendRepository = get(),
            analyticsHelper = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        TradeNegotiationViewModel(
            savedStateHandle = get(),
            authRepository = get(),
            friendRepository = get(),
            getThread = get(),
            refreshTradeThread = get(),
            acceptProposal = get(),
            declineProposal = get(),
            cancelProposal = get(),
            revokeAcceptance = get(),
            markCompleted = get(),
            updateTradeCollection = get(),
            tradeCollectionSyncDao = get(),
            analyticsHelper = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        TradesHistoryViewModel(
            authRepository = get(),
            friendRepository = get(),
            getActive = get(),
            getHistory = get(),
            refreshTrades = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        TradesSharedListViewModel(
            savedStateHandle = get(),
            sharedListsRepository = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
