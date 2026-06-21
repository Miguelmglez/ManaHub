package com.mmg.manahub.feature.friends.di

import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.feature.friends.domain.usecase.AcceptInviteUseCase
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendCollectionUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import com.mmg.manahub.feature.friends.presentation.FriendsViewModel
import com.mmg.manahub.feature.friends.presentation.detail.FriendDetailViewModel
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherViewModel
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Friends feature is the ninth "Koin island" and the
 * heaviest so far (~24 transitive deps). It is also the second MULTI-ViewModel island (after
 * CommunityDecks): all THREE of its ViewModels are migrated together so no call-site is left half-wired:
 * - [FriendsViewModel] — the friends list / search screen (5 ctor deps).
 * - [FriendDetailViewModel] — a friend's collection / stats / history (5 ctor deps; reads a `"userId"`
 *   nav arg from a Koin-injected `SavedStateHandle`).
 * - [InviteDispatcherViewModel] — processes invite deep links (3 ctor deps; **Activity-scoped** —
 *   resolved in `AppNavGraph` via `koinViewModel(viewModelStoreOwner = activity)`).
 *
 * ## Bridge pattern (same as the prior eight islands)
 * The shared singletons these VMs depend on are still owned by the Hilt object graph. `ManaHubApp` is
 * the bridge: it `@Inject`s the already-constructed Hilt instances and hands them to this module, which
 * re-exposes the Friends-only ones to Koin as `single { }`.
 *
 * Shared singletons are NOT registered here — they are bridged exactly once in `coreBridgeKoinModule`
 * (registering the same type in two loaded modules throws `DefinitionOverrideException`) and resolved
 * via `get()`:
 * - [com.mmg.manahub.core.domain.repository.FriendRepository] `FriendRepository` — shared with the
 *   Profile island. It was PROMOTED into `coreBridgeKoinModule` (and `profileKoinModule` shrunk to
 *   resolve it via `get()`) when Friends began consuming it.
 * - `AuthRepository` — shared with Settings + Profile + Home + CardDetail.
 * - `AnalyticsHelper` — shared with Settings + CardDetail.
 *
 * The four use cases all depend only on `FriendRepository` (resolved via the bridge `get()`), so they
 * are simple Koin factories registered as `single { }` here (none appears in any other loaded module).
 * [TradesRepository] and [PendingInviteStore] are Friends-only here too (no other island registers them).
 *
 * @param tradesRepository the Hilt-owned [TradesRepository] singleton (FriendDetail trade history).
 * @param pendingInviteStore the Hilt-owned [PendingInviteStore] singleton (deferred invite codes).
 * @return a Koin [Module] providing the Friends-only bridged singletons + the three ViewModel factories.
 */
fun friendsKoinModule(
    tradesRepository: TradesRepository,
    pendingInviteStore: PendingInviteStore,
): Module = module {
    // ── Hilt → Koin bridge: Friends-only Hilt-owned singletons. ──
    // (FriendRepository, AuthRepository and AnalyticsHelper are shared → bridged in
    //  coreBridgeKoinModule, not here, to avoid DefinitionOverrideException.)
    single { tradesRepository }
    single { pendingInviteStore }

    // ── Friends-only use cases (each depends only on the bridged FriendRepository). ──
    single { SearchUserByGameTagUseCase(get()) }
    single { SendFriendRequestUseCase(get()) }
    single { GetFriendCollectionUseCase(get()) }
    single { AcceptInviteUseCase(get()) }

    // ── The Koin island: all three Friends ViewModels are now resolved by Koin, not Hilt. ──
    viewModel {
        FriendsViewModel(
            friendRepo = get(),
            authRepo = get(),
            searchUseCase = get(),
            sendRequestUseCase = get(),
            analyticsHelper = get(),
        )
    }
    viewModel {
        // savedStateHandle = get() resolves the Koin-injected SavedStateHandle carrying the
        // "userId" nav arg — identical behaviour to the old Hilt-injected SavedStateHandle.
        FriendDetailViewModel(
            savedStateHandle = get(),
            friendRepo = get(),
            getFriendCollectionUseCase = get(),
            tradesRepo = get(),
            authRepo = get(),
        )
    }
    viewModel {
        InviteDispatcherViewModel(
            acceptInviteUseCase = get(),
            pendingInviteStore = get(),
            authRepo = get(),
        )
    }
}
