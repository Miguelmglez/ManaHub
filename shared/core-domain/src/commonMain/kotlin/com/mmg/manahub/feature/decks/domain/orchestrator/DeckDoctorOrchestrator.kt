package com.mmg.manahub.feature.decks.domain.orchestrator

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.ScoreWeightOverrides
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.queryFragment
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
    /** Add suggestions (collection + wishlist + external), budget-filtered, best fit first. */
    val adds: List<AddSuggestion> = emptyList(),
    /** Total money the currently shown adds would cost to buy (owned/free cards excluded). */
    val addsTotalCostEur: Double = 0.0,
    /** How many of the shown adds have a non-zero price (i.e. need buying). */
    val addsCardsToBuy: Int = 0,
    /** True while the full analysis (Health + Cut + Add) is being computed. */
    val isSuggestionsLoading: Boolean = false,
    /** True while only the external (Scryfall) ADD pool is being fetched/recomputed. */
    val isAddsLoading: Boolean = false,
    /** True once at least one full [DeckDoctorOrchestrator.loadAnalysis] has completed. */
    val isLoaded: Boolean = false,
)

/** One-shot Deck Doctor events, delivered through a buffered [Channel] (never a nullable StateFlow). */
sealed interface DeckDoctorEvent {
    /**
     * The external (Scryfall) candidate fetch failed; suggestions fell back to collection +
     * wishlist. The host surfaces this as a non-fatal warning toast.
     */
    data object ExternalPoolFailed : DeckDoctorEvent
}

/**
 * Owns the Deck Doctor "Suggestions" incremental-analysis machinery: the [AnalysisCache] +
 * [GapSignature] pattern, the full [loadAnalysis] pass, and incremental card add/cut recompute —
 * extracted out of `DeckStudioViewModel` (Phase 0.4,
 * `docs/claude-code-prompt-deck-doctor-community.md`) so Phase 1's archetype-aware engine has a
 * single home for this orchestration instead of two independently-maintained VM-side copies.
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
 * analysis can all race to recompute the external candidate pool; each would write
 * `analysisCache.externalPool` / emit a fresh adds list. [recomputeAddsJob] is cancelled
 * immediately before every new launch (mirroring the pre-extraction `DeckStudioViewModel`
 * behavior verbatim) so only the LATEST recompute can ever survive to update [state] — a stale,
 * slower fetch can never clobber the cache or emit a stale ADD list.
 *
 * ## Zero-Scryfall incremental reuse (Phase 7 pattern — see `feedback_deck_doctor_phase7_incremental_reload`)
 * The external pool is re-fetched ONLY when [GapSignature] (queryable gap roles ∩
 * [DeckRole.queryFragment], color identity, format) changes between recomputes; otherwise the
 * cached pool is reused via `externalCardsOverride`, so a cut→re-add round-trip with an unchanged
 * gap set issues zero Scryfall calls.
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
    private val suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val crashReporter: CrashReporter,
    private val resolveCard: suspend (scryfallId: String) -> Card?,
    private val weightsProvider: suspend () -> ScoreWeightOverrides,
) {

    private val _state = MutableStateFlow(DeckDoctorState())
    val state: StateFlow<DeckDoctorState> = _state.asStateFlow()

    private val _events = Channel<DeckDoctorEvent>(Channel.BUFFERED)
    val events: Flow<DeckDoctorEvent> = _events.receiveAsFlow()

    /**
     * Everything an in-memory incremental re-analysis needs after the first full
     * [loadAnalysis]. A suggestion add/cut mutates [AnalysisCache.workingMainboard] and
     * recomputes profile/evaluation/cuts locally; the expensive external (Scryfall) pool is
     * only re-fetched when [AnalysisCache.gapSignature] changes. Null until the first full
     * analysis runs.
     */
    private var analysisCache: AnalysisCache? = null

    private var analysisJob: Job? = null

    /** The in-flight ADD recompute job (H3) — see the class doc for the cancel-before-launch contract. */
    private var recomputeAddsJob: Job? = null

    private class AnalysisCache(
        var workingMainboard: List<DeckEntry>,
        val format: DeckFormat,
        val commanderId: String?,
        val commanderIdentity: Set<String>,
        val seedTags: List<CardTag>,
        val collection: List<Card>,
        val wishlistIds: Set<String>,
        val resolvedById: MutableMap<String, Card>,
        val unresolvedCount: Int,
        var gapSignature: GapSignature,
        var externalPool: List<Card>,
        // ── Archetype-aware Deck Doctor (Phase 1.6, D2) ─────────────────────────
        /** Raw `Deck.archetypeOverride`/`themesOverride` — re-read on every incremental recompute
         * so a mid-session override change (via [setArchetypeOverride]/[clearArchetypeOverride])
         * is picked up without a full [loadAnalysis]. */
        var archetypeOverride: String?,
        var themesOverride: List<String>,
        val commanderTags: List<CardTag>,
    )

    /**
     * Drives the external Scryfall candidate query; an unchanged signature reuses the cached pool.
     * [archetypeId]/[colorCount] were added in Phase 1.6 (D18/D2): an override change or a
     * color-affecting mainboard edit must invalidate the cached external pool exactly like a gap-
     * role change already does — the resolved skeleton (and therefore which roles are "gaps")
     * depends on both.
     */
    private data class GapSignature(
        val gapRoles: Set<DeckRole>,
        val colorIdentity: Set<ManaColor>,
        val format: DeckFormat,
        val archetypeId: ArchetypeId,
        val themes: List<ThemeId>,
        val colorCount: Int,
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
                seedTags = seedTags,
                collection = collectionCards,
                wishlistIds = wishlistIds,
                resolvedById = resolvedById,
                unresolvedCount = unresolvedCount,
                gapSignature = gapSignatureOf(health),
                externalPool = emptyList(),
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
            recomputeAddsInternal(constraints, externalOverride = null)
        }
    }

    /**
     * Public entry for a budget-change-triggered recompute (a budget change alters the external
     * USD pre-filter, so the external pool is always re-fetched here — never reused).
     */
    fun recomputeAdds(constraints: BudgetConstraints) {
        recomputeAddsInternal(constraints, externalOverride = null)
    }

    /**
     * Recomputes the ADD suggestions from [analysisCache] against [constraints].
     *
     * @param externalOverride when non-null, the cached external pool is reused and NO Scryfall
     *        call is made; when null, a fresh external pool is fetched and re-cached.
     */
    private fun recomputeAddsInternal(constraints: BudgetConstraints, externalOverride: List<Card>?) {
        val context = analysisCache ?: return
        val profile = _state.value.health?.profile ?: return
        val evaluation = _state.value.health?.evaluation ?: return
        // Cancel any in-flight recompute so two racing fetches can't both write
        // context.externalPool / emit a stale ADD list (H3).
        recomputeAddsJob?.cancel()
        recomputeAddsJob = scope.launch {
            _state.update { it.copy(isAddsLoading = true) }
            val mainboardIds = context.workingMainboard.map { it.card.scryfallId }.toSet()
            val mainboardCopiesByName = context.workingMainboard
                .groupBy { it.card.name }
                .mapValues { (_, entries) -> entries.sumOf { it.quantity } }
            val weights = weightsProvider().toScoreWeights()
            val result = runCatching {
                suggestAddsWithBudgetUseCase(
                    collection = context.collection,
                    wishlistIds = context.wishlistIds,
                    mainboardIds = mainboardIds,
                    profile = profile,
                    evaluation = evaluation,
                    constraints = constraints,
                    weights = weights,
                    externalCardsOverride = externalOverride,
                    mainboardCopiesByName = mainboardCopiesByName,
                )
            }
            val selection = result.getOrNull()

            if (selection == null) {
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

            context.externalPool = selection.externalPool
            selection.selected.forEach {
                val id = it.fit.card.scryfallId
                if (id !in context.resolvedById) context.resolvedById[id] = it.fit.card
            }

            _state.update {
                it.copy(
                    adds = selection.selected,
                    addsTotalCostEur = selection.totalCostEur,
                    addsCardsToBuy = selection.cardsToBuy,
                    isAddsLoading = false,
                )
            }
        }
    }

    /**
     * Re-evaluates the deck IN MEMORY from [AnalysisCache.workingMainboard] after a single-card
     * suggestion add/cut: rebuild profile/evaluation/cuts (pure), then recompute ADD suggestions.
     * The external pool is re-fetched ONLY when the queryable gap set changed; otherwise the
     * cached pool is reused (ZERO Scryfall calls).
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

            val newSignature = gapSignatureOf(health)
            val gapsUnchanged = newSignature == context.gapSignature
            context.gapSignature = newSignature
            recomputeAddsInternal(constraints, externalOverride = if (gapsUnchanged) context.externalPool else null)
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

    /** The queryable gap set + identity/format/archetype that drives the external Scryfall pool. */
    private fun gapSignatureOf(health: DeckHealth): GapSignature = GapSignature(
        gapRoles = health.evaluation.roleCoverage
            .filter { it.gap > 0 && it.role.queryFragment() != null }
            .map { it.role }
            .toSet(),
        colorIdentity = health.profile.colorIdentity,
        format = health.profile.format,
        archetypeId = health.archetypeResolution.macro,
        themes = health.archetypeResolution.themes,
        colorCount = health.profile.colorIdentity.size,
    )

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
    }
}
