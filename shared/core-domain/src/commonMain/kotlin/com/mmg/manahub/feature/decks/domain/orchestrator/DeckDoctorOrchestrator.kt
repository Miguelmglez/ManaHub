package com.mmg.manahub.feature.decks.domain.orchestrator

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.toAnalysisWeights
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.engine.withUnresolvedFinding
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SimilarDeckResult
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
 * Deck Analysis Category Sections rework (W0, D3/D5): the old Motor A ADD-ranking pipeline that
 * used to occupy the stretch between [READING_DECK_PLAN] and [SEARCHING_COMMUNITY] (collection
 * ranking + the Scryfall backstop + final merge/sort) was DELETED end-to-end along with the
 * Adds/Cuts suggestion engine -- [loadAnalysis] now clears [DeckDoctorState.stage] to `null`
 * unconditionally right after Health is computed, so in practice this staged screen is visible
 * only for the brief [READING_DECK_PLAN] window.
 *
 * NEVER set from [DeckDoctorOrchestrator.recomputeIncremental] -- that stays instant/unstaged by
 * design (see [DeckDoctorOrchestrator.loadAnalysis]'s KDoc); only the full analysis pass emits
 * [DeckDoctorState.stage] transitions.
 */
enum class DoctorAnalysisStage {
    /** Snapshotting the live deck + resolving the mainboard/commander/seed tags, before Health is
     * computed. */
    READING_DECK_PLAN,
    /**
     * Motor B ([com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase], "Decks
     * like yours") -- runs CONCURRENTLY with the stage around it ([recomputeCommunityInternal] is
     * fire-and-forget, never awaited by [loadAnalysis]), so this stage is best-effort/informational
     * only. Never resurrects the staged screen once [loadAnalysis]'s own terminal update has
     * already cleared [DeckDoctorState.stage] to `null` (see [advanceDoctorStage]'s no-op guard).
     */
    SEARCHING_COMMUNITY,
}

/**
 * Read-only Deck Doctor (Suggestions surface) state, mirrored into a host ViewModel's own UI
 * state by collecting [DeckDoctorOrchestrator.state].
 */
data class DeckDoctorState(
    /** Read-only Health evaluation from the scoring engine. Null until first computed. */
    val health: DeckHealth? = null,
    /** True while the full analysis (Health) is being computed. */
    val isSuggestionsLoading: Boolean = false,
    /** True once at least one full [DeckDoctorOrchestrator.loadAnalysis] has completed. */
    val isLoaded: Boolean = false,

    // ── Staged progress (Deck Wizard & Engine Rework plan, Workstream 8.4) ──────────────────────
    /** Non-null while the FULL [loadAnalysis] pass is progressing; null once that pass finishes,
     * or when no full analysis is in flight (an incremental add/cut never sets this). Drives the
     * Suggestions tab's staged progress screen -- see [DoctorAnalysisStage]. */
    val stage: DoctorAnalysisStage? = null,
    /** Stages already finished THIS pass, oldest first (the completed-stages checklist under the
     * current stage, mirroring [com.mmg.manahub.feature.decks.domain.template.BuildStage]'s UI).
     * Reset to empty at the start of every [loadAnalysis]. */
    val completedStages: List<DoctorAnalysisStage> = emptyList(),
    /**
     * Deck Engine Unification plan (D4): mirrors `Deck.strategyLocked`. The host UI hides the
     * "Deck plan" archetype/theme editor while true. Flip off via [unlockStrategy] (an explicit,
     * confirmed user action).
     */
    val strategyLocked: Boolean = false,

    // ── Motor B — "Decks like yours" (Deck Doctor Community/Archetype plan, Phase 4) ────────────
    // Deck Analysis Category Sections rework (W0, D3/D4/D5): the old Motor A/B "Adds"/"Cuts"
    // suggestion engine (budget, Scryfall backstop, "Popular in similar decks") was DELETED
    // end-to-end here. `similarDecks` SURVIVES -- independent state field, independent use case
    // ([com.mmg.manahub.feature.decks.domain.usecase.FindSimilarDecksUseCase]), still gated behind
    // `communityEngineEnabledFlow` (D4) exactly as before.
    /** "Decks like yours" carousel — real, importable Archidekt decks. */
    val similarDecks: List<SimilarDeckResult> = emptyList(),
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
 * ## Deck Analysis Category Sections rework (W0, D3/D5) — the old Adds/Cuts suggestion engine is
 * GONE end-to-end
 * The Motor A collection-ranking pipeline ([recomputeAddsInternal]/`onAddSuggestion`/
 * `onCutSuggestion`, plus the Scryfall backstop toggle and the whole free-text budget UI it used to
 * accept for signature compatibility) and the Motor B "Popular in similar decks" ranking (the
 * `SuggestAddsFromCommunityUseCase`-backed half of Motor B — NOT "Decks like yours", which is a
 * separate use case and survives, see below) were DELETED. [loadAnalysis] now only computes
 * [DeckHealth] (score/pillars/findings) and unconditionally clears [DeckDoctorState.stage] once
 * that's done — there is no more staged ADD pipeline to await.
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
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val crashReporter: CrashReporter,
    private val resolveCard: suspend (scryfallId: String) -> Card?,
    private val weightsProvider: suspend () -> ScoreWeightOverrides,
    // ── Motor B, "Decks like yours" only (Phase 4) — appended last, all defaulted, so no existing
    // positional-arg constructor call site (see `project_archetype_engine` memory's "append new
    // optional params at the end" rule) needs to change. `null`/`{ false }` defaults mean an
    // un-migrated caller simply never sees [DeckDoctorState.similarDecks] populated (identical to
    // today's behavior).
    private val findSimilarDecksUseCase: FindSimilarDecksUseCase? = null,
    private val isCommunityEngineEnabled: suspend () -> Boolean = { false },
) {

    private val _state = MutableStateFlow(DeckDoctorState())
    val state: StateFlow<DeckDoctorState> = _state.asStateFlow()

    private val _events = Channel<DeckDoctorEvent>(Channel.BUFFERED)
    val events: Flow<DeckDoctorEvent> = _events.receiveAsFlow()

    /**
     * Everything an in-memory incremental re-analysis needs after the first full [loadAnalysis].
     * A manual (Build-tab) card add/cut mutates [AnalysisCache.workingMainboard] and recomputes
     * profile/evaluation locally (see [recomputeIncremental]). Null until the first full analysis
     * runs.
     */
    private var analysisCache: AnalysisCache? = null

    private var analysisJob: Job? = null

    /** The in-flight Motor B ("Decks like yours") fetch job — cancelled before every new launch,
     * so only the LATEST fetch can ever survive to update [state]. */
    private var communityJob: Job? = null

    /**
     * Workstream 8.4 -- bumped once per [loadAnalysis] call, BEFORE its coroutine is launched.
     * [recomputeCommunityInternal] captures the generation value active at that moment
     * (`ownerGeneration`) and re-checks it against the CURRENT [stagingGeneration] before applying
     * a [DoctorAnalysisStage] transition. This is what stops an old, superseded [loadAnalysis]
     * pass's own [communityJob] (a sibling of [analysisJob], NOT its child -- cancelling
     * [analysisJob] alone does not stop it) from emitting a stale stage update after a NEWER
     * [loadAnalysis] call has already taken over — the job is independently cancelled via its own
     * existing cancel-before-launch call, but only once the new pass's code reaches it, which can
     * lag behind the old job's own in-flight network call.
     */
    private var stagingGeneration: Int = 0

    private class AnalysisCache(
        var workingMainboard: List<DeckEntry>,
        val format: DeckFormat,
        val commanderIdentity: Set<String>,
        /** The resolved commander's name (Motor B: EDHREC commander-aggregate lookup key). Null for
         * non-Commander decks or an unresolved commander. */
        val commanderName: String?,
        val seedTags: List<CardTag>,
        val collection: List<Card>,
        /** Kept for a possible future wishlist-backed feature. Not currently read anywhere. */
        val wishlistIds: Set<String>,
        val resolvedById: MutableMap<String, Card>,
        val unresolvedCount: Int,
        /** Wave 2 / B3: total sideboard card count, captured once per full [loadAnalysis] pass and
         * reused UNCHANGED by every incremental [recomputeIncremental] call (mainboard add/cut
         * never touches the sideboard) — fed to P5's
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
    )

    /**
     * Full analysis: snapshot the live deck, resolve the mainboard, infer the seed tags
     * (commander + top identity-tag cards), and evaluate Health (score/pillars/findings). Primes
     * [analysisCache] for subsequent incremental recompute and sets [DeckDoctorState.isLoaded].
     */
    fun loadAnalysis(deckId: String) {
        analysisCache = null
        analysisJob?.cancel()
        // Workstream 8.4 -- this pass's own identity, captured BEFORE launch so every stage-emitting
        // sub-job it spawns ([recomputeCommunityInternal]) can tell whether it is still the CURRENT
        // pass by the time it actually gets to update [DeckDoctorState.stage].
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
                // extended (not duplicated) to also carry the 5 pillar weights. Deck Analysis Engine
                // v3 (spec §8): passed RAW (not pre-mapped) since the macro-dependent base weights
                // can only be resolved once EvaluateDeckUseCase knows the resolved macro -- see that
                // method's own KDoc for [scoreWeightOverrides].
                scoreWeightOverrides = weightOverrides,
                sideboardCount = sideboardCount,
            )

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
                // Deck Analysis Engine v3 fix (Phase 5, 2026-08-27): PillarResult.notApplicable
                // (currently only SYNERGY, on a zero-edge deck) means the pillar was never actually
                // MEASURED -- its subscore is forced to 0 as a formality (see the field's own KDoc),
                // never a real low score. minByOrNull used to pick that forced 0 unconditionally, so
                // every edge-less deck (a legitimate, well-built plan with no graph-based synergy)
                // reported SYNERGY as its "weakest pillar" even though nothing about SYNERGY was
                // actually poor -- excluded here so this key only ever names a pillar that was
                // genuinely scored.
                val weakestPillar = analysis.pillars.filterNot { it.notApplicable }.minByOrNull { it.subscore }
                crashReporter.setCustomKey("deck_analysis_score_bucket", scoreBucket(analysis.totalScore))
                crashReporter.setCustomKey("deck_analysis_pillar_min_id", weakestPillar?.id?.name ?: "none")
                crashReporter.setCustomKey("deck_analysis_format", format.name)
                crashReporter.setCustomKey("deck_analysis_strategy_id", analysis.strategy.curatedStrategyId ?: "custom")
                crashReporter.log("deck_analysis_completed")
            }

            // Deck Analysis Category Sections rework (W0, D3/D5): the old Motor A ADD pipeline
            // (recomputeAddsInternal) is GONE -- Health above is the only thing this pass computes
            // now, so [DeckDoctorState.stage] clears to null UNCONDITIONALLY right here, folding
            // whatever stage is still showing (READING_DECK_PLAN) into the completed-stages
            // checklist. This is the ORIGINAL [loadAnalysis] coroutine (not a spawned sub-job
            // re-checked against [stagingGeneration] like [recomputeCommunityInternal] below), so no
            // staleness re-check is needed here.
            //
            // PRESERVED BUG FIX (2026-08-20): the Suggestions tab's staged-progress gate in
            // DeckStudioScreen is `doctorStage != null`, NOT `isSuggestionsLoading` -- clearing
            // `stage` unconditionally (not just `isSuggestionsLoading`) is what keeps that gate
            // honest; a prior version of this method left `stage` set whenever the (now-deleted)
            // suggestions-engine flag was off, which stuck the Analysis tab on "Reading your deck
            // plan..." forever.
            _state.update {
                it.copy(
                    health = withUnresolvedWarning(health, unresolvedCount),
                    isSuggestionsLoading = false,
                    strategyLocked = strategyLocked,
                    completedStages = if (it.stage != null) it.completedStages + it.stage else it.completedStages,
                    stage = null,
                )
            }
            // Motor B ("Decks like yours"): fetched once per full analysis, not on every
            // incremental add/cut (see [recomputeCommunityInternal]'s KDoc for why).
            recomputeCommunityInternal(ownerGeneration = myGeneration)
        }
    }

    /**
     * Motor B ("Decks like yours", Deck Doctor Community/Archetype plan Phase 4): fetches the
     * similar-decks carousel via [findSimilarDecksUseCase]. Entirely OPT-IN and defensive:
     *  - A `null` [findSimilarDecksUseCase] (the legacy no-arg constructor default) is a silent
     *    no-op — [DeckDoctorState.similarDecks] stays at its default.
     *  - [isCommunityEngineEnabled] false (D4's `communityEngineEnabledFlow` off) is likewise a
     *    silent no-op, no network/cache access at all.
     *  - A failed fetch simply leaves [DeckDoctorState.similarDecks] empty — it NEVER touches
     *    [DeckDoctorState.health] above.
     *
     * Deck Analysis Category Sections rework (W0, D3/D4): the community-AGGREGATE-backed half of
     * Motor B ("Popular in similar decks", ranked via the now-deleted
     * `SuggestAddsFromCommunityUseCase`) was DELETED here — this function no longer fetches
     * [com.mmg.manahub.core.model.CommunityAggregate] at all, only the similar-decks carousel.
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
        val similarUseCase = findSimilarDecksUseCase ?: return

        communityJob?.cancel()
        communityJob = scope.launch {
            if (!isCommunityEngineEnabled()) {
                _state.update { it.copy(similarDecks = emptyList()) }
                return@launch
            }
            if (ownerGeneration == stagingGeneration) advanceDoctorStage(DoctorAnalysisStage.SEARCHING_COMMUNITY)

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

            _state.update { it.copy(similarDecks = similarDecks) }
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
     * Re-evaluates the deck IN MEMORY from [AnalysisCache.workingMainboard] after a manual
     * (Build-tab) card add/cut: rebuilds profile/evaluation (pure).
     *
     * [AnalysisCache.seedTags] is reused UNCHANGED (never re-derived here) -- it was already computed
     * once in [loadAnalysis] as `inference + `[pinSeedTags]` (Wave 4, Task 1), so every incremental
     * add/cut in this session ranks against the EXACT SAME basis the full analysis started from. An
     * archetype/theme override change goes through [setArchetypeOverride], which re-runs a FULL
     * [loadAnalysis] rather than an incremental step (see its own KDoc) -- so there is no path where
     * this method's basis can drift from the pin.
     */
    private fun recomputeIncremental() {
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
                // Deck Analysis Engine v3 (spec §8) -- raw overrides, see the sibling call site above.
                scoreWeightOverrides = weightOverrides,
                sideboardCount = context.sideboardCount,
            )
            _state.update {
                it.copy(health = withUnresolvedWarning(health, context.unresolvedCount))
            }
        }
    }

    /**
     * Adds one copy of [scryfallId] to the in-memory working mainboard and recomputes
     * incrementally. Returns `false` when there is no primed [analysisCache] or the card cannot
     * be resolved from any cached source — the caller must fall back to a full [loadAnalysis] in
     * that case.
     */
    fun onAddCard(scryfallId: String): Boolean {
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
        recomputeIncremental()
        return true
    }

    /**
     * Removes ONE copy of [scryfallId] from the in-memory working mainboard (C1 — decrements a
     * multi-copy slot rather than dropping the whole slot) and recomputes incrementally. Returns
     * `false` when there is no primed [analysisCache] — the caller must fall back to a full
     * [loadAnalysis] in that case.
     */
    fun onCutCard(scryfallId: String): Boolean {
        val context = analysisCache ?: return false
        context.workingMainboard = context.workingMainboard.mapNotNull { entry ->
            if (entry.card.scryfallId != scryfallId) entry
            else if (entry.quantity <= 1) null
            else entry.copy(quantity = entry.quantity - 1)
        }
        recomputeIncremental()
        return true
    }

    /**
     * The working-mainboard quantity for [scryfallId] if [analysisCache] is primed, else `null`
     * (the host falls back to its own live-deck quantity source). Lets the host compute the
     * correct repository write (decrement vs. delete) BEFORE calling [onCutCard].
     */
    fun cachedMainboardQuantity(scryfallId: String): Int? =
        analysisCache?.workingMainboard?.firstOrNull { it.card.scryfallId == scryfallId }?.quantity

    /** Looks up a resolved [Card] in the cached resolved-by-id map (no repository call). */
    private fun findCachedCard(context: AnalysisCache, scryfallId: String): Card? =
        context.resolvedById[scryfallId]

    /**
     * Invalidates the loaded analysis after a MANUAL (Build-tab) deck mutation so the next time
     * the host opens Suggestions a fresh [loadAnalysis] re-syncs with the live deck. Deliberately
     * does NOT recompute here (the work is wasted while the user is still editing) and must NEVER
     * be called from a deck-observe transformer (that would create a write→observe→recompute
     * feedback loop — the host is responsible for calling this only from explicit manual
     * mutations). [DeckDoctorState.health] is left untouched (stale but hidden once
     * [DeckDoctorState.isLoaded] flips to `false`) — mirrors the pre-extraction behavior exactly.
     */
    fun invalidate() {
        if (_state.value.isLoaded) {
            analysisJob?.cancel()
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
     * pass already finished ([loadAnalysis]'s own terminal update cleared it, unconditionally, right
     * after Health is computed) — in the latter case this guard is what stops a late
     * [recomputeCommunityInternal] update from resurrecting the staged screen after the real
     * Analysis content is already showing.
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
        // Deck Analysis Engine v3: ArchetypeId.GENERIC no longer exists -- forArchetype now takes a
        // nullable archetype directly (null = no macro pin), no fallback coercion needed.
        return DeckIdentitySeedTags.forArchetype(archetype, themes, tribeOverride)
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
            loadAnalysis(deckId)
        }
    }

    /** "Auto-detect" — clears both the macro and theme pin and re-infers from scratch. */
    fun clearArchetypeOverride(deckId: String) {
        setArchetypeOverride(deckId, archetypeId = null, themes = emptyList())
    }

    /**
     * Deck Engine Unification plan (D4): the deck's own explicit "Unlock strategy" action —
     * flips `Deck.strategyLocked` off (releasing the gate on the host UI's "Deck plan" editor) then
     * re-runs a full [loadAnalysis] (mirrors [setArchetypeOverride]'s "cheap enough to just reload"
     * precedent).
     */
    fun unlockStrategy(deckId: String) {
        scope.launch {
            runCatching {
                deckRepository.updateStrategyLocked(deckId, false)
            }.onFailure {
                crashReporter.log("deck_studio_unlock_strategy_failed")
                crashReporter.recordException(RuntimeException("[DeckDoctorOrchestrator] deck_studio_unlock_strategy_failed", it))
                return@launch
            }
            loadAnalysis(deckId)
        }
    }

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
    }
}
