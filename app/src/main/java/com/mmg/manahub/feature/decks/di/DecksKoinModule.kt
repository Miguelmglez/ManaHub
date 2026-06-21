package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.feature.decks.domain.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.presentation.DeckMagicDetailViewModel
import com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
import com.mmg.manahub.feature.decks.presentation.DeckViewModel
import com.mmg.manahub.feature.decks.presentation.improvement.DeckImprovementViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The **Decks** Koin island.
 *
 * This is a multi-ViewModel island: it resolves ALL ViewModels under the `feature/decks`
 * tree via Koin — [DeckViewModel] (deck list), [DeckStudioViewModel] (the unified create+edit
 * surface), [DeckImprovementViewModel] (inline Deck Doctor), and the legacy fallback
 * [DeckMagicDetailViewModel].
 *
 * ## Bridge strategy (Hilt-built objects re-exposed to Koin)
 * The Deck Doctor scoring engine ([com.mmg.manahub.feature.decks.domain.engine.DeckScorer]
 * and its `@Inject`-constructed graph) MUST stay Hilt-owned: the still-Hilt Draft feature
 * (`ScoringDraftDeckBuilder` in `DraftModule`) consumes the SAME `DeckScorer` singleton, and
 * the scoring use cases ([EvaluateDeckUseCase], [SuggestCutsUseCase],
 * [SuggestAddsWithBudgetUseCase], [BuildDeckFromSeedsUseCase], [DeckMagicEngine]) transitively
 * depend on it. Rebuilding them in Koin would create a SECOND `DeckScorer` instance that could
 * diverge from the one Draft uses. So — exactly like the Tournament island — the feature-private
 * Hilt `DeckDoctorModule` is KEPT, the engine + use cases keep their `@Inject` annotations, and
 * the Hilt-built singletons are BRIDGED in through [ManaHubApp] and handed to this module. The DI
 * cutover therefore touches only annotations/wiring, never any Deck Studio / Deck Doctor behaviour
 * (the `createdFreshDraft` discard contract, the `isImporting` exit-guard, the free-text budget
 * parsing, the incremental `AnalysisCache`/`GapSignature` logic all stay byte-for-byte identical).
 *
 * ## Dependencies resolved via `get()` from OTHER loaded modules (NOT re-registered here)
 * Re-registering any of these would load a second `single<T>` for the same type into the one Koin
 * container → `DefinitionOverrideException` at `startKoin`:
 * - `DeckRepository`, `CardRepository`, `WishlistRepository`, `UserPreferencesRepository`,
 *   `UserPreferencesDataStore`, `AuthRepository`, `AnalyticsHelper` — from `coreBridgeKoinModule`.
 * - `UserCardRepository` — already a `single` in `cardDetailKoinModule`.
 * - `SyncManager` — already a `single` in `collectionKoinModule`.
 * - `SearchCardsUseCase` — already a `single` in `addCardKoinModule`.
 *
 * @param suggestTags Hilt-owned [SuggestTagsUseCase] (Decks-only consumer among Koin islands).
 * @param evaluateDeck Hilt-owned [EvaluateDeckUseCase] (wraps the shared `DeckScorer`).
 * @param inferDeckIdentity Hilt-owned [InferDeckIdentityUseCase].
 * @param suggestCuts Hilt-owned [SuggestCutsUseCase] (wraps the shared `DeckScorer`).
 * @param suggestAddsWithBudget Hilt-owned [SuggestAddsWithBudgetUseCase] (wraps the shared `DeckScorer`).
 * @param buildDeckFromSeeds Hilt-owned [BuildDeckFromSeedsUseCase] (wraps the shared `DeckScorer`).
 * @param getDeckGameStats Hilt-owned [GetDeckGameStatsUseCase].
 * @param importDeck Hilt-owned [ImportDeckUseCase].
 * @param deckMagicEngine Hilt-owned [DeckMagicEngine] (wraps the shared `DeckScorer`).
 * @param applicationScope the Hilt-owned `@ApplicationScope` [CoroutineScope] (legacy
 *   [DeckMagicDetailViewModel] only — survives the ViewModel for fire-and-forget sync work).
 * @return a Koin [Module] exposing the four Decks ViewModels + the bridged Decks-only singletons.
 */
fun decksKoinModule(
    suggestTags: SuggestTagsUseCase,
    evaluateDeck: EvaluateDeckUseCase,
    inferDeckIdentity: InferDeckIdentityUseCase,
    suggestCuts: SuggestCutsUseCase,
    suggestAddsWithBudget: SuggestAddsWithBudgetUseCase,
    buildDeckFromSeeds: BuildDeckFromSeedsUseCase,
    getDeckGameStats: GetDeckGameStatsUseCase,
    importDeck: ImportDeckUseCase,
    deckMagicEngine: DeckMagicEngine,
    applicationScope: CoroutineScope,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Decks-only Hilt-owned singletons to Koin. ──
    // Each appears in NO other loaded module (audited) → no duplicate single<T>.
    single { suggestTags }
    single { evaluateDeck }
    single { inferDeckIdentity }
    single { suggestCuts }
    single { suggestAddsWithBudget }
    single { buildDeckFromSeeds }
    single { getDeckGameStats }
    single { importDeck }
    single { deckMagicEngine }
    single { applicationScope }

    // ── ViewModels (the Decks island) ──────────────────────────────────────────────
    // DeckViewModel: backs the deck list.
    viewModel { DeckViewModel(deckRepo = get(), cardRepo = get()) }

    // DeckStudioViewModel: the unified create+edit surface. `savedStateHandle = get()` carries
    // the optional "deckId" nav arg ("" ⇒ fresh draft) populated from the NavBackStackEntry.
    viewModel {
        DeckStudioViewModel(
            deckRepository = get(),
            cardRepository = get(),
            userCardRepository = get(),
            searchCardsUseCase = get(),
            suggestTagsUseCase = get(),
            evaluateDeckUseCase = get(),
            inferDeckIdentityUseCase = get(),
            suggestCutsUseCase = get(),
            suggestAddsWithBudgetUseCase = get(),
            buildDeckFromSeedsUseCase = get(),
            getDeckGameStatsUseCase = get(),
            importDeckUseCase = get(),
            deckMagicEngine = get(),
            wishlistRepository = get(),
            userPreferences = get(),
            appContext = get(),
            savedStateHandle = get(),
        )
    }

    // DeckImprovementViewModel: inline Deck Doctor. `savedStateHandle = get()` carries "deckId".
    viewModel {
        DeckImprovementViewModel(
            deckRepository = get(),
            cardRepository = get(),
            userCardRepository = get(),
            evaluateDeckUseCase = get(),
            inferDeckIdentityUseCase = get(),
            suggestCutsUseCase = get(),
            suggestAddsWithBudgetUseCase = get(),
            wishlistRepository = get(),
            userPreferences = get(),
            savedStateHandle = get(),
        )
    }

    // DeckMagicDetailViewModel: the legacy (unused fallback) editor. `savedStateHandle = get()`
    // carries "deckId".
    viewModel {
        DeckMagicDetailViewModel(
            deckRepository = get(),
            cardRepository = get(),
            userCardRepository = get(),
            authRepository = get(),
            suggestTagsUseCase = get(),
            userPreferencesRepo = get(),
            userPrefsStore = get(),
            syncManager = get(),
            workManager = get(),
            applicationScope = get(),
            savedStateHandle = get(),
            getDeckGameStatsUseCase = get(),
        )
    }
}
