package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.DeckstatsClient
import com.mmg.manahub.core.data.remote.DeckstatsFetcherImpl
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.DeckstatsFetcher
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCaseV2
import com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.DeriveCommanderStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RankOwnedCardsForProfileUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestStrategiesForSeedsUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver
import com.mmg.manahub.feature.decks.domain.template.DiscoverSynergiesV2UseCase
import com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase
import com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel
import com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
import com.mmg.manahub.feature.decks.presentation.DeckViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * KMP migration — Hilt→Koin cutover batch 3. The **Decks** Koin island.
 *
 * This is a multi-ViewModel island: it resolves ALL ViewModels under the `feature/decks`
 * tree via Koin — [DeckViewModel] (deck list) and [DeckStudioViewModel] (the unified create+edit
 * surface, including its inline Deck Doctor Suggestions tab). The legacy fallback
 * `DeckMagicDetailViewModel` (and its `Screen.DeckDetail` route) was RETIRED in the Deck Wizard &
 * Engine Rework plan, Workstream 7.1 (2026-07-28) — parity with Deck Studio was confirmed first
 * (commander flow, land suggestions, grouping, sideboard movement, playtest button, share/export
 * all live in `DeckStudioScreen`/`DeckStudioViewModel`). Do not re-add it.
 *
 * `DeckImprovementViewModel`/`DeckImprovementScreen` (the standalone Deck Doctor surface) were
 * RETIRED in Phase 0.5 of `docs/claude-code-prompt-deck-doctor-community.md` (D10) — Deck Studio's
 * Suggestions tab is now the sole Deck Doctor UI. See `project_deck_studio_improvement_retirement`.
 *
 * ## The Deck Doctor scoring engine is now natively Koin-built (the Hilt `DeckDoctorModule` was DELETED)
 * Every class in the engine graph ([DeckScorer], [RoleClassifier], [ManaBaseAnalyzer], [EdhrecPowerResolver],
 * [CandidatePoolGenerator], [InferDeckIdentityUseCase] and the six
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
 * @return a Koin [Module] exposing the Decks ViewModels + the Deck Doctor engine graph.
 */
fun decksKoinModule(): Module = module {
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
    // ── Candidate pool helpers. ──
    // `BudgetOptimizer` was DELETED in WS7.3 (D-H, budget feature not coming back) alongside
    // `SuggestAddsWithBudgetUseCase`. `CandidatePoolGenerator` SURVIVES -- it has a live caller
    // (the wizard's Scryfall backstop, WS4) beyond the now-deleted dormant pipeline; see
    // `feedback_candidatepoolgenerator_no_longer_dormant` memory.
    single { CandidatePoolGenerator(cardRepository = get()) }

    // ── Deck use cases. ──
    single { InferDeckIdentityUseCase() }
    // Deck Doctor Community/Archetype plan, Phase 1.4: the archetype/theme classifier. Stateless
    // and pure — a single shared instance is safe.
    single { InferDeckArchetypeUseCase() }
    // Deck Analysis Engine v2 (Phase 2) -- the new unified pillar pipeline. Shares the same
    // ManaBaseAnalyzer singleton DeckScorer already uses above (P1 reuses it verbatim, never a
    // second instance).
    single { EvaluateDeckUseCaseV2(manaBaseAnalyzer = get()) }
    single {
        EvaluateDeckUseCase(
            deckScorer = get(),
            progressionEventBus = get(),
            inferDeckArchetypeUseCase = get(),
            evaluateDeckUseCaseV2 = get(),
            // Deck Analysis Engine v2 Phase 4 (telemetry/resilience) -- crashReporter comes from
            // coreBridgeKoinModule (single<CrashReporter>), same pattern as every other feature
            // module's crashReporter injection (see e.g. PuzzleKoinModule/GamificationEngineKoinModule).
            crashReporter = get(),
        )
    }
    single { SuggestAddsUseCase(deckScorer = get()) }
    // Deck Analysis Category Sections rework (W0, D3): the old Deck Doctor "Cuts" suggestion tab
    // was deleted, so NOTHING in production resolves `SuggestCutsUseCase` via Koin any more --
    // its `single { }` registration is intentionally NOT re-added here. The class itself SURVIVES
    // undeleted (not Koin-wired) because `app/src/test/.../harness/HarnessDoctorPipeline.kt`
    // directly constructs it (bypassing Koin) for the separate Wizard Quality Campaign's
    // "coherence-cuts-v2" ZERO-TOLERANCE QA gate -- deleting the class would have broken that
    // unrelated harness. `SuggestAddsFromCollectionUseCase`/`SuggestAddsFromCommunityUseCase`
    // SURVIVE (and stay Koin-registered) below for a similar but PRODUCTION reason: they are now
    // ALSO the Deck Wizard's own fill-from-collection/community placement engine
    // (`BuildDeckFromTemplateUseCase`, registered further down), not just Suggestions-tab-only as
    // their original (Phase 2/4) KDoc implied.
    single { SuggestAddsFromCollectionUseCase(deckScorer = get(), manaBaseAnalyzer = get()) }
    // `CommunityAggregateRepository` (communityAggregateKoinModule) and `CommunityDecksRepository`
    // (communityDecksKoinModule) are resolved via `get()` — both modules load in the same
    // ManaHubApp `modules(...)` call, so declaration order does not matter to Koin.
    single { SuggestAddsFromCommunityUseCase(cardRepository = get()) }
    single { FindSimilarDecksUseCase(communityDecksRepository = get()) }

    // Deck Doctor Community/Archetype plan, Phase 6: the deckstats.net import-by-URL adapter
    // (D17). A DEDICATED HttpClient with `expectSuccess = false` — deckstats returns a plain-text
    // (not JSON) body on a 400 "not found" response (`docs/adr/ADR-004-community-api-contracts.md`
    // §4), so the default `expectSuccess = true` convention every OTHER client in this codebase
    // uses would throw before [DeckstatsClient] could read that body. See [DeckstatsFetcher]'s KDoc.
    single { provideDeckstatsClient() }
    single<DeckstatsFetcher> { DeckstatsFetcherImpl(client = get(), crashReporter = get()) }

    // Deck Doctor Community/Archetype plan, Phase 6: the unified import pipeline.
    // ImportDeckUseCase / ImportCommunityDeckUseCase (feature.communitydecks) are now THIN
    // ADAPTERS over this — see their own KDocs.
    single { ImportDeckCardsUseCase(deckRepository = get(), cardRepository = get(), crashReporter = get(), deckstatsFetcher = get()) }
    single { ImportDeckUseCase(importDeckCardsUseCase = get()) }

    // Deck Builder v2 (docs/plans/deck-builder-v2-plan.md), Phase 1/2. Flag-gated OFF
    // (DeckFeatureFlags.DECK_BUILDER_V2_ENABLED) -- registered natively in Koin now so the wizard
    // (Phase 3, not yet built) can resolve them with no further DI work. `CommunityAggregateRepository`
    // is resolved via `get()` from `communityAggregateKoinModule` (same cross-module pattern as
    // SuggestAddsFromCommunityUseCase above).
    single { DeckTemplateResolver(communityAggregateRepository = get(), crashReporter = get()) }
    single { CollectionProfileUseCase() }
    // Deck Engine Unification plan (§5 Phase 3) — the wizard's own use cases for the two NEW entry
    // flows: Flow A's "suggested strategies from seeds" ranking and Flow B/C's "suggested seeds from
    // a picked profile" ranking. Both pure/dependency-free (no defaults needed here since Koin
    // always supplies a real instance; see each class's own KDoc).
    single { SuggestStrategiesForSeedsUseCase() }
    single { RankOwnedCardsForProfileUseCase() }
    // Deck Wizard & Engine Rework plan, Workstream 2.2 — the STRATEGY step's pure candidate
    // union/ranking. Pure/dependency-free, same registration convention as the two above.
    single { DeriveCommanderStrategiesUseCase() }
    // Deck Engine Unification plan (D1, live-wired in §5 Phase 3.5): build = the Doctor's own Motor A
    // loop, so BuildDeckFromTemplateUseCase shares the SAME SuggestAddsFromCollectionUseCase
    // singleton DeckDoctorOrchestrator uses. Motor B (community) is NOW wired live -- the wizard's
    // Review step gained a per-build "also use community trends" toggle
    // (DeckWizardSpec.useCommunityData) that ANDs with this global flag at build time
    // (BuildDeckFromTemplateUseCase.fetchCommunityOwnedCandidates checks BOTH), mirroring
    // DeckStudioViewModel's own `isCommunityEngineEnabled = { userPreferences
    // .communityEngineEnabledFlow.first() }` pattern exactly.
    single {
        BuildDeckFromTemplateUseCase(
            deckTemplateResolver = get(),
            deckScorer = get(),
            cardRepository = get(),
            suggestAddsFromCollectionUseCase = get(),
            manaBaseAnalyzer = get(),
            crashReporter = get(),
            communityAggregateRepository = get(),
            suggestAddsFromCommunityUseCase = get(),
            isCommunityEngineEnabled = { get<UserPreferencesDataStore>().communityEngineEnabledFlow.first() },
            // Deck Wizard & Engine Rework plan, Workstream 4.1 (F4 fix): the Scryfall backstop fill
            // phase, gated at runtime on DeckWizardSpec.includeOutsideCollection -- shares the SAME
            // CandidatePoolGenerator singleton registered above (no longer purely dormant, see that
            // class's KDoc).
            candidatePoolGenerator = get(),
        )
    }
    // Deck Builder v2, Phase 5 (docs/plans/deck-builder-v2-plan.md §3.5) -- Discoveries v2. Flag
    // -gated OFF by default until DeckFeatureFlags.DISCOVERIES_V2_ENABLED flips (this batch flips
    // it to true -- see that flag's KDoc).
    single { DiscoverSynergiesV2UseCase(deckScorer = get()) }

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
            getDeckGameStatsUseCase = get(),
            importDeckUseCase = get(),
            wishlistRepository = get(),
            userPreferences = get(),
            crashReporter = get(),
            appContext = get(),
            savedStateHandle = get(),
            findSimilarDecksUseCase = get(),
            importDeckCardsUseCase = get(),
            discoverSynergiesV2UseCase = get(),
            findCombosUseCase = get(),
            // Deck Wizard & Engine Rework plan, Workstream 6 -- unifies calculateLandDeltas onto the
            // SAME LandTargetResolver the wizard uses at build time (see DeckStudioViewModel's KDoc
            // on resolveStudioLandTarget).
            deckScorer = get(),
            manaBaseAnalyzer = get(),
            // Suggestions Tab UI Polish plan (W11 bug-fix pass, 2026-08-25) -- already a `single`
            // in SharedDomainKoinModule (shared with AdvancedSearchViewModel/AddCardViewModel).
            buildScryfallQueryUseCase = get(),
        )
    }

    // DeckWizardViewModel: Deck Builder v2 wizard (docs/plans/deck-builder-v2-plan.md §3.4). Always
    // creates its own fresh draft (no `deckId` nav arg, unlike DeckStudioViewModel) -- the optional
    // strategyHint/themeHint/colors args are the Discoveries v2 "Build this" hand-off (D11).
    viewModel {
        DeckWizardViewModel(
            deckRepository = get(),
            userCardRepository = get(),
            collectionProfileUseCase = get(),
            buildDeckFromTemplateUseCase = get(),
            searchCardsUseCase = get(),
            communityAggregateRepository = get(),
            crashReporter = get(),
            appContext = get(),
            savedStateHandle = get(),
            suggestStrategiesForSeedsUseCase = get(),
            rankOwnedCardsForProfileUseCase = get(),
            userPreferences = get(),
            // Deck Wizard & Engine Rework plan, Workstream 2 -- STRATEGY step's source 1 +
            // derivation ranking.
            cardStrategyTagsRepository = get(),
            deriveCommanderStrategiesUseCase = get(),
        )
    }
}

/**
 * Builds the dedicated [DeckstatsClient] (Deck Doctor Community/Archetype plan, Phase 6, D17).
 * `expectSuccess = false` is a DELIBERATE deviation from every other Ktor client in this codebase
 * (`provideArchidektClient`/`provideCommunityHttpClient` both use `expectSuccess = true`) — see
 * [DeckstatsClient]'s KDoc for why the endpoint's own non-JSON error shape requires it.
 */
private fun provideDeckstatsClient(): DeckstatsClient {
    val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                callTimeout(20, TimeUnit.SECONDS)
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "ManaHub/1.0 Android (deck import)")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                            else HttpLoggingInterceptor.Level.NONE
                })
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }
    return DeckstatsClient(httpClient)
}
