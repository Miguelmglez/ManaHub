package com.mmg.manahub.feature.decks.domain.orchestrator

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.toAnalysisWeights
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.withUnresolvedFinding
import com.mmg.manahub.feature.decks.domain.usecase.AddOrigin
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SimilarDeckResult
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Deck Wizard & Engine Rework plan, Workstream 8.4: the named progress phases the Suggestions
 * tab's FULL [DeckDoctorOrchestrator.loadAnalysis] pass moves through -- mirrors
 * [com.mmg.manahub.feature.decks.domain.template.BuildStage]'s visual language (the same
 * "current stage + completed-stages checklist" UI pattern the wizard's generating step already
 * uses, not a second divergent design). Ordinal order is display order.
 *
 * NEVER set from [DeckDoctorOrchestrator.recomputeIncremental] or a bare
 * [DeckDoctorOrchestrator.recomputeAdds] (budget-change) call -- those stay instant/unstaged by
 * design (see [DeckDoctorOrchestrator.loadAnalysis]'s KDoc); only the full analysis pass emits
 * [DeckDoctorState.stage] transitions.
 */
enum class DoctorAnalysisStage {
    /** Snapshotting the live deck + resolving the mainboard/commander/seed tags, before Health/
     * Cuts are computed. */
    READING_DECK_PLAN,
    /** Motor A ([com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase])
     * ranking the owned collection. */
    EVALUATING_COLLECTION,
    /**
     * Motor B ([com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase] /
     * [com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase]) -- runs
     * CONCURRENTLY with the stages around it ([recomputeCommunityInternal] is fire-and-forget,
     * never awaited by [loadAnalysis]), so this stage is best-effort/informational only: it can
     * legitimately appear slightly out of the 1-2-3-4-5 order relative to the other stages, but
     * never regresses the displayed sequence backward (see [advanceDoctorStage]) and never
     * resurrects the staged screen once Motor A + the backstop have already finished.
     */
    SEARCHING_COMMUNITY,
    /** The Workstream 8.2 Scryfall backstop
     * ([com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator]) -- a NEW latency
     * source only when [DeckDoctorState.includeOutsideCollection] is on; a near-instant no-op
     * otherwise. */
    SEARCHING_CARD_POOL,
    /** Final merge/sort/cap of the ADD list -- the last stage before the Suggestions tab reveals
     * real content. */
    RANKING_SUGGESTIONS,
}

/**
 * Read-only Deck Doctor (Suggestions surface) state, mirrored into a host ViewModel's own UI
 * state by collecting [DeckDoctorOrchestrator.state].
 */
data class DeckDoctorState(
    /** Read-only Health evaluation from the scoring engine. Null until first computed. */
    val health: DeckHealth? = null,
    /** Cut candidates (worst fit first), excluding lands / commander / combo cores. */
    val cuts: List<CardFit> = emptyList(),
    /** Add suggestions — Motor A (collection-only, Phase 2), best fit first. */
    val adds: List<AddSuggestion> = emptyList(),
    /**
     * Total money the currently shown adds would cost to buy. Always `0.0` for Motor A (every
     * candidate is already owned) — kept on the state shape so a future budget-aware source
     * (Motor B, Phase 4) does not need a UI-facing rename.
     */
    val addsTotalCostEur: Double = 0.0,
    /** How many of the shown adds have a non-zero price. Always `0` for Motor A. */
    val addsCardsToBuy: Int = 0,
    /** True while the full analysis (Health + Cut + Add) is being computed. */
    val isSuggestionsLoading: Boolean = false,
    /** True while the ADD list is being (re)computed. */
    val isAddsLoading: Boolean = false,
    /** True once at least one full [DeckDoctorOrchestrator.loadAnalysis] has completed. */
    val isLoaded: Boolean = false,

    // ── Staged progress (Deck Wizard & Engine Rework plan, Workstream 8.4) ──────────────────────
    /** Non-null while the FULL [loadAnalysis] pass is progressing (Motor A + the Scryfall
     * backstop); null once that pass finishes, or when no full analysis is in flight (a budget
     * recompute / incremental add-cut never sets this). Drives the Suggestions tab's staged
     * progress screen -- see [DoctorAnalysisStage]. */
    val stage: DoctorAnalysisStage? = null,
    /** Stages already finished THIS pass, oldest first (the completed-stages checklist under the
     * current stage, mirroring [com.mmg.manahub.feature.decks.domain.template.BuildStage]'s UI).
     * Reset to empty at the start of every [loadAnalysis]. */
    val completedStages: List<DoctorAnalysisStage> = emptyList(),
    /**
     * Deck Engine Unification plan (D4): mirrors `Deck.strategyLocked`. While true, [cuts] never
     * includes a [DeckCardSource.WIZARD]-sourced card (see [loadAnalysis]/[recomputeIncremental]'s
     * `protectedIds` extension) and the host UI hides the "Deck plan" archetype/theme editor. Flip
     * off via [unlockStrategy] (an explicit, confirmed user action).
     */
    val strategyLocked: Boolean = false,

    // ── Scryfall backstop -- 3rd adds source at evaluation time (Deck Wizard & Engine Rework
    //    plan, Workstream 8.2). Reuses the WS4 CandidatePoolGenerator (the wizard's own build-time
    //    last-resort fill source) as a Suggestions-tab source; entirely additive to Motor A -- when
    //    OFF (the default), [adds] is byte-identical to pre-WS8.2. Mirrors [communityUnavailable]'s
    //    degrade-not-hide contract exactly. ──────────────────────────────────────────────────────
    /** The user's own per-session toggle (mirrors the wizard's `includeOutsideCollection` — a
     * SEPARATE, Suggestions-tab-local choice, not shared state with the wizard). Resets to `false`
     * on every full [loadAnalysis]. */
    val includeOutsideCollection: Boolean = false,
    /** True when [includeOutsideCollection] is on but the last Scryfall backstop fetch failed --
     * [adds] still shows Motor A's (and Motor B's) results in that case, never a silently thinner
     * list passed off as "nothing more to suggest". */
    val outsideCollectionUnavailable: Boolean = false,

    // ── Motor B — community suggestions (Deck Doctor Community/Archetype plan, Phase 4) ────────
    // Entirely additive to Motor A above and gated behind `communityEngineEnabledFlow` (D4):
    // when the flag is off (or the deck's format has no community aggregate — Draft), every field
    // in this block simply stays at its empty/false default and the Studio UI renders nothing
    // community-related. A Worker/aggregate failure NEVER touches `adds`/`cuts`/`health` above —
    // see [recomputeCommunityInternal]'s KDoc.
    /** "Popular in similar decks" — ranked by the community aggregate's own synergy. */
    val communityAdds: List<CommunityAddSuggestion> = emptyList(),
    /** "Decks like yours" carousel — real, importable Archidekt decks. */
    val similarDecks: List<SimilarDeckResult> = emptyList(),
    /** True while the community aggregate + similar-decks fetch is in flight. */
    val isCommunityLoading: Boolean = false,
    /**
     * True when the community engine is ENABLED but the Worker/aggregate could not be reached (a
     * degraded-but-not-dead state, per D3's fallback layering) — the Studio shows a single
     * `InlineErrorState` for the Motor B section only. `false` (not an error) when the flag is off
     * or the community fetch simply has not run yet.
     */
    val communityUnavailable: Boolean = false,
)

/** One-shot Deck Doctor events, delivered through a buffered [Channel] (never a nullable StateFlow). */
sealed interface DeckDoctorEvent {
    /**
     * The ADD suggestion computation threw. Motor A ([SuggestAddsFromCollectionUseCase]) is a pure,
     * offline, in-memory computation, so this is a purely DEFENSIVE catch (an unexpected bug, never
     * a network failure) — kept from the pre-Motor-A (Scryfall-backed) pipeline so the host's
     * existing non-fatal warning toast wiring needs no change. See `project_deck_doctor_phase2_motor_a`
     * memory for why the name/shape survived the Phase 2 swap unchanged.
     */
    data object ExternalPoolFailed : DeckDoctorEvent
}

/**
 * Owns the Deck Doctor "Suggestions" incremental-analysis machinery: the [AnalysisCache] pattern,
 * the full [loadAnalysis] pass, and incremental card add/cut recompute — extracted out of
 * `DeckStudioViewModel` (Phase 0.4, `docs/claude-code-prompt-deck-doctor-community.md`) so Phase
 * 1's archetype-aware engine and Phase 2's Motor A have a single home for this orchestration
 * instead of two independently-maintained VM-side copies.
 *
 * Pure `commonMain`: platform-only concerns (Crashlytics, DataStore-backed weight overrides,
 * cache-aware card resolution) are injected as callbacks/interfaces by the host ViewModel rather
 * than referenced directly, so this class has zero Android/AndroidX imports.
 *
 * ## Ownership contract
 * One instance per "live deck editing session" (i.e. one per `DeckStudioViewModel` instance,
 * constructed with that ViewModel's own `viewModelScope`) — it is NOT a singleton. All work is
 * launched on the [scope] passed at construction time; the host's `onCleared()` cancelling that
 * scope also cancels any in-flight analysis.
 *
 * ## H3 — cancel-before-launch on the ADD recompute (preserved exactly)
 * A budget edit, an incremental recompute (after a suggestion add/cut), and the initial full
 * analysis can all race to recompute the ADD list; each would emit a fresh adds list into [state].
 * [recomputeAddsJob] is cancelled immediately before every new launch (mirroring the
 * pre-extraction `DeckStudioViewModel` behavior verbatim) so only the LATEST recompute can ever
 * survive to update [state] — a stale, slower computation can never clobber a newer one.
 *
 * ## Phase 2 — Motor A is the PRIMARY (and, as of this phase, only) adds source
 * `docs/claude-code-prompt-deck-doctor-community.md` Phase 2 replaces the previous
 * `SuggestAddsWithBudgetUseCase` pipeline (wishlist + external Scryfall via `CandidatePoolGenerator`/
 * `BudgetOptimizer`, D5 -- that whole pipeline was DELETED in the Deck Wizard & Engine Rework plan,
 * WS7.3, 2026-07-28, D-H: the budget feature is not coming back) with
 * [SuggestAddsFromCollectionUseCase]: an OFFLINE, ALWAYS-AVAILABLE source with no flag gate (unlike
 * the future Cloudflare-Worker-backed Motor B, Phase 4, which IS flag-gated behind
 * `communityEngineEnabledFlow`). Because Motor A only ever suggests already-owned cards, the whole
 * external-pool-fetch-avoidance apparatus the old pipeline needed (a `GapSignature` cache key +
 * `externalPool` re-fetch guard, to avoid hammering Scryfall on every recompute) is genuinely dead
 * weight now — there is no network call to avoid, and a full in-memory Motor A recompute on every
 * incremental add/cut is cheap — so it was removed rather than left unused; see
 * `project_deck_doctor_phase2_motor_a` memory. Budget UI ([BudgetConstraints]) is intentionally
 * still threaded through every public method here (D5: "the code stays intact, you're just not
 * surfacing budget controls") even though Motor A ignores it — zero VM-side signature churn for a
 * change that only touches HOW the adds list is computed, not the API host `DeckStudioViewModel`
 * calls.
 *
 * @param scope the host's coroutine scope (typically `viewModelScope`) that every internal job
 *        launches on.
 * @param resolveCard cache-aware card resolution (scryfallId → Card), owned by the host so this
 *        class never needs its own duplicate card cache.
 * @param weightsProvider supplies the current (possibly debug-overridden) [ScoreWeightOverrides];
 *        the host owns the actual DataStore-backed source (Android-only) behind this suspend
 *        lambda so this class stays commonMain-pure.
 */
class DeckDoctorOrchestrator(
    private val scope: CoroutineScope,
    private val deckRepository: DeckRepository,
    private val userCardRepository: UserCardRepository,
    private val wishlistRepository: WishlistRepository,
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val suggestCutsUseCase: SuggestCutsUseCase,
    private val suggestAddsFromCollectionUseCase: SuggestAddsFromCollectionUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val crashReporter: CrashReporter,
    private val resolveCard: suspend (scryfallId: String) -> Card?,
    private val weightsProvider: suspend () -> ScoreWeightOverrides,
    // ── Motor B (Phase 4) — appended last, all defaulted, so no existing positional-arg
    // constructor call site (see `project_archetype_engine` memory's "append new optional params
    // at the end" rule) needs to change. `null`/`{ false }` defaults mean an un-migrated caller
    // simply never sees any community state populated (identical to today's behavior).
    private val communityAggregateRepository: CommunityAggregateRepository? = null,
    private val suggestAddsFromCommunityUseCase: SuggestAddsFromCommunityUseCase? = null,
    private val findSimilarDecksUseCase: FindSimilarDecksUseCase? = null,
    private val isCommunityEngineEnabled: suspend () -> Boolean = { false },
    // ── Scryfall backstop (Workstream 8.2) -- appended last, defaulted null, same "no existing
    // positional-arg call site needs to change" precedent as Motor B above. `null` is a silent
    // no-op: [DeckDoctorState.includeOutsideCollection] can still be toggled on by the UI, but
    // [recomputeAddsInternal] simply never finds a generator to call, so `adds` stays Motor-A-only.
    private val candidatePoolGenerator: CandidatePoolGenerator? = null,
    /** Deck Analysis Engine v2 Phase 0 carve-out: `FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED`
     * lives in the `:app` module and can't be imported here (commonMain layering) — the host supplies it
     * as a plain lambda, same pattern as [isCommunityEngineEnabled]. Defaults to `true` (today's behavior)
     * so the existing test constructor call site and any future un-migrated caller keep working unchanged;
     * the real Android host wires the actual (currently `false`) flag value explicitly. */
    private val isSuggestionsEngineEnabled: () -> Boolean = { true },
) {

    private val _state = MutableStateFlow(DeckDoctorState())
    val state: StateFlow<DeckDoctorState> = _state.asStateFlow()

    private val _events = Channel<DeckDoctorEvent>(Channel.BUFFERED)
    val events: Flow<DeckDoctorEvent> = _events.receiveAsFlow()

    /**
     * Everything an in-memory incremental re-analysis needs after the first full [loadAnalysis].
     * A suggestion add/cut mutates [AnalysisCache.workingMainboard] and recomputes
     * profile/evaluation/cuts/adds locally. Null until the first full analysis runs.
     */
    private var analysisCache: AnalysisCache? = null

    private var analysisJob: Job? = null

    /** The in-flight ADD recompute job (H3) — see the class doc for the cancel-before-launch contract. */
    private var recomputeAddsJob: Job? = null

    /** The in-flight Motor B (community) fetch job — cancelled before every new launch, mirroring H3. */
    private var communityJob: Job? = null

    /**
     * Workstream 8.4 -- bumped once per [loadAnalysis] call, BEFORE its coroutine is launched. Every
     * [recomputeAddsInternal]/[recomputeCommunityInternal] invocation that is PART OF a specific
     * [loadAnalysis] pass captures the generation value active at that moment (`ownerGeneration`)
     * and re-checks it against the CURRENT [stagingGeneration] before applying a [DoctorAnalysisStage]
     * transition. This is what stops an old, superseded [loadAnalysis] pass's own
     * [recomputeAddsJob]/[communityJob] (siblings of [analysisJob], NOT its children -- cancelling
     * [analysisJob] alone does not stop them) from emitting a stale stage update after a NEWER
     * [loadAnalysis] call has already taken over — the two jobs are independently cancelled via
     * their own existing cancel-before-launch calls, but only once the new pass's code reaches
     * them, which can lag behind the old jobs' own in-flight network calls.
     */
    private var stagingGeneration: Int = 0

    private class AnalysisCache(
        var workingMainboard: List<DeckEntry>,
        val format: DeckFormat,
        val commanderId: String?,
        val commanderIdentity: Set<String>,
        /** The resolved commander's name (Motor B: EDHREC commander-aggregate lookup key). Null for
         * non-Commander decks or an unresolved commander. */
        val commanderName: String?,
        val seedTags: List<CardTag>,
        val collection: List<Card>,
        /**
         * Kept for a possible future wishlist-backed suggestion source — Motor A (Phase 2) reads
         * ONLY [collection], never this. See the class KDoc's "Phase 2" section.
         */
        val wishlistIds: Set<String>,
        val resolvedById: MutableMap<String, Card>,
        val unresolvedCount: Int,
        /** Wave 2 / B3: total sideboard card count, captured once per full [loadAnalysis] pass and
         * reused UNCHANGED by every incremental [recomputeIncremental] call (mainboard add/cut
         * suggestions never touch the sideboard) — fed to P5's
         * [com.mmg.manahub.feature.decks.domain.engine.Finding.SideboardOversized] check. */
        val sideboardCount: Int,
        // ── Archetype-aware Deck Doctor (Phase 1.6, D2) ─────────────────────────
        /** Raw `Deck.archetypeOverride`/`themesOverride` — re-read on every incremental recompute
         * so a mid-session override change (via [setArchetypeOverride]/[clearArchetypeOverride])
         * is picked up without a full [loadAnalysis]. */
        var archetypeOverride: String?,
        var themesOverride: List<String>,
        val commanderTags: List<CardTag>,
        // ── Deck Engine Unification (D4) ────────────────────────────────────────
        /** Mirrors `Deck.strategyLocked` -- re-read on every full [loadAnalysis] (an unlock is
         * always followed by a full reload, see [unlockStrategy]). */
        val strategyLocked: Boolean,
        /** Scryfall ids of every mainboard slot whose persisted `source == WIZARD` -- folded into
         * [suggestCutsUseCase]'s `protectedIds` while [strategyLocked] (D4 hard no-cut guarantee). */
        val wizardSourcedIds: Set<String>,
        // ── Scryfall backstop (Workstream 8.2) ──────────────────────────────────
        /** The user's per-session Suggestions-tab toggle -- see [setIncludeOutsideCollection] and
         * [DeckDoctorState.includeOutsideCollection]'s KDoc. Always starts `false` on a fresh
         * [loadAnalysis]. */
        var includeOutsideCollection: Boolean = false,
    )

    /**
     * Full analysis: snapshot the live deck, resolve the mainboard, infer the seed tags
     * (commander + top identity-tag cards), evaluate Health + cuts, then run the ADD pipeline.
     * Primes [analysisCache] for subsequent incremental recompute and sets [DeckDoctorState.isLoaded].
     */
    fun loadAnalysis(deckId: String, constraints: BudgetConstraints) {
        analysisCache = null
        analysisJob?.cancel()
        // Workstream 8.4 -- this pass's own identity, captured BEFORE launch so every stage-emitting
        // sub-job it spawns (recomputeAddsInternal/recomputeCommunityInternal) can tell whether it is
        // still the CURRENT pass by the time it actually gets to update [DeckDoctorState.stage].
        val myGeneration = ++stagingGeneration
        analysisJob = scope.launch {
            crashReporter.log("deck_studio_suggestions_analysis_started")
            _state.update {
                it.copy(
                    isSuggestionsLoading = true,
                    isLoaded = true,
                    stage = DoctorAnalysisStage.READING_DECK_PLAN,
                    completedStages = emptyList(),
                )
            }

            val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            if (deckWithCards == null) {
                crashReporter.log("deck_studio_suggestions_analysis_aborted")
                _state.update { it.copy(isSuggestionsLoading = false) }
                return@launch
            }

            val collection = userCardRepository.observeCollection().first()
            val format = DeckFormat.entries
                .firstOrNull { it.name.equals(deckWithCards.deck.format, ignoreCase = true) }
                ?: DeckFormat.CASUAL

            var unresolvedCount = 0
            val mainboardEntries = deckWithCards.mainboard.mapNotNull { slot ->
                val card = resolveCard(slot.scryfallId)
                if (card == null) {
                    unresolvedCount += slot.quantity
                    null
                } else {
                    DeckEntry(card = card, quantity = slot.quantity, isOwned = false, isSideboard = false)
                }
            }

            val commanderId = deckWithCards.deck.commanderCardId
            val commanderCard = commanderId?.let { resolveCard(it) }
            val commanderIdentity = commanderCard?.colorIdentity?.toSet().orEmpty()
            val commanderTags = commanderCard?.let { it.tags + it.userTags }.orEmpty()

            val archetypeOverride = deckWithCards.deck.archetypeOverride
            val themesOverride = deckWithCards.deck.themesOverride
            val tribeOverride = deckWithCards.deck.tribeOverride
            val strategyLocked = deckWithCards.deck.strategyLocked
            // Deck Engine Unification (D4): every mainboard slot the wizard placed persists
            // source == WIZARD (DeckWizardViewModel.writeResultIntoNewDeck) -- while the deck is
            // strategyLocked, NONE of them may ever appear in `cuts` (a hard guarantee, not a
            // ranking nudge, since rankCuts fully filters `protectedIds` out of its candidate pool).
            val wizardSourcedIds = deckWithCards.mainboard
                .filter { it.source == DeckCardSource.WIZARD }
                .mapTo(mutableSetOf()) { it.scryfallId }

            val seedCards = inferenceSeeds(commanderCard, mainboardEntries)
            val inferredSeedTags = inferDeckIdentityUseCase(seedCards).seedTags
            val seedTags = (inferredSeedTags + pinSeedTags(archetypeOverride, themesOverride, tribeOverride)).distinct()
            val weightOverrides = weightsProvider()
            val weights = weightOverrides.toScoreWeights()
            // Wave 2 / B3: P5's SideboardOversized check needs the sideboard count; the mainboard
            // resolution above never touches deckWithCards.sideboard.
            val sideboardCount = deckWithCards.sideboard.sumOf { it.quantity }

            val health = evaluateDeckUseCase(
                mainboard = mainboardEntries,
                format = format,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                weights = weights,
                archetypeOverride = archetypeOverride,
                themesOverride = themesOverride,
                commanderTags = commanderTags,
                // Deck Analysis Engine v2 Phase 2 -- same DataStore-backed debug-tuning mechanism,
                // extended (not duplicated) to also carry the 5 pillar weights.
                analysisWeights = weightOverrides.toAnalysisWeights(),
                sideboardCount = sideboardCount,
            )
            // Deck Analysis Engine v2 Phase 0 carve-out: skip Cuts entirely (no candidate pool scan)
            // while the flag is off -- see [isSuggestionsEngineEnabled]'s KDoc.
            val cuts = if (isSuggestionsEngineEnabled()) {
                suggestCutsUseCase(
                    mainboard = mainboardEntries,
                    profile = health.profile,
                    protectedIds = cutProtectedIds(commanderId, strategyLocked, wizardSourcedIds),
                    weights = weights,
                    resolvedSkeleton = resolveArchetypeSkeleton(health),
                )
            } else {
                emptyList()
            }

            val collectionCards = collection.map { it.card }
            val wishlistIds = wishlistRepository.observeLocal().first().map { it.cardId }.toSet()

            // NOTE: MutableMap.putIfAbsent is a JVM-only extension (kotlin.collections stdlib
            // gap on wasmJs) — use the portable `if (key !in map)` guard instead everywhere in
            // this class.
            val resolvedById = HashMap<String, Card>()
            mainboardEntries.forEach { resolvedById[it.card.scryfallId] = it.card }
            collectionCards.forEach { if (it.scryfallId !in resolvedById) resolvedById[it.scryfallId] = it }
            commanderCard?.let { if (it.scryfallId !in resolvedById) resolvedById[it.scryfallId] = it }

            analysisCache = AnalysisCache(
                workingMainboard = mainboardEntries,
                format = format,
                commanderId = commanderId,
                commanderIdentity = commanderIdentity,
                commanderName = commanderCard?.name,
                seedTags = seedTags,
                collection = collectionCards,
                wishlistIds = wishlistIds,
                resolvedById = resolvedById,
                unresolvedCount = unresolvedCount,
                sideboardCount = sideboardCount,
                archetypeOverride = archetypeOverride,
                themesOverride = themesOverride,
                commanderTags = commanderTags,
                strategyLocked = strategyLocked,
                wizardSourcedIds = wizardSourcedIds,
            )

            if (unresolvedCount > 0) {
                crashReporter.setCustomKey(
                    "deck_studio_card_count",
                    (mainboardEntries.sumOf { it.quantity } + unresolvedCount).toString(),
                )
            }
            crashReporter.log("deck_studio_suggestions_analysis_succeeded")
            // Deck Analysis Engine v2 Phase 4 (telemetry): score/quality signal for a FULL analysis
            // pass only -- never fired from [recomputeIncremental] (every card add/cut would be far
            // too frequent for a breadcrumb). `health.analysis` can be null here if the v2 engine
            // itself failed (see [EvaluateDeckUseCase]'s own runCatching guard) -- simply skip the
            // event rather than logging a synthetic/placeholder score.
            health.analysis?.let { analysis ->
                val weakestPillar = analysis.pillars.minByOrNull { it.subscore }
                crashReporter.setCustomKey("deck_analysis_score_bucket", scoreBucket(analysis.totalScore))
                crashReporter.setCustomKey("deck_analysis_pillar_min_id", weakestPillar?.id?.name ?: "none")
                crashReporter.setCustomKey("deck_analysis_format", format.name)
                crashReporter.setCustomKey("deck_analysis_strategy_id", analysis.strategy.curatedStrategyId ?: "custom")
                crashReporter.log("deck_analysis_completed")
            }

            _state.update {
                it.copy(
                    health = withUnresolvedWarning(health, unresolvedCount),
                    cuts = cuts,
                    isSuggestionsLoading = false,
                    strategyLocked = strategyLocked,
                )
            }
            // Deck Analysis Engine v2 Phase 0 carve-out: neither Motor A (recomputeAddsInternal,
            // which owns the Scryfall/CandidatePoolGenerator backstop) nor Motor B
            // (recomputeCommunityInternal, community aggregate + similar-decks) ever launches while
            // the flag is off -- zero network/Worker calls from a hidden section.
            // isSuggestionsLoading is already false in the `_state.update` above regardless, so the
            // staged-progress UI never hangs waiting for these.
            if (isSuggestionsEngineEnabled()) {
                recomputeAddsInternal(constraints, emitStages = true, ownerGeneration = myGeneration)
                // Motor B (Phase 4): fetched once per full analysis, not on every incremental add/cut
                // (see [recomputeCommunityInternal]'s KDoc for why) — [onAddCard]/[onCutCard] instead
                // locally filter the already-fetched lists.
                recomputeCommunityInternal(ownerGeneration = myGeneration)
            }
        }
    }

    /**
     * Public entry for a budget-change-triggered recompute. Motor A ignores [constraints] entirely
     * (every candidate is already owned — see the class KDoc's "Phase 2" section) but the method is
     * kept so the host's budget-editing handlers (`onPerCardBudgetChange`/`onTotalBudgetChange`/
     * `onClearBudget`) need no change, per D5.
     */
    fun recomputeAdds(constraints: BudgetConstraints) {
        recomputeAddsInternal(constraints)
    }

    /**
     * Recomputes the ADD suggestions from [analysisCache] via Motor A
     * ([SuggestAddsFromCollectionUseCase]). [constraints] is accepted (see [recomputeAdds]'s KDoc)
     * but not forwarded — Motor A has no budget dimension.
     *
     * @param emitStages Workstream 8.4 -- true ONLY when called from [loadAnalysis] (the full
     *   analysis pass); a budget-change ([recomputeAdds]) or incremental ([recomputeIncremental])
     *   caller leaves this `false` so [DeckDoctorState.stage] is never touched outside a full pass.
     * @param ownerGeneration the [stagingGeneration] value active when the ENCLOSING [loadAnalysis]
     *   pass launched this recompute (irrelevant when [emitStages] is false). Re-checked against the
     *   CURRENT [stagingGeneration] AT EACH stage-transition call site below (edge-case audit Fix 4,
     *   2026-07-28 — freshly recomputed every time, never cached in a single local `val` and reused)
     *   so a superseded pass's job can never emit a stale stage — see [stagingGeneration]'s KDoc.
     *   Pre-fix, this freshness check was computed ONCE at the top of the function and the resulting
     *   boolean was reused at 2 later stage-advance call sites (`SEARCHING_CARD_POOL`/
     *   `RANKING_SUGGESTIONS`) — if a NEWER pass (higher generation) started and progressed its OWN
     *   stage between this job's launch and one of those later call sites, this job's stale-true
     *   flag would still fire, overwriting whatever genuinely-current stage the newer pass was
     *   displaying. The two `_state.update` blocks below (the failure path and the terminal
     *   success path) already recomputed freshness inline correctly — only the 2 intermediate
     *   `advanceDoctorStage` calls were affected.
     */
    private fun recomputeAddsInternal(
        @Suppress("UNUSED_PARAMETER") constraints: BudgetConstraints,
        emitStages: Boolean = false,
        ownerGeneration: Int = stagingGeneration,
    ) {
        val context = analysisCache ?: return
        val health = _state.value.health ?: return
        // Cancel any in-flight recompute so two racing computations can't both emit a stale ADD
        // list (H3).
        recomputeAddsJob?.cancel()
        recomputeAddsJob = scope.launch {
            _state.update { it.copy(isAddsLoading = true) }
            if (emitStages && ownerGeneration == stagingGeneration) advanceDoctorStage(DoctorAnalysisStage.EVALUATING_COLLECTION)
            val weights = weightsProvider().toScoreWeights()
            val resolvedSkeleton = resolveArchetypeSkeleton(health)

            val result = runCatching {
                suggestAddsFromCollectionUseCase(
                    collection = context.collection,
                    mainboard = context.workingMainboard,
                    profile = health.profile,
                    resolvedSkeleton = resolvedSkeleton,
                    weights = weights,
                )
            }
            val suggestions = result.getOrNull()

            if (suggestions == null) {
                val error = result.exceptionOrNull()
                crashReporter.log("deck_studio_external_pool_failed")
                crashReporter.setCustomKey("deck_studio_format", context.format.name)
                crashReporter.setCustomKey("deck_studio_card_count", context.workingMainboard.sumOf { it.quantity }.toString())
                if (error != null) {
                    crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_external_pool_failed", error))
                }
                // Clear the staged screen on this defensive failure path too -- never leave the
                // Suggestions tab stuck on a stage forever because Motor A (a pure in-memory
                // computation that should never throw) unexpectedly threw.
                _state.update { s ->
                    val stillCurrent = emitStages && ownerGeneration == stagingGeneration
                    s.copy(
                        isAddsLoading = false,
                        completedStages = if (stillCurrent && s.stage != null) s.completedStages + s.stage else s.completedStages,
                        stage = if (stillCurrent) null else s.stage,
                    )
                }
                _events.send(DeckDoctorEvent.ExternalPoolFailed)
                return@launch
            }

            // Fix 4 (edge-case audit, 2026-07-28): freshly re-checked HERE, not read from a val
            // cached at the top of the function -- see this function's own KDoc for the exact race.
            if (emitStages && ownerGeneration == stagingGeneration) advanceDoctorStage(DoctorAnalysisStage.SEARCHING_CARD_POOL)
            // Workstream 8.2: the Scryfall backstop as a THIRD adds source, merged in ONLY when the
            // user's own per-session toggle is on -- a silent no-op (empty, never-failed) otherwise,
            // so `adds` stays byte-identical to Motor-A-only pre-WS8.2 whenever the toggle is off.
            val (backstopSuggestions, backstopFailed) = fetchOutsideCollectionSuggestions(
                context = context,
                health = health,
                resolvedSkeleton = resolvedSkeleton,
                weights = weights,
            )
            // Fix 4 (edge-case audit, 2026-07-28): same re-check, freshly evaluated again -- the
            // Scryfall backstop fetch above is another suspend point where a newer pass could have
            // advanced past this job's stale understanding of "still current".
            if (emitStages && ownerGeneration == stagingGeneration) advanceDoctorStage(DoctorAnalysisStage.RANKING_SUGGESTIONS)
            val merged = (suggestions + backstopSuggestions)
                .distinctBy { it.fit.card.scryfallId }
                .sortedWith(
                    compareByDescending<AddSuggestion> { it.fit.score }
                        .thenBy { it.fit.card.name }
                        .thenBy { it.fit.card.scryfallId }
                )
                .take(ADDS_DISPLAY_LIMIT)

            merged.forEach {
                val id = it.fit.card.scryfallId
                if (id !in context.resolvedById) context.resolvedById[id] = it.fit.card
            }

            _state.update {
                val stillCurrent = emitStages && ownerGeneration == stagingGeneration
                it.copy(
                    adds = merged,
                    addsTotalCostEur = 0.0,
                    addsCardsToBuy = 0,
                    isAddsLoading = false,
                    includeOutsideCollection = context.includeOutsideCollection,
                    outsideCollectionUnavailable = backstopFailed,
                    // Workstream 8.4 -- this is the terminal stage: fold whatever stage is still
                    // showing into the checklist, then clear it so the Suggestions tab reveals its
                    // real content. Gated on [stillCurrent] so a superseded pass's own job can never
                    // clear a NEWER pass's in-progress stage out from under it.
                    completedStages = if (stillCurrent && it.stage != null) it.completedStages + it.stage else it.completedStages,
                    stage = if (stillCurrent) null else it.stage,
                )
            }
        }
    }

    /**
     * Workstream 8.2 -- fetches the Scryfall backstop pool ([CandidatePoolGenerator], the SAME
     * class the wizard's own build-time last-resort fill reuses, per
     * `BuildDeckFromTemplateUseCase.runScryfallBackstopLoop`'s exact shape: recompute profile ->
     * ask the generator -> rescore through the SAME [suggestAddsFromCollectionUseCase] Motor A
     * call) and tags every result [AddOrigin.NEW] (genuinely not owned). Returns an empty,
     * non-failed result when [candidatePoolGenerator] is unset or the toggle is off (silent no-op,
     * mirrors [recomputeCommunityInternal]'s "null dependency / flag off" contract) -- ANY OTHER
     * failure (network, parsing) is caught and reported as `failed = true` so the caller can show a
     * per-source degrade notice ([DeckDoctorState.outsideCollectionUnavailable]) rather than
     * silently returning a thinner merged list that looks like "nothing more to suggest".
     */
    private suspend fun fetchOutsideCollectionSuggestions(
        context: AnalysisCache,
        health: DeckHealth,
        resolvedSkeleton: ResolvedArchetypeSkeleton?,
        weights: ScoreWeights,
    ): Pair<List<AddSuggestion>, Boolean> {
        val generator = candidatePoolGenerator ?: return emptyList<AddSuggestion>() to false
        if (!context.includeOutsideCollection) return emptyList<AddSuggestion>() to false

        return runCatching {
            val pool = generator(profile = health.profile, evaluation = health.evaluation)
            if (pool.isEmpty()) {
                emptyList()
            } else {
                suggestAddsFromCollectionUseCase(
                    collection = pool,
                    mainboard = context.workingMainboard,
                    profile = health.profile,
                    resolvedSkeleton = resolvedSkeleton,
                    weights = weights,
                ).map { it.copy(origin = AddOrigin.NEW) }
            }
        }.fold(
            onSuccess = { it to false },
            onFailure = { t ->
                crashReporter.log("deck_studio_scryfall_backstop_suggestions_failed")
                crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_scryfall_backstop_suggestions_failed", t))
                emptyList<AddSuggestion>() to true
            },
        )
    }

    /**
     * Workstream 8.2 -- toggles the Scryfall backstop (3rd adds source) for the CURRENT session
     * only (mirrors the wizard's own `includeOutsideCollection`, but this is a SEPARATE choice --
     * the Suggestions tab does not read or write the wizard's spec). A no-op when no analysis is
     * primed yet, or the value is unchanged (avoids a redundant recompute on a no-op toggle tap).
     */
    fun setIncludeOutsideCollection(enabled: Boolean, constraints: BudgetConstraints) {
        val context = analysisCache ?: return
        if (context.includeOutsideCollection == enabled) return
        context.includeOutsideCollection = enabled
        recomputeAddsInternal(constraints)
    }

    /**
     * Motor B (Deck Doctor Community/Archetype plan, Phase 4): fetches the community aggregate
     * (Commander via EDHREC, 60-card via Archidekt — [CommunityAggregateRepository]) and the
     * "decks like yours" carousel, then ranks them via [suggestAddsFromCommunityUseCase] /
     * [findSimilarDecksUseCase]. Entirely OPT-IN and defensive:
     *  - A `null` Motor B dependency (the legacy no-arg constructor default) is a silent no-op —
     *    every field stays at its `DeckDoctorState` default.
     *  - [isCommunityEngineEnabled] false (D4's `communityEngineEnabledFlow` off) is likewise a
     *    silent no-op, no network/cache access at all (mirrors [CommunityAggregateRepositoryImpl]'s
     *    own flag short-circuit — belt-and-braces).
     *  - ANY failure (network, parsing, an unexpected exception in either use case) is caught here
     *    and surfaces ONLY as [DeckDoctorState.communityUnavailable] = true — it NEVER touches
     *    [DeckDoctorState.health]/`cuts`/`adds` (Motor A) and never emits [DeckDoctorEvent
     *    .ExternalPoolFailed] (that event stays Motor-A-only; Motor B has its own dedicated,
     *    non-blocking degradation flag instead of a toast, since the Studio renders an inline
     *    per-section error state — see the plan's "Worker down -> Motor A untouched" requirement).
     *
     * @param ownerGeneration Workstream 8.4 -- the [stagingGeneration] value active when the
     *   ENCLOSING [loadAnalysis] pass launched this fetch (this method has only ever had one
     *   caller, [loadAnalysis], so there is no unstaged variant to preserve). Re-checked against
     *   the CURRENT [stagingGeneration] before setting [DoctorAnalysisStage.SEARCHING_COMMUNITY] so
     *   a superseded pass's community job can never emit a stale stage — see [stagingGeneration]'s
     *   KDoc.
     */
    private fun recomputeCommunityInternal(ownerGeneration: Int) {
        val context = analysisCache ?: return
        val health = _state.value.health ?: return
        val aggregateRepository = communityAggregateRepository
        val addsUseCase = suggestAddsFromCommunityUseCase
        val similarUseCase = findSimilarDecksUseCase
        if (aggregateRepository == null || addsUseCase == null || similarUseCase == null) return

        communityJob?.cancel()
        communityJob = scope.launch {
            if (!isCommunityEngineEnabled()) {
                _state.update {
                    it.copy(communityAdds = emptyList(), similarDecks = emptyList(), isCommunityLoading = false, communityUnavailable = false)
                }
                return@launch
            }
            if (ownerGeneration == stagingGeneration) advanceDoctorStage(DoctorAnalysisStage.SEARCHING_COMMUNITY)
            _state.update { it.copy(isCommunityLoading = true, communityUnavailable = false) }

            val archetypeFormat = ArchetypeFormat.of(context.format)
            val aggregateResult: DataResult<CommunityAggregate>? = try {
                val commanderName = context.commanderName
                if (context.format == DeckFormat.COMMANDER && commanderName != null) {
                    aggregateRepository.getCommanderAggregate(commanderName)
                } else if (archetypeFormat != null) {
                    val signature = signatureCards(context.workingMainboard)
                    if (signature.isEmpty()) null
                    else aggregateRepository.getSixtyAggregate(signature, SIXTY_ARCHIDEKT_FORMAT_ID)
                } else {
                    null
                }
            } catch (t: Throwable) {
                crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_community_aggregate_failed", t))
                null
            }

            val cards: List<AggregateCardEntry> = when (val agg = aggregateResult) {
                is DataResult.Success -> when (val data = agg.data) {
                    is CommunityAggregate.Commander -> data.cards
                    is CommunityAggregate.Sixty.Materialized -> data.cards
                    is CommunityAggregate.Sixty.Building -> emptyList()
                    else -> emptyList()
                }
                else -> emptyList()
            }

            val resolvedSkeleton = resolveArchetypeSkeleton(health)
            val communityAdds = if (cards.isEmpty()) emptyList() else runCatching {
                addsUseCase(
                    aggregateCards = cards,
                    mainboard = context.workingMainboard,
                    profile = health.profile,
                    collection = context.collection,
                    resolvedSkeleton = resolvedSkeleton,
                )
            }.getOrDefault(emptyList())

            // Register resolved community cards in the shared resolved-by-id map so a subsequent
            // `onAddSuggestion` on a Motor B card hits [findCachedCard] and stays incremental
            // instead of falling back to a full [loadAnalysis].
            communityAdds.forEach { s -> if (s.card.scryfallId !in context.resolvedById) context.resolvedById[s.card.scryfallId] = s.card }

            val similarSeed = context.commanderName ?: signatureCards(context.workingMainboard).firstOrNull()
            val similarResult = similarSeed?.let { seed ->
                runCatching {
                    similarUseCase(
                        seedQuery = seed,
                        deckFormat = if (context.format == DeckFormat.COMMANDER) ArchidektFormat.COMMANDER.apiId else SIXTY_ARCHIDEKT_FORMAT_ID,
                        userColorIdentity = health.profile.colorIdentity.map { it.symbol }.toSet(),
                    )
                }.getOrNull()
            }
            val similarDecks = (similarResult as? DataResult.Success)?.data.orEmpty()

            // "Unavailable" only when EVERY Motor B signal came back empty AND the aggregate fetch
            // itself failed/errored — a legitimately empty-but-successful aggregate (a very obscure
            // commander with zero EDHREC data) is NOT an error state, just an empty section.
            val aggregateFailed = aggregateResult == null || aggregateResult is DataResult.Error
            val unavailable = aggregateFailed && communityAdds.isEmpty() && similarDecks.isEmpty()

            _state.update {
                it.copy(
                    communityAdds = communityAdds,
                    similarDecks = similarDecks,
                    isCommunityLoading = false,
                    communityUnavailable = unavailable,
                )
            }
        }
    }

    /**
     * Picks 2-3 signature cards for the 60-card canonical aggregate key (Phase 3.2 /
     * [com.mmg.manahub.core.data.remote.CommunityAggregateKeys]): the LEAST globally popular
     * non-land mainboard cards (highest [Card.edhrecRank] number — EDHREC ranks 1 = most played),
     * so the key stays distinctive rather than collapsing onto generic staples every deck plays.
     */
    private fun signatureCards(mainboard: List<DeckEntry>): List<String> =
        mainboard
            .asSequence()
            .map { it.card }
            .filterNot { com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isLand(it) }
            .filter { it.edhrecRank != null }
            .sortedByDescending { it.edhrecRank }
            .map { it.name }
            .distinct()
            .take(SIGNATURE_CARD_COUNT)
            .toList()
            .sorted()

    /**
     * Re-evaluates the deck IN MEMORY from [AnalysisCache.workingMainboard] after a single-card
     * suggestion add/cut: rebuild profile/evaluation/cuts (pure), then recompute ADD suggestions.
     *
     * [AnalysisCache.seedTags] is reused UNCHANGED (never re-derived here) -- it was already computed
     * once in [loadAnalysis] as `inference + `[pinSeedTags]` (Wave 4, Task 1), so every incremental
     * add/cut in this session ranks against the EXACT SAME basis the full analysis started from. An
     * archetype/theme override change goes through [setArchetypeOverride], which re-runs a FULL
     * [loadAnalysis] rather than an incremental step (see its own KDoc) -- so there is no path where
     * this method's basis can drift from the pin.
     */
    private fun recomputeIncremental(constraints: BudgetConstraints) {
        val context = analysisCache ?: return
        scope.launch {
            val mainboard = context.workingMainboard
            val weightOverrides = weightsProvider()
            val weights = weightOverrides.toScoreWeights()
            val health = evaluateDeckUseCase(
                mainboard = mainboard,
                format = context.format,
                commanderIdentity = context.commanderIdentity,
                seedTags = context.seedTags,
                weights = weights,
                archetypeOverride = context.archetypeOverride,
                themesOverride = context.themesOverride,
                commanderTags = context.commanderTags,
                analysisWeights = weightOverrides.toAnalysisWeights(),
                sideboardCount = context.sideboardCount,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboard,
                profile = health.profile,
                protectedIds = cutProtectedIds(context.commanderId, context.strategyLocked, context.wizardSourcedIds),
                weights = weights,
                resolvedSkeleton = resolveArchetypeSkeleton(health),
            )
            _state.update {
                it.copy(
                    health = withUnresolvedWarning(health, context.unresolvedCount),
                    cuts = cuts,
                )
            }
            recomputeAddsInternal(constraints)
        }
    }

    /**
     * Adds one copy of [scryfallId] to the in-memory working mainboard and recomputes
     * incrementally. Returns `false` when there is no primed [analysisCache] or the card cannot
     * be resolved from any cached source — the caller must fall back to a full [loadAnalysis] in
     * that case (mirrors the pre-extraction `DeckStudioViewModel.onAddSuggestion` fallback exactly).
     */
    fun onAddCard(scryfallId: String, constraints: BudgetConstraints): Boolean {
        val context = analysisCache ?: return false
        val added = findCachedCard(context, scryfallId) ?: return false
        val existing = context.workingMainboard.firstOrNull { it.card.scryfallId == scryfallId }
        context.workingMainboard = if (existing != null) {
            context.workingMainboard.map {
                if (it.card.scryfallId == scryfallId) it.copy(quantity = it.quantity + 1) else it
            }
        } else {
            context.workingMainboard + DeckEntry(card = added, quantity = 1, isOwned = false, isSideboard = false)
        }
        // Motor B is NOT re-fetched on every increment (see [recomputeCommunityInternal]'s KDoc) —
        // just locally drop the just-added card so a suggestion already acted on disappears from
        // the "Popular in similar decks" list immediately.
        _state.update { it.copy(communityAdds = it.communityAdds.filterNot { s -> s.card.scryfallId == scryfallId }) }
        recomputeIncremental(constraints)
        return true
    }

    /**
     * Removes ONE copy of [scryfallId] from the in-memory working mainboard (C1 — decrements a
     * multi-copy slot rather than dropping the whole slot) and recomputes incrementally. Returns
     * `false` when there is no primed [analysisCache] — the caller must fall back to a full
     * [loadAnalysis] in that case.
     */
    fun onCutCard(scryfallId: String, constraints: BudgetConstraints): Boolean {
        val context = analysisCache ?: return false
        context.workingMainboard = context.workingMainboard.mapNotNull { entry ->
            if (entry.card.scryfallId != scryfallId) entry
            else if (entry.quantity <= 1) null
            else entry.copy(quantity = entry.quantity - 1)
        }
        recomputeIncremental(constraints)
        return true
    }

    /**
     * The working-mainboard quantity for [scryfallId] if [analysisCache] is primed, else `null`
     * (the host falls back to its own live-deck quantity source). Lets the host compute the
     * correct repository write (decrement vs. delete) BEFORE calling [onCutCard].
     */
    fun cachedMainboardQuantity(scryfallId: String): Int? =
        analysisCache?.workingMainboard?.firstOrNull { it.card.scryfallId == scryfallId }?.quantity

    /** Looks up a resolved [Card] in the cached add list / resolved-by-id map (no repository call). */
    private fun findCachedCard(context: AnalysisCache, scryfallId: String): Card? =
        _state.value.adds.firstOrNull { it.fit.card.scryfallId == scryfallId }?.fit?.card
            ?: context.resolvedById[scryfallId]

    /**
     * Invalidates the loaded analysis after a MANUAL (Build-tab) deck mutation so the next time
     * the host opens Suggestions a fresh [loadAnalysis] re-syncs with the live deck. Deliberately
     * does NOT recompute here (the work is wasted while the user is still editing) and must NEVER
     * be called from a deck-observe transformer (that would create a write→observe→recompute
     * feedback loop — the host is responsible for calling this only from explicit manual
     * mutations). [DeckDoctorState.health]/`cuts`/`adds` are left untouched (stale but hidden once
     * [DeckDoctorState.isLoaded] flips to `false`) — mirrors the pre-extraction behavior exactly.
     */
    fun invalidate() {
        if (_state.value.isLoaded) {
            analysisJob?.cancel()
            recomputeAddsJob?.cancel()
            communityJob?.cancel()
            analysisCache = null
            _state.update { it.copy(isLoaded = false, stage = null, completedStages = emptyList()) }
        }
    }

    /**
     * Workstream 8.4 -- advances [DeckDoctorState.stage] forward (by [DoctorAnalysisStage] ordinal,
     * never backward) and appends the stage it is replacing to [DeckDoctorState.completedStages].
     * A no-op when [DeckDoctorState.stage] is already `null` — either the pass hasn't set its own
     * initial stage yet (never happens in practice: [loadAnalysis] sets [DoctorAnalysisStage
     * .READING_DECK_PLAN] directly, synchronously, before spawning anything that calls this), or the
     * pass already finished ([recomputeAddsInternal]'s terminal update cleared it) — in the latter
     * case this guard is what stops a late [recomputeCommunityInternal] update from resurrecting the
     * staged screen after Motor A + the backstop already revealed the real Suggestions content.
     */
    private fun advanceDoctorStage(next: DoctorAnalysisStage) {
        _state.update { s ->
            val current = s.stage ?: return@update s
            if (next.ordinal <= current.ordinal) return@update s
            s.copy(completedStages = s.completedStages + current, stage = next)
        }
    }

    /**
     * Picks the inference seed cards: the commander (when present) plus the deck's highest-weight
     * identity cards (most STRATEGY / ARCHETYPE / TRIBAL tags), capped so one off-theme card can't
     * skew the seed.
     */
    private fun inferenceSeeds(commander: Card?, mainboard: List<DeckEntry>): List<Card> {
        val ranked = mainboard
            .map { it.card }
            .filter { it.scryfallId != commander?.scryfallId }
            .map { card -> card to identityTagCount(card) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEED_CARDS)
            .map { it.first }
        return (listOfNotNull(commander) + ranked).distinctBy { it.scryfallId }
    }

    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    /**
     * Wizard Quality Campaign Wave 4 (Task 1): folds a deck's PERSISTED `archetypeOverride`/
     * `themesOverride` pin into the seed-tag basis via the SAME [DeckIdentitySeedTags] table
     * [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase.recomputeProfile]
     * uses at build time -- without this, a deck built with an explicit Direction hint (e.g. the
     * wizard's GRAVEYARD strategy) scored its own placed cards against a richer basis DURING the
     * build than the Doctor scores them against AFTERWARD (inference-only), so a card that legitimately
     * cleared the wizard's category-fill floor could fall below the Doctor's cut floor purely from
     * the missing explicit signal -- see `project_wizard_quality_campaign_wave3` memory's bucket-(ii)
     * root cause and [DeckIdentitySeedTags]'s class KDoc.
     *
     * [archetypeOverride]/[themesOverride] are persisted as raw enum-name STRINGS
     * ([com.mmg.manahub.core.model.Deck.archetypeOverride]/`themesOverride`) -- mapped back
     * defensively via `entries.firstOrNull`; an unknown/stale name (e.g. a renamed enum entry)
     * resolves to "no pin contribution" for that piece, never a guess or a crash. A deck with no
     * override (both null/empty -- the common case) contributes an empty list here, so
     * [loadAnalysis]'s `seedTags` is BYTE-IDENTICAL to before this change for every unpinned/GENERIC
     * deck.
     *
     * A stale/unresolvable override string is now ALSO reported as a non-fatal (never silently
     * swallowed): the persisted string was written by this same app and should always resolve, so a
     * miss means enum drift (a renamed/removed [ArchetypeId]/[ThemeId] entry without a data
     * migration) -- an actionable bug, not an expected runtime state, and exactly the class of
     * silent-degradation this campaign exists to catch early.
     */
    private fun pinSeedTags(archetypeOverride: String?, themesOverride: List<String>, tribeOverride: String? = null): List<CardTag> {
        val archetype = archetypeOverride?.let { name -> ArchetypeId.entries.firstOrNull { it.name == name } }
        val themes = themesOverride.mapNotNull { name -> ThemeId.entries.firstOrNull { it.name == name } }
        val archetypeStale = archetypeOverride != null && archetype == null
        val themesLostCount = themesOverride.size - themes.size
        if (archetypeStale || themesLostCount > 0) {
            crashReporter.log("deck_doctor_pin_seed_tags_unresolved")
            crashReporter.setCustomKey("deck_doctor_pin_archetype_stale", archetypeStale.toString())
            crashReporter.setCustomKey("deck_doctor_pin_themes_lost_count", themesLostCount.toString())
            crashReporter.recordException(
                RuntimeException(
                    "[DeckDoctorOrchestrator] deck_doctor_pin_seed_tags_unresolved: " +
                        "archetypeOverride=$archetypeOverride themesOverride=$themesOverride"
                )
            )
        }
        // Deck Engine Unification (D2): tribeOverride is a SEPARATE pin column (never folded into
        // themesOverride's JSON list -- see Deck.tribeOverride's KDoc), so it never participates in
        // the stale-pin detection above (a blank/absent tribe is simply "no tribe pin", not a data
        // hazard the way an unresolvable ArchetypeId/ThemeId name is).
        if (archetype == null && themes.isEmpty() && tribeOverride.isNullOrBlank()) return emptyList()
        return DeckIdentitySeedTags.forArchetype(archetype ?: ArchetypeId.GENERIC, themes, tribeOverride)
    }

    /**
     * Resolves the archetype/theme skeleton Motor A's theme-role bonus scores against, mirroring
     * [EvaluateDeckUseCase]'s own resolution EXACTLY (same inputs: format, resolved macro/themes,
     * color count) — a cheap, pure, side-effect-free re-derivation (no card iteration), never a
     * second source of truth. Returns `null` for the GENERIC-with-no-themes / no-archetype-skeleton
     * (Draft) cases, exactly like [EvaluateDeckUseCase]'s own byte-stability branch — Motor A then
     * scores purely off [com.mmg.manahub.feature.decks.domain.engine.DeckScorer.rankAdds] plus the
     * pip multiplier, with zero theme bonus.
     */
    private fun resolveArchetypeSkeleton(health: DeckHealth): ResolvedArchetypeSkeleton? {
        val archetypeFormat = ArchetypeFormat.of(health.profile.format) ?: return null
        val resolution = health.archetypeResolution
        if (resolution.macro == ArchetypeId.GENERIC && resolution.themes.isEmpty()) return null
        return ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = resolution.macro,
            themes = resolution.themes,
            identity = health.profile.colorIdentity,
        )
    }

    // ── Archetype override (Phase 1.7 Studio UI entry point) ───────────────────────

    /**
     * Pins (or, when [archetypeId]/[themes] are both null/empty, clears) the deck's archetype/theme
     * override — writes through [DeckRepository.updateArchetypeOverride] then re-runs a FULL
     * [loadAnalysis] (a macro/theme change reshapes the whole resolved skeleton, so an incremental
     * recompute is not enough — mirrors [changeFormat]'s "cheap enough to just reload" precedent).
     *
     * @param archetypeId `null` clears the macro pin (back to inference); a non-null value pins it.
     * @param themes at most 2 (the caller — the Studio bottom sheet — already enforces this cap);
     *        empty clears the theme pin.
     * @param tribe Deck Analysis Engine v2 Phase 3 -- the curated strategy picker's tribe sub-pick
     *        (only meaningful when a [ThemeId.TRIBAL]-requiring strategy is applied), written through
     *        the SEPARATE [DeckRepository.updateTribeOverride] column (mirrors the wizard's own
     *        `updateArchetypeOverride` + `updateTribeOverride` pair, see [DeckWizardViewModel]).
     *        Defaults to `null` so every pre-Phase-3 call site (the legacy `ArchetypePlanSheet`,
     *        which has no tribe UI, and existing tests) keeps clearing/leaving the tribe pin exactly
     *        as before -- `null` here always clears the tribe column, which is also the CORRECT
     *        behavior for a non-tribal strategy pick or "Auto-detect" ([clearArchetypeOverride]).
     */
    fun setArchetypeOverride(
        deckId: String,
        constraints: BudgetConstraints,
        archetypeId: ArchetypeId?,
        themes: List<ThemeId>,
        tribe: String? = null,
    ) {
        scope.launch {
            runCatching {
                deckRepository.updateArchetypeOverride(
                    deckId = deckId,
                    archetypeOverride = archetypeId?.name,
                    themesOverride = themes.take(2).map { it.name },
                )
                deckRepository.updateTribeOverride(deckId, tribe)
            }.onFailure {
                crashReporter.log("deck_studio_archetype_override_failed")
                crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_archetype_override_failed", it))
                return@launch
            }
            loadAnalysis(deckId, constraints)
        }
    }

    /** "Auto-detect" — clears both the macro and theme pin and re-infers from scratch. */
    fun clearArchetypeOverride(deckId: String, constraints: BudgetConstraints) {
        setArchetypeOverride(deckId, constraints, archetypeId = null, themes = emptyList())
    }

    /**
     * Deck Engine Unification plan (D4): the deck's own explicit "Unlock strategy" action —
     * flips `Deck.strategyLocked` off (releasing BOTH gates: [DeckDoctorState.cuts] may include a
     * WIZARD-sourced card again, and the host UI may show the "Deck plan" editor again) then re-runs
     * a full [loadAnalysis] (mirrors [setArchetypeOverride]'s "cheap enough to just reload"
     * precedent — the cut candidate pool itself changed, not just a display flag).
     */
    fun unlockStrategy(deckId: String, constraints: BudgetConstraints) {
        scope.launch {
            runCatching {
                deckRepository.updateStrategyLocked(deckId, false)
            }.onFailure {
                crashReporter.log("deck_studio_unlock_strategy_failed")
                crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_unlock_strategy_failed", it))
                return@launch
            }
            loadAnalysis(deckId, constraints)
        }
    }

    /**
     * Deck Engine Unification plan (D4): the commander is ALWAYS protected from cuts (pre-existing
     * behavior, unchanged); every [wizardSourcedIds] id joins it ONLY while [strategyLocked] — an
     * unlocked deck's wizard-placed cards become ordinary cut candidates again, same as any manual
     * addition. [suggestCutsUseCase]/[DeckScorer.rankCuts] fully EXCLUDES `protectedIds` from its
     * candidate pool (not a ranking nudge), so this is a hard, structural guarantee.
     */
    private fun cutProtectedIds(commanderId: String?, strategyLocked: Boolean, wizardSourcedIds: Set<String>): Set<String> =
        setOfNotNull(commanderId) + (if (strategyLocked) wizardSourcedIds else emptySet())

    /** Appends a [DeckWarning.UnresolvedCards] (legacy) / [Finding.UnresolvedCards] (Deck Analysis
     * Engine v2) when one or more mainboard slots failed to resolve — kept in sync across both
     * result shapes so neither the legacy health display nor the v2 pipeline silently hides a
     * partial evaluation. */
    private fun withUnresolvedWarning(health: DeckHealth, unresolvedCount: Int): DeckHealth {
        if (unresolvedCount <= 0) return health
        val withWarning = health.evaluation.copy(
            warnings = health.evaluation.warnings + DeckWarning.UnresolvedCards(unresolvedCount)
        )
        return health.copy(evaluation = withWarning, analysis = health.analysis?.withUnresolvedFinding(unresolvedCount))
    }

    /** Deck Analysis Engine v2 Phase 4 (telemetry): buckets a raw 0-100 [DeckAnalysis.totalScore]
     * into a coarse string for the `deck_analysis_completed` breadcrumb -- never the raw score
     * itself, per the project's telemetry granularity discipline. */
    private fun scoreBucket(totalScore: Int): String = when {
        totalScore < 40 -> "0-39"
        totalScore < 60 -> "40-59"
        totalScore < 80 -> "60-79"
        else -> "80-100"
    }

    private companion object {
        /** Identity tag categories used to rank inference seed cards (mirrors the scorer's set). */
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /** Cap on auto-selected identity seed cards (plus the commander) so one card can't skew the seed. */
        const val MAX_SEED_CARDS = 8

        /** Signature-card count for the 60-card canonical aggregate key (Phase 3.2 precedent: 2-3). */
        const val SIGNATURE_CARD_COUNT = 3

        /** Archidekt's "Custom" format id (`7`) — the best-effort proxy for ManaHub's generic
         * CASUAL 60-card format, which has no clean 1:1 Archidekt equivalent (see [ArchidektFormat]). */
        const val SIXTY_ARCHIDEKT_FORMAT_ID = 7

        /** Workstream 8.2 -- the merged (Motor A + Scryfall backstop) adds list is capped here,
         * matching [SuggestAddsFromCollectionUseCase]'s own default `limit` so merging in a second
         * source never balloons the Suggestions tab's displayed list size. */
        const val ADDS_DISPLAY_LIMIT = 50
    }
}
