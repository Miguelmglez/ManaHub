package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.feature.decks.domain.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.usecase.BudgetOptimizer
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.presentation.DeckMagicDetailViewModel
import com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
import com.mmg.manahub.feature.decks.presentation.DeckViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 3. The **Decks** Koin island.
 *
 * This is a multi-ViewModel island: it resolves ALL ViewModels under the `feature/decks`
 * tree via Koin — [DeckViewModel] (deck list), [DeckStudioViewModel] (the unified create+edit
 * surface, including its inline Deck Doctor Suggestions tab), and the legacy fallback
 * [DeckMagicDetailViewModel].
 *
 * `DeckImprovementViewModel`/`DeckImprovementScreen` (the standalone Deck Doctor surface) were
 * RETIRED in Phase 0.5 of `docs/claude-code-prompt-deck-doctor-community.md` (D10) — Deck Studio's
 * Suggestions tab is now the sole Deck Doctor UI. See `project_deck_studio_improvement_retirement`.
 *
 * ## The Deck Doctor scoring engine is now natively Koin-built (the Hilt `DeckDoctorModule` was DELETED)
 * Every class in the engine graph ([DeckScorer], [RoleClassifier], [ManaBaseAnalyzer], [EdhrecPowerResolver],
 * [DeckMagicEngine], [BudgetOptimizer], [CandidatePoolGenerator], [InferDeckIdentityUseCase] and the six
 * deck use cases) already lived in `:shared:core-domain` `commonMain` with NO `@Inject`/`@Singleton`
 * annotations (an earlier KMP-migration slice stripped them). The ONLY class that still had `@Inject`
 * was [com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder] (the still-Hilt Draft
 * consumer that motivated keeping `DeckDoctorModule` alive) — this batch de-Hilt's Draft too (see
 * `feature.draft.di.draftKoinModule`), so `DeckScorer` now has a SINGLE natively-Koin-built instance
 * shared by both islands (`get()` resolves the same singleton regardless of which module is loaded
 * first — Koin doesn't care about declaration order).
 *
 * ## Dependencies resolved via `get()` from OTHER loaded modules (NOT re-registered here)
 * Re-registering any of these would load a second `single<T>` for the same type into the one Koin
 * container → `DefinitionOverrideException` at `startKoin`:
 * - `DeckRepository`, `CardRepository`, `WishlistRepository`, `UserPreferencesRepository`,
 *   `UserPreferencesDataStore`, `AuthRepository`, `AnalyticsHelper`, `ProgressionEventBus` —
 *   from `coreBridgeKoinModule`.
 * - `UserCardRepository` — already a `single` in `cardDetailKoinModule`.
 * - `SyncManager` — already a `single` in `collectionKoinModule`.
 * - `SearchCardsUseCase`, `SuggestTagsUseCase`, `GetDeckGameStatsUseCase` — already `single`s in
 *   `SharedDomainKoinModule`.
 *
 * @param applicationScope the Hilt-owned `@ApplicationScope` [CoroutineScope] (legacy
 *   [DeckMagicDetailViewModel] only — survives the ViewModel for fire-and-forget sync work).
 * @return a Koin [Module] exposing the three Decks ViewModels + the Deck Doctor engine graph.
 */
fun decksKoinModule(
    applicationScope: CoroutineScope,
): Module = module {
    // ── Hilt → Koin bridge: the one remaining Hilt-owned singleton this island still needs. ──
    single { applicationScope }

    // ── Deck Doctor scoring engine (natively Koin-built; DeckDoctorModule's Hilt sibling was
    //    DELETED). Now `edhrec_rank` is persisted on Card, EdhrecPowerResolver derives the power
    //    signal from each card's EDHREC rank on a logarithmic scale. ──
    single<PowerResolver> { EdhrecPowerResolver(rankOf = { it.edhrecRank }) }
    single { RoleClassifier() }
    single { ManaBaseAnalyzer() }
    single {
        DeckScorer(
            roleClassifier = get(),
            power = get(),
            manaBaseAnalyzer = get(),
        )
    }
    single { DeckMagicEngine(deckScorer = get()) }

    // ── Candidate pool helpers. ──
    single { BudgetOptimizer() }
    single { CandidatePoolGenerator(cardRepository = get()) }

    // ── Deck use cases. ──
    single { InferDeckIdentityUseCase() }
    // Deck Doctor Community/Archetype plan, Phase 1.4: the archetype/theme classifier. Stateless
    // and pure — a single shared instance is safe.
    single { InferDeckArchetypeUseCase() }
    single { EvaluateDeckUseCase(deckScorer = get(), progressionEventBus = get(), inferDeckArchetypeUseCase = get()) }
    single { SuggestAddsUseCase(deckScorer = get()) }
    single { SuggestCutsUseCase(deckScorer = get()) }
    single {
        SuggestAddsWithBudgetUseCase(
            deckScorer = get(),
            candidatePoolGenerator = get(),
            budgetOptimizer = get(),
            cardRepository = get(),
        )
    }
    single {
        BuildDeckFromSeedsUseCase(
            deckScorer = get(),
            roleClassifier = get(),
            candidatePoolGenerator = get(),
            budgetOptimizer = get(),
        )
    }
    single { ImportDeckUseCase(cardRepository = get(), deckRepository = get()) }

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
            crashReporter = get(),
            appContext = get(),
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
