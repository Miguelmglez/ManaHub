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
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
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
 * `SuggestAddsWithBudgetUseCase` pipeline (wishlist + external Scryfall via the now-dormant
 * `CandidatePoolGenerator`/`BudgetOptimizer`, D5 — see `project_dormant_budget_pool` memory) with
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
        // ── Archetype-aware Deck Doctor (Phase 1.6, D2) ─────────────────────────
        /** Raw `Deck.archetypeOverride`/`themesOverride` — re-read on every incremental recompute
         * so a mid-session override change (via [setArchetypeOverride]/[clearArchetypeOverride])
         * is picked up without a full [loadAnalysis]. */
        var archetypeOverride: String?,
        var themesOverride: List<String>,
        val commanderTags: List<CardTag>,
    )

    /**
     * Full analysis: snapshot the live deck, resolve the mainboard, infer the seed tags
     * (commander + top identity-tag cards), evaluate Health + cuts, then run the ADD pipeline.
     * Primes [analysisCache] for subsequent incremental recompute and sets [DeckDoctorState.isLoaded].
     */
    fun loadAnalysis(deckId: String, constraints: BudgetConstraints) {
        analysisCache = null
        analysisJob?.cancel()
        analysisJob = scope.launch {
            crashReporter.log("deck_studio_suggestions_analysis_started")
            _state.update { it.copy(isSuggestionsLoading = true, isLoaded = true) }

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

            val seedCards = inferenceSeeds(commanderCard, mainboardEntries)
            val seedTags = inferDeckIdentityUseCase(seedCards).seedTags
            val weights = weightsProvider().toScoreWeights()
            val archetypeOverride = deckWithCards.deck.archetypeOverride
            val themesOverride = deckWithCards.deck.themesOverride

            val health = evaluateDeckUseCase(
                mainboard = mainboardEntries,
                format = format,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                weights = weights,
                archetypeOverride = archetypeOverride,
                themesOverride = themesOverride,
                commanderTags = commanderTags,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboardEntries,
                profile = health.profile,
                protectedIds = setOfNotNull(commanderId),
                weights = weights,
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
                commanderId = commanderId,
                commanderIdentity = commanderIdentity,
                commanderName = commanderCard?.name,
                seedTags = seedTags,
                collection = collectionCards,
                wishlistIds = wishlistIds,
                resolvedById = resolvedById,
                unresolvedCount = unresolvedCount,
                archetypeOverride = archetypeOverride,
                themesOverride = themesOverride,
                commanderTags = commanderTags,
            )

            if (unresolvedCount > 0) {
                crashReporter.setCustomKey(
                    "deck_studio_card_count",
                    (mainboardEntries.sumOf { it.quantity } + unresolvedCount).toString(),
                )
            }
            crashReporter.log("deck_studio_suggestions_analysis_succeeded")

            _state.update {
                it.copy(
                    health = withUnresolvedWarning(health, unresolvedCount),
                    cuts = cuts,
                    isSuggestionsLoading = false,
                )
            }
            recomputeAddsInternal(constraints)
            // Motor B (Phase 4): fetched once per full analysis, not on every incremental add/cut
            // (see [recomputeCommunityInternal]'s KDoc for why) — [onAddCard]/[onCutCard] instead
            // locally filter the already-fetched lists.
            recomputeCommunityInternal()
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
     */
    private fun recomputeAddsInternal(@Suppress("UNUSED_PARAMETER") constraints: BudgetConstraints) {
        val context = analysisCache ?: return
        val health = _state.value.health ?: return
        // Cancel any in-flight recompute so two racing computations can't both emit a stale ADD
        // list (H3).
        recomputeAddsJob?.cancel()
        recomputeAddsJob = scope.launch {
            _state.update { it.copy(isAddsLoading = true) }
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
                _state.update { it.copy(isAddsLoading = false) }
                _events.send(DeckDoctorEvent.ExternalPoolFailed)
                return@launch
            }

            suggestions.forEach {
                val id = it.fit.card.scryfallId
                if (id !in context.resolvedById) context.resolvedById[id] = it.fit.card
            }

            _state.update {
                it.copy(
                    adds = suggestions,
                    addsTotalCostEur = 0.0,
                    addsCardsToBuy = 0,
                    isAddsLoading = false,
                )
            }
        }
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
     */
    private fun recomputeCommunityInternal() {
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
     */
    private fun recomputeIncremental(constraints: BudgetConstraints) {
        val context = analysisCache ?: return
        scope.launch {
            val mainboard = context.workingMainboard
            val weights = weightsProvider().toScoreWeights()
            val health = evaluateDeckUseCase(
                mainboard = mainboard,
                format = context.format,
                commanderIdentity = context.commanderIdentity,
                seedTags = context.seedTags,
                weights = weights,
                archetypeOverride = context.archetypeOverride,
                themesOverride = context.themesOverride,
                commanderTags = context.commanderTags,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboard,
                profile = health.profile,
                protectedIds = setOfNotNull(context.commanderId),
                weights = weights,
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
            _state.update { it.copy(isLoaded = false) }
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
            colorCount = health.profile.colorIdentity.size,
        )
    }

    // ── Archetype override (Phase 1.7 Studio UI entry point) ───────────────────────

    /**
     * Pins (or, when both params are null/empty, clears) the deck's archetype/theme override —
     * writes through [DeckRepository.updateArchetypeOverride] then re-runs a FULL [loadAnalysis]
     * (a macro/theme change reshapes the whole resolved skeleton, so an incremental recompute is
     * not enough — mirrors [changeFormat]'s "cheap enough to just reload" precedent).
     *
     * @param archetypeId `null` clears the macro pin (back to inference); a non-null value pins it.
     * @param themes at most 2 (the caller — the Studio bottom sheet — already enforces this cap);
     *        empty clears the theme pin.
     */
    fun setArchetypeOverride(deckId: String, constraints: BudgetConstraints, archetypeId: ArchetypeId?, themes: List<ThemeId>) {
        scope.launch {
            runCatching {
                deckRepository.updateArchetypeOverride(
                    deckId = deckId,
                    archetypeOverride = archetypeId?.name,
                    themesOverride = themes.take(2).map { it.name },
                )
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

    /** Appends a [DeckWarning.UnresolvedCards] when one or more mainboard slots failed to resolve. */
    private fun withUnresolvedWarning(health: DeckHealth, unresolvedCount: Int): DeckHealth {
        if (unresolvedCount <= 0) return health
        val withWarning = health.evaluation.copy(
            warnings = health.evaluation.warnings + DeckWarning.UnresolvedCards(unresolvedCount)
        )
        return health.copy(evaluation = withWarning)
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
