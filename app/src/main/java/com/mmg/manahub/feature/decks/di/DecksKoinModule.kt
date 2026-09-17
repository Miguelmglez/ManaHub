package com.mmg.manahub.feature.decks.di
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.common.DataStoreKeyValueStore
import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.data.local.userPrefsDataStore
import com.mmg.manahub.core.data.remote.DeckstatsClient
import com.mmg.manahub.core.data.remote.DeckstatsFetcherImpl
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.KeyValueWizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.DeckstatsFetcher
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCaseV2
import com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckCardsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsUseCase
import com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.DiscoverSynergiesV2UseCase
import com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase
import com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel
import com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
import com.mmg.manahub.feature.decks.presentation.DeckViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
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
 * [InferDeckIdentityUseCase] and the six
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
    // Deck Wizard Commander v3 plan (Phase 0 / E4, D2): the ONE shared analysis entry point --
    // built from the same EvaluateDeckUseCase/InferDeckIdentityUseCase/CrashReporter singletons
    // above, consumed by DeckDoctorOrchestrator today and by the Commander builder in a later phase.
    single { DeckAnalysisPipeline(evaluateDeckUseCase = get(), inferDeckIdentityUseCase = get(), crashReporter = get()) }
    // Deck Wizard Commander v3 plan (Phase 2/6); generalized to every format by the Deck Wizard
    // 60-card wave (v6, plan §5 Phase 5.4) -- the ONE build engine for every anchor now, consuming
    // the SAME DeckAnalysisPipeline singleton above. Bound under BuildWizardDeckUseCase (not the
    // kept BuildWizardDeckUseCase typealias) so DeckWizardViewModel's own `buildWizardDeckUseCase`
    // param type resolves correctly -- see feedback_koin_single_concrete_type_mismatch.
    single { BuildWizardDeckUseCase(deckAnalysisPipeline = get(), crashReporter = get()) }
    // Deck Wizard v4, W7 Task B (E8) -- shares the app's own "user_prefs" DataStore file (never a
    // second preferences file) via the SAME internal accessor UserPreferencesDataStore uses. Bound
    // to the interface type (feedback_koin_single_concrete_type_mismatch): every consumer's
    // constructor param is typed as KeyValueStore/WizardPreferenceStore, never the concrete class.
    single<KeyValueStore> { DataStoreKeyValueStore(get<android.content.Context>().userPrefsDataStore) }
    single<WizardPreferenceStore> { KeyValueWizardPreferenceStore(get()) }
    single { SuggestAddsUseCase(deckScorer = get()) }
    // Deck Analysis Category Sections rework (W0, D3): the old Deck Doctor "Cuts" suggestion tab
    // was deleted, so NOTHING in production resolves `SuggestCutsUseCase` via Koin any more --
    // its `single { }` registration is intentionally NOT re-added here. The class itself SURVIVES
    // undeleted (not Koin-wired) because `app/src/test/.../harness/HarnessDoctorPipeline.kt`
    // directly constructs it (bypassing Koin) for the separate Wizard Quality Campaign's
    // "coherence-cuts-v2" ZERO-TOLERANCE QA gate -- deleting the class would have broken that
    // unrelated harness. `SuggestAddsFromCollectionUseCase` SURVIVES the same way (also
    // constructed directly by `HarnessDoctorPipeline.kt`, bypassing Koin) -- its own `single { }`
    // registration is deliberately NOT re-added here either (Deck Wizard 60-card wave v6, plan §5
    // Phase 7.1): its only production consumer, `the deleted Motor A wizard build use case`, was deleted along
    // with the rest of the Motor A wizard path. `SuggestAddsFromCommunityUseCase` had no surviving
    // caller at all once that same use case was deleted, so it (and its Koin binding) were deleted
    // outright, not just un-registered.
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

    single { CollectionProfileUseCase() }
    // Deck Wizard Commander v3 plan, Phase 4.1; generalized to every 60-card anchor by the Deck
    // Wizard 60-card wave (v6, plan §5 Phase 5.4) — the STRATEGY/COLOR_PICK/STRATEGY_PICK ranking
    // use case, still pure/dependency-free. Bound under RecommendWizardStrategiesUseCase (not the
    // deleted RecommendCommanderStrategiesUseCase typealias, Phase 7.1) so DeckWizardViewModel's
    // own `recommendCommanderStrategiesUseCase` param type resolves correctly -- see
    // feedback_koin_single_concrete_type_mismatch. Replaces the retired DeriveCommanderStrategiesUseCase.
    single { RecommendWizardStrategiesUseCase() }
    // Deck Wizard 60-card wave (v6, plan §5 Phase 7.1): `DeckTemplateResolver`,
    // `SuggestStrategiesForSeedsUseCase`, `RankOwnedCardsForProfileUseCase`, and
    // `the deleted Motor A wizard build use case` (the legacy Motor A wizard build engine, Casual's own build
    // path until this phase) were deleted along with their Koin bindings here -- every format now
    // builds through the single `BuildWizardDeckUseCase` registered above.
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
            calculateDeckValueSummaryUseCase = get(),
        )
    }

    // DeckWizardViewModel: Deck Builder v2 wizard (docs/plans/deck-builder-v2-plan.md §3.4), every
    // format since the Deck Wizard 60-card wave (v6, plan §5 Phase 5.4). A Commander build creates
    // its own fresh draft; a 60-card build always writes into the `deckId` nav arg (R13/S14) -- the
    // optional strategyHint/themeHint/colors args are the Discoveries v2 "Build this" hand-off (D11).
    viewModel {
        DeckWizardViewModel(
            deckRepository = get(),
            userCardRepository = get(),
            collectionProfileUseCase = get(),
            searchCardsUseCase = get(),
            communityAggregateRepository = get(),
            crashReporter = get(),
            appContext = get(),
            savedStateHandle = get(),
            // Deck Wizard & Engine Rework plan, Workstream 2 -- STRATEGY step's source 1 +
            // derivation ranking.
            cardStrategyTagsRepository = get(),
            recommendCommanderStrategiesUseCase = get(),
            // Deck Wizard Commander v3 plan, Phase 5 (D2) -- the SAME shared DeckAnalysisPipeline
            // singleton DeckDoctorOrchestrator/the harness already resolve, never a second instance.
            deckAnalysisPipeline = get(),
            // Deck Wizard Commander v3 plan, Phase 6; generalized to every format by the 60-card
            // wave -- onGenerate's ONE build path now.
            buildWizardDeckUseCase = get(),
            // Deck Wizard v4, W0.2 -- pre-warms real basic-land Card objects before a build.
            cardRepository = get(),
            // Deck Wizard v4, W7 Task B (E4/E8) -- biases placement toward previously-chosen Choice
            // picks and records freshly-made ones.
            wizardPreferenceStore = get(),
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
