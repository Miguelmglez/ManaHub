package com.mmg.manahub.feature.friends.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.feature.friends.domain.usecase.AcceptInviteUseCase
import com.mmg.manahub.feature.friends.domain.usecase.GetFriendCollectionUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SearchUserByGameTagUseCase
import com.mmg.manahub.feature.friends.domain.usecase.SendFriendRequestUseCase
import com.mmg.manahub.feature.friends.presentation.FriendsViewModel
import com.mmg.manahub.feature.friends.presentation.detail.FriendDetailViewModel
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherViewModel
import io.ktor.client.HttpClient
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
 * `TradesRepository` was originally a Friends-only `single` here; the Trades island PROMOTED it into
 * `coreBridgeKoinModule` (shared with Trades + the still-Hilt Home/FriendDetail), so it is now resolved
 * via `get()` and this module was shrunk accordingly. [PendingInviteStore] is still Friends-only here.
 *
 * ## KMP migration — Hilt→Koin cutover batch 4
 * [FriendshipClient] and [FriendRemoteDataSource] are now NATIVELY Koin-built here (the feature-private
 * Hilt `FriendModule` that used to `@Provides` them was deleted). `FriendRepository` itself moved to
 * `coreBridgeKoinModule` (shared with Profile) and resolves [FriendRemoteDataSource] from here via
 * `get()`. [FriendshipClient] needs the `@Named("supabaseKtor")` Ktor [HttpClient] — still Hilt-built by
 * the not-yet-converted `feature.auth.di.AuthModule` (`AuthModule` is deferred to the next batch) — so it
 * is forward-bridged from `ManaHubApp` as [supabaseKtorHttpClient]. It is registered here (not
 * `coreBridgeKoinModule`) because Friends is its ONLY Koin consumer, and no other `HttpClient` (Ktor)
 * single exists anywhere in the app yet, so no Koin qualifier is needed (mirrors the
 * `CommunityDecksKoinModule` OkHttpClient precedent).
 *
 * @param pendingInviteStore the Hilt-owned [PendingInviteStore] singleton (deferred invite codes).
 * @param supabaseKtorHttpClient the Hilt-owned `@Named("supabaseKtor")` [HttpClient] singleton
 *   (`AuthModule`, not yet converted) — needed to build [FriendshipClient] natively.
 * @return a Koin [Module] providing the Friends-only bridged singletons + the three ViewModel factories.
 */
fun friendsKoinModule(
    pendingInviteStore: PendingInviteStore,
    supabaseKtorHttpClient: HttpClient,
): Module = module {
    // ── Hilt → Koin bridge: Friends-only Hilt-owned singletons. ──
    // (FriendRepository, AuthRepository, AnalyticsHelper and TradesRepository are shared → bridged in
    //  coreBridgeKoinModule, not here, to avoid DefinitionOverrideException.)
    single { pendingInviteStore }

    // ── FriendshipClient / FriendRemoteDataSource: natively Koin-constructed (KMP migration batch 4;
    //    Hilt `FriendModule` deleted). Friends-only — FriendRepository (coreBridgeKoinModule) resolves
    //    FriendRemoteDataSource from here via `get()`. ──
    single {
        FriendshipClient(
            httpClient = supabaseKtorHttpClient,
            baseUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/",
        )
    }
    single { FriendRemoteDataSource(get()) }

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
