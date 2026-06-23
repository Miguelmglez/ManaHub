package com.mmg.manahub.feature.trades.di

import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendsUseCase
import com.mmg.manahub.core.domain.repository.SharedListsRepository
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
 * KMP migration — Phase 1 Hilt→Koin cutover. The **Trades "Koin island"**: all FIVE trades
 * `*ViewModel`s are resolved via `koinViewModel()`. This is the most repo-entangled island so far —
 * the trades data layer is intentionally split across five repositories by concern, three of which
 * are also consumed by other islands and so live in [com.mmg.manahub.app.di.coreBridgeKoinModule].
 *
 * ## Shared-repository ownership (the entangled part)
 * Per CLAUDE.md, the five trades repositories own distinct behaviours and must NOT be merged. Their
 * Koin ownership:
 * - **TradesRepository** — promoted into `coreBridgeKoinModule` (shared with Friends + the still-Hilt
 *   `HomeViewModel`/`FriendDetailViewModel`); resolved here via `get()`. (Previously a Friends-island
 *   `single`; the Friends module was shrunk when this island promoted it.)
 * - **WishlistRepository** — promoted into `coreBridgeKoinModule` (shared with Home + CardDetail +
 *   still-Hilt Collection/DeckStudio/DeckImprovement); resolved here via `get()`. (Previously a Home
 *   `single`; the Home module was shrunk.)
 * - **OpenForTradeRepository** — promoted into `coreBridgeKoinModule` (shared with CardDetail +
 *   still-Hilt Collection); resolved here via `get()`. (Previously a CardDetail `single`; CardDetail
 *   was shrunk.)
 * - **SharedListsRepository** — trades-only → bridged here as a `single` (from the Hilt graph).
 * - **TradeSuggestionsRepository** — trades-only AND consumed by no migrated ViewModel → stays
 *   Hilt-owned in `TradesModule`; NOT bridged here.
 *
 * The Hilt `TradesModule` is KEPT (NOT deleted): its `@Binds` for `WishlistRepository`/
 * `OpenForTradeRepository`/`TradeSuggestionsRepository` are still consumed by Hilt features
 * (`CollectionViewModel`, `DeckStudioViewModel`, `DeckImprovementViewModel`). The bridge guarantees the
 * SAME singleton instance serves both DI graphs.
 *
 * @param sharedListsRepository the Hilt-owned [SharedListsRepository] singleton (trades-only).
 * @param tradeCollectionSyncDao the Room/`DatabaseModule`-owned [TradeCollectionSyncDao] (trades-only;
 *   used by [UpdateTradeCollectionUseCase] inside [TradeNegotiationViewModel]).
 * @return a Koin [Module] providing the trades-only bridged singletons, the trades use-case factories,
 *   and the five ViewModel factories.
 */
fun tradesKoinModule(
    sharedListsRepository: SharedListsRepository,
    tradeCollectionSyncDao: TradeCollectionSyncDao,
): Module = module {
    // ── Hilt → Koin bridge: trades-only Hilt-owned singletons. ──
    // (Shared repos — TradesRepository / WishlistRepository / OpenForTradeRepository — live in
    //  coreBridgeKoinModule and are resolved via get(); UserCardRepository is a CardDetail-island
    //  single and is also resolved via get(); AuthRepository / CardRepository / AnalyticsHelper /
    //  FriendRepository are all in coreBridge. Registering any of those here would load a duplicate
    //  single<T> across two modules → DefinitionOverrideException.)
    single { sharedListsRepository }
    single { tradeCollectionSyncDao }

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
