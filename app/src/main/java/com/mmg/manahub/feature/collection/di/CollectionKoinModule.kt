package com.mmg.manahub.feature.collection.di

import androidx.work.WorkManager
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.feature.collection.presentation.CollectionViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Collection feature is the seventeenth "Koin island":
 * its single [CollectionViewModel] is resolved by Koin (`koinViewModel()`) while every other un-migrated
 * feature stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D (after
 * Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash,
 * Survey, News, Draft, Playtest, Tournament and Trades).
 *
 * ## Bridge pattern (same as the earlier islands)
 * [CollectionViewModel] takes thirteen constructor arguments, all of which are singletons still owned by the
 * Hilt object graph. Rather than re-providing them in Koin — which would risk duplicate construction /
 * divergent state — `ManaHubApp` is the bridge: it `@Inject`s the already-constructed Hilt instances and
 * passes the Collection-only ones into [collectionKoinModule], which re-exposes them to Koin as
 * `single { }`. The shared ones are resolved via `get()` from the modules that already register them.
 *
 * `savedStateHandle = get()` resolves the Koin-injected [androidx.lifecycle.SavedStateHandle] that carries
 * the `tab` nav arg (if any), so the nav-arg behaviour is identical to the previous Hilt resolution.
 * All three Collection call-sites (Collection / DeckList / Trades tabs in `AppNavGraph`) use the screen's
 * default `viewModel` param, so the only call-site change is swapping that default `hiltViewModel()` →
 * `koinViewModel()` in `CollectionScreen`. No `AppNavGraph` edit.
 *
 * Six of the thirteen dependencies are SHARED with other islands and are therefore NOT registered here —
 * they are bridged exactly once in `coreBridgeKoinModule` (registering the same type in two loaded modules
 * would throw `DefinitionOverrideException`) and resolved below via `get()`:
 * - `CardRepository` — shared with Home + CommunityDecks + CardDetail.
 * - `AuthRepository` — shared with Settings + Profile + Home + CardDetail + Friends.
 * - `UserPreferencesRepository` — shared with Settings + Stats + CardDetail.
 * - `AnalyticsHelper` — shared with Settings + CardDetail + Friends + Draft.
 * - `WishlistRepository` — shared with Trades + Home + CardDetail (PROMOTED into `coreBridgeKoinModule`
 *   by the Trades island).
 * - `OpenForTradeRepository` — shared with Trades + CardDetail (PROMOTED into `coreBridgeKoinModule` by
 *   the Trades island).
 *
 * Two more dependencies are already `single`s in OTHER loaded feature modules and are resolved via `get()`
 * WITHOUT being re-registered here (a `single<T>` is resolvable from any loaded module; re-registering
 * would throw `DefinitionOverrideException`):
 * - `GetLocalWishlistUseCase` — already a `single` in `tradesKoinModule`.
 * - `UserCardRepository` — already a `single` in `cardDetailKoinModule`.
 *
 * The remaining two are Collection-only bridged singletons consumed by no other Koin island and are the
 * [Module] params here: [SyncManager] and [WorkManager] (the same Hilt-owned singleton `ManaHubApp`
 * already `@Inject`s for its own use).
 *
 * `GetCollectionUseCase` and `MigrateLocalTradeListsUseCase` (KMP migration batch 2) are now natively
 * Koin-built in `SharedDomainKoinModule` — resolved below via `get()`, not registered here anymore.
 *
 * As features migrate further in Phase 1, each `single { hiltInstance }` here is replaced by a real Koin
 * provider and the matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing
 * without ever leaving the app uncompilable between commits.
 *
 * @param syncManager the Hilt-owned [SyncManager] singleton (this island only).
 * @param workManager the Hilt-owned [WorkManager] singleton (this island only; the same instance
 *   `ManaHubApp` already injects for global sync scheduling).
 * @return a Koin [Module] that provides the Collection-only bridged singletons and the
 *   [CollectionViewModel] factory.
 */
fun collectionKoinModule(
    syncManager: SyncManager,
    workManager: WorkManager,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Collection-only Hilt-owned singletons to Koin. ──
    // (CardRepository, AuthRepository, UserPreferencesRepository, AnalyticsHelper, WishlistRepository and
    //  OpenForTradeRepository are shared → bridged in coreBridgeKoinModule; GetLocalWishlistUseCase and
    //  UserCardRepository are already singles in tradesKoinModule / cardDetailKoinModule; GetCollectionUseCase
    //  and MigrateLocalTradeListsUseCase are now singles in SharedDomainKoinModule. All resolved below via
    //  get(), never re-registered here — a second single<T> for the same type across two loaded modules
    //  would throw DefinitionOverrideException.)
    single { syncManager }
    single { workManager }

    // ── The Koin island: CollectionViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        CollectionViewModel(
            savedStateHandle = get(),
            getCollection = get(),
            cardRepository = get(),
            userCardRepository = get(),
            authRepository = get(),
            syncManager = get(),
            workManager = get(),
            migrateLocalTradeLists = get(),
            getLocalWishlist = get(),
            wishlistRepository = get(),
            openForTradeRepository = get(),
            userPreferencesRepository = get(),
            analyticsHelper = get(),
        )
    }
}
