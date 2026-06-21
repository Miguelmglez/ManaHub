package com.mmg.manahub.feature.carddetail.di

import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.feature.carddetail.presentation.CardDetailViewModel
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Card Detail feature is the eighth "Koin island":
 * [CardDetailViewModel] is resolved by Koin (`koinViewModel()`) while every other un-migrated feature
 * stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D (after Settings,
 * Stats, Profile, Home, TagDictionary, AddCard and CommunityDecks).
 *
 * ## Bridge pattern (same as the earlier islands)
 * [CardDetailViewModel] depends on twelve constructor arguments. The `SavedStateHandle` is supplied by
 * Koin itself; the other eleven are singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes the CardDetail-only
 * ones into [cardDetailKoinModule], which re-exposes them to Koin as `single { }`.
 *
 * `savedStateHandle = get()` resolves the Koin-injected [androidx.lifecycle.SavedStateHandle] that
 * carries the `scryfallId` nav arg (the CommunityDeckDetail precedent — `koinViewModel()` reads it from
 * the `NavBackStackEntry` `CreationExtras`), so the nav-arg behaviour is identical to the previous Hilt
 * resolution.
 *
 * Six of the eleven Hilt-owned dependencies are SHARED with other islands and are therefore NOT
 * registered here — they are bridged exactly once in `coreBridgeKoinModule` (registering the same type
 * in two loaded modules would throw `DefinitionOverrideException`) and resolved below via `get()`:
 * - `CardRepository` — shared with Home + CommunityDecks.
 * - `DeckRepository` — shared with Stats + Home + CommunityDecks.
 * - `UserPreferencesRepository` — shared with Settings + Stats.
 * - `UserPreferencesDataStore` — shared with Settings + Profile + Home + CommunityDecks.
 * - `AuthRepository` — shared with Settings + Profile + Home.
 * - `AnalyticsHelper` — shared with Settings (PROMOTED into `coreBridgeKoinModule` for this island —
 *   it was previously a Settings-only bridged singleton; Settings was shrunk to resolve it via `get()`).
 *
 * A seventh, `WishlistRepository`, is already bridged by `homeKoinModule` (the Home `wishlistRepository`
 * `single`). It is therefore NOT re-registered here either — registering a second `single<WishlistRepository>`
 * across two loaded modules would throw `DefinitionOverrideException` — and is resolved below via `get()`.
 *
 * The five [Module] params are the CardDetail-only bridged singletons consumed by no other Koin island.
 *
 * As features migrate further in Phase 1, each `single { hiltInstance }` here is replaced by a real Koin
 * provider and the matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing
 * without ever leaving the app uncompilable between commits.
 *
 * @param userCardRepository the Hilt-owned [UserCardRepository] singleton (this island only).
 * @param addToCollection the Hilt-owned [AddCardToCollectionUseCase] singleton (this island only).
 * @param addToWishlistUseCase the Hilt-owned [AddToWishlistUseCase] singleton (this island only).
 * @param openForTradeRepository the Hilt-owned [OpenForTradeRepository] singleton (this island only).
 * @return a Koin [Module] that provides the CardDetail-only bridged singletons and the
 *   [CardDetailViewModel] factory.
 */
fun cardDetailKoinModule(
    userCardRepository: UserCardRepository,
    addToCollection: AddCardToCollectionUseCase,
    addToWishlistUseCase: AddToWishlistUseCase,
    openForTradeRepository: OpenForTradeRepository,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the CardDetail-only Hilt-owned singletons to Koin. ──
    // (CardRepository, DeckRepository, UserPreferencesRepository, UserPreferencesDataStore,
    //  AuthRepository and AnalyticsHelper are shared → bridged in coreBridgeKoinModule; WishlistRepository
    //  is bridged in homeKoinModule. All seven are resolved below via get(), never re-registered here —
    //  a second single<T> for the same type across two loaded modules would throw DefinitionOverrideException.)
    single { userCardRepository }
    single { addToCollection }
    single { addToWishlistUseCase }
    single { openForTradeRepository }

    // ── The Koin island: CardDetailViewModel is now resolved by Koin, not Hilt. ──
    // Koin injects the SavedStateHandle (carrying the `scryfallId` nav arg) into the factory, so the
    // nav-arg behaviour is identical to the previous Hilt resolution.
    viewModel {
        CardDetailViewModel(
            savedStateHandle = get(),
            cardRepo = get(),
            userCardRepo = get(),
            deckRepo = get(),
            addToCollection = get(),
            addToWishlistUseCase = get(),
            wishlistRepo = get(),
            openForTradeRepo = get(),
            userPrefs = get(),
            userPreferencesDataStore = get(),
            authRepository = get(),
            helper = get(),
        )
    }
}
