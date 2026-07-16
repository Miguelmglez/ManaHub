package com.mmg.manahub.feature.carddetail.di

import com.mmg.manahub.core.domain.usecase.collection.UpdateCollectionEntryUseCase
import com.mmg.manahub.feature.carddetail.presentation.CardDetailViewModel
import com.mmg.manahub.feature.trades.domain.usecase.UpdateWishlistEntryUseCase
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
 * - `UserCardRepository` (batch 6) — shared with Trades + Collection + Decks (PROMOTED into
 *   `coreBridgeKoinModule` to fix a Hilt/Koin startup circularity crash; see `ManaHubApp`'s KDoc).
 *
 * `WishlistRepository` and `OpenForTradeRepository` are both bridged in `coreBridgeKoinModule` (shared
 * with the Trades island, which PROMOTED them — `WishlistRepository` was previously a Home `single`,
 * `OpenForTradeRepository` a CardDetail `single` here until then). They are therefore NOT registered here
 * — registering the same type in two loaded modules would throw `DefinitionOverrideException` — and are
 * resolved below via `get()`.
 *
 * `AddCardToCollectionUseCase` and `AddToWishlistUseCase` (KMP migration batch 2) are now natively
 * Koin-built in `SharedDomainKoinModule` — resolved below via `get()`, not registered here anymore.
 * (`AddToWishlistUseCase` is ALSO re-exposed to the still-Hilt, excluded `ScannerViewModel` via
 * `KoinToHiltBridgeModule's reverse bridge — same singleton instance either way.)
 *
 * `UpdateCollectionEntryUseCase` and `UpdateWishlistEntryUseCase` (Card Versions & Languages, Phase
 * 1A) are likewise natively Koin-built in `SharedDomainKoinModule` — resolved below via `get()`.
 * They back the CardDetail edit flow added in Phase 1B (edit an existing collection/wishlist entry's
 * printing/language/foil/condition/quantity in place).
 *
 * As features migrate further in Phase 1, each `single { hiltInstance }` here is replaced by a real Koin
 * provider and the matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing
 * without ever leaving the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the [CardDetailViewModel] factory.
 */
fun cardDetailKoinModule(): Module = module {
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
            authRepository = get(),
            helper = get(),
            updateCollectionEntry = get(),
            updateWishlistEntry = get(),
        )
    }
}
