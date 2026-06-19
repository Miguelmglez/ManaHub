package com.mmg.manahub.feature.decks.presentation.improvement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.toScoreWeights
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.queryFragment
import com.mmg.manahub.feature.decks.presentation.improvement.DeckImprovementViewModel.Companion.MAX_SEED_CARDS
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/**
 * One-shot UI events emitted by [DeckImprovementViewModel]. A buffered [Channel] is used (not a
 * nullable StateFlow) so repeated identical toasts are never equality-collapsed or dropped while
 * the lifecycle is paused. The event carries the structured data (card name); the UI layer turns
 * it into a localized [com.mmg.manahub.core.ui.components.MagicToast] message.
 */
sealed interface DeckImprovementEvent {
    /** A card was removed from the deck. */
    data class CardCut(val cardName: String) : DeckImprovementEvent

    /** A card was added to the deck. */
    data class CardAdded(val cardName: String) : DeckImprovementEvent

    /**
     * The external (Scryfall) candidate fetch failed; suggestions fell back to collection + wishlist.
     * The UI surfaces this as a non-fatal warning toast.
     */
    data object ExternalPoolFailed : DeckImprovementEvent
}

@HiltViewModel
class DeckImprovementViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    private val evaluateDeckUseCase: EvaluateDeckUseCase,
    private val inferDeckIdentityUseCase: InferDeckIdentityUseCase,
    private val suggestCutsUseCase: SuggestCutsUseCase,
    private val suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase,
    private val wishlistRepository: WishlistRepository,
    private val userPreferences: UserPreferencesDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])
    private val _uiState = MutableStateFlow(DeckImprovementUiState())
    val uiState: StateFlow<DeckImprovementUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeckImprovementEvent>(Channel.BUFFERED)
    val events: Flow<DeckImprovementEvent> = _events.receiveAsFlow()

    /**
     * Everything an in-memory incremental re-analysis (plan E4) needs after the first full
     * [loadAnalysis]. A cut/add mutates [workingMainboard] and recomputes profile/evaluation/cuts
     * locally; the expensive external (Scryfall) pool is only re-fetched when the deck's queryable
     * [gapSignature] changes — otherwise the cached [externalPool] is re-fed to the ADD pipeline.
     * Null until the first full analysis has populated it.
     */
    private var analysisCache: AnalysisCache? = null

    private var analysisJob: Job? = null

    /**
     * Immutable-ish snapshot of the inputs the incremental ADD/CUT recompute reuses. The mutable
     * deck state lives in [workingMainboard]; the rest is captured once per full [loadAnalysis].
     */
    private class AnalysisCache(
        var workingMainboard: List<DeckEntry>,
        val format: DeckFormat,
        val commanderId: String?,
        val commanderIdentity: Set<String>,
        val seedTags: List<CardTag>,
        val collection: List<Card>,
        val wishlistIds: Set<String>,
        /**
         * Every full [Card] the VM has resolved so far (initial mainboard + collection), so an
         * add re-resolves from memory instead of forcing a full [loadAnalysis] when the card is not
         * in the current add list / collection (e.g. re-adding a card that was just cut). Grown as
         * new candidates appear.
         */
        val resolvedById: MutableMap<String, Card>,
        /** Mainboard slots that could not be resolved to a full Card (plan E6); constant per deck. */
        val unresolvedCount: Int,
        /** The queryable gap set + identity/format that drives the external Scryfall query (E4). */
        var gapSignature: GapSignature,
        /** The last external pool fetched, reused while [gapSignature] is unchanged. */
        var externalPool: List<Card>,
    )

    /**
     * Signature of everything that determines the EXTERNAL (Scryfall) candidate query in
     * [com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator]: the set of under-covered
     * roles that have a query fragment, plus the color identity and format. When two analyses share
     * this signature, the external pool would be identical, so we reuse the cached one and issue ZERO
     * Scryfall calls (plan E4 acceptance).
     */
    private data class GapSignature(
        val gapRoles: Set<DeckRole>,
        val colorIdentity: Set<ManaColor>,
        val format: DeckFormat,
    )

    init {
        loadAnalysis()
    }

    /**
     * Full (network-backed) analysis: fetch the deck, the collection and the wishlist, resolve every
     * mainboard slot, infer the strategy seed (B4), build the Health evaluation + cut list, then run
     * the ADD pipeline (which fetches the external pool). Also primes [analysisCache] so subsequent
     * add/cut changes recompute incrementally without re-running any of this.
     */
    private fun loadAnalysis() {
        analysisCache = null
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { s -> if (s.health == null) s.copy(isLoading = true) else s }

            val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            if (deckWithCards == null) {
                _uiState.update { it.copy(isLoading = false, error = "Deck not found") }
                return@launch
            }

            val collection = userCardRepository.observeCollection().first()
            val deckFormat = DeckFormat.entries
                .firstOrNull { it.name.equals(deckWithCards.deck.format, ignoreCase = true) }
                ?: DeckFormat.CASUAL.also {
                    FirebaseCrashlytics.getInstance().log("Unknown deck format: '${deckWithCards.deck.format}' – defaulting to CASUAL")
                }

            // Resolve every mainboard slot. Slots whose card cannot be resolved are counted (E6) and
            // surfaced as a warning instead of silently shrinking the deck; only resolved slots feed
            // the engine (it cannot score a card it does not have).
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

            // B4 — seed the profile via inference: the commander (when present) + the deck's
            // highest-weight identity cards. The fingerprint is no longer purely self-referential;
            // the commander's own strategy now seeds the evaluation. Phase-3 floors the seed weight
            // (SEED_FLOOR) so seed influence is size-independent — a singleton commander seed counts
            // the same in a 99-card list as in an empty one.
            val seedCards = inferenceSeeds(commanderCard, mainboardEntries)
            val seedTags = inferDeckIdentityUseCase(seedCards).seedTags

            // F2 — read the (debug-tuned) score weights once for this analysis pass. With no override
            // persisted the DataStore emits ScoreWeightOverrides.NONE, which maps to the default
            // ScoreWeights() → byte-identical output.
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()

            val health = evaluateDeckUseCase(
                mainboard = mainboardEntries,
                format = deckFormat,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                weights = weights,
            )

            val protectedIds = setOfNotNull(commanderId)
            val cuts = suggestCutsUseCase(
                mainboard = mainboardEntries,
                profile = health.profile,
                protectedIds = protectedIds,
                weights = weights,
            )

            val collectionCards = collection.map { it.card }
            val wishlistIds = wishlistRepository.observeLocal().first()
                .map { it.cardId }
                .toSet()

            // Seed the resolved-card lookup with the initial mainboard + collection so a later
            // re-add (of a card just cut) resolves from memory and never forces a full reload.
            val resolvedById = HashMap<String, Card>()
            mainboardEntries.forEach { resolvedById[it.card.scryfallId] = it.card }
            collectionCards.forEach { resolvedById.putIfAbsent(it.scryfallId, it) }
            commanderCard?.let { resolvedById.putIfAbsent(it.scryfallId, it) }

            analysisCache = AnalysisCache(
                workingMainboard = mainboardEntries,
                format = deckFormat,
                commanderId = commanderId,
                commanderIdentity = commanderIdentity,
                seedTags = seedTags,
                collection = collectionCards,
                wishlistIds = wishlistIds,
                resolvedById = resolvedById,
                unresolvedCount = unresolvedCount,
                gapSignature = gapSignatureOf(health),
                externalPool = emptyList(), // primed by the first recomputeAdds() below
            )

            // Render Health + Cut immediately; the ADD pipeline (network) fills in asynchronously.
            _uiState.update {
                it.copy(
                    deckName = deckWithCards.deck.name,
                    health = withUnresolvedWarning(health, unresolvedCount),
                    cuts = cuts,
                    isLoading = false,
                )
            }
            // First ADD run: fetch a fresh external pool (no override) and cache it.
            recomputeAdds(externalOverride = null)
        }
    }

    /**
     * Recomputes the ADD suggestions from [analysisCache] using the current
     * [DeckImprovementUiState.budget]. On a Scryfall failure the pipeline already falls back to
     * collection + wishlist, so this never crashes — it only emits a warning toast when the whole
     * pipeline fails. Safe to call repeatedly (budget changes / incremental add-cut).
     *
     * @param externalOverride when non-null, the cached external pool is reused and NO Scryfall call
     *        is made (plan E4); when null, a fresh external pool is fetched and re-cached.
     */
    private fun recomputeAdds(
        constraints: BudgetConstraints = _uiState.value.budget,
        externalOverride: List<Card>?,
    ) {
        val context = analysisCache ?: return
        val profile = lastProfile ?: return
        val evaluation = lastEvaluation ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddsLoading = true) }
            val mainboardIds = context.workingMainboard.map { it.card.scryfallId }.toSet()
            // D3 — per-name copy counts so a 60-card-format suggestion can top a card up to a playset.
            val mainboardCopiesByName = context.workingMainboard
                .groupBy { it.card.name }
                .mapValues { (_, entries) -> entries.sumOf { it.quantity } }
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
            val selection = runCatching {
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
            }.getOrNull()

            if (selection == null) {
                _uiState.update { it.copy(isAddsLoading = false) }
                _events.send(DeckImprovementEvent.ExternalPoolFailed)
                return@launch
            }

            // Cache the pool we just used so the next gap-unchanged add/cut can reuse it (E4).
            context.externalPool = selection.externalPool
            // Remember every suggested card so re-adding it later resolves from memory (no reload).
            selection.selected.forEach { context.resolvedById.putIfAbsent(it.fit.card.scryfallId, it.fit.card) }

            _uiState.update {
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
     * add/cut (plan E4): rebuild profile/evaluation/cuts (all pure, no network), then recompute the
     * ADD suggestions. The external Scryfall pool is re-fetched ONLY when the queryable gap set
     * changed; otherwise the cached pool is reused and ZERO Scryfall calls are issued.
     */
    private fun recomputeIncremental() {
        val context = analysisCache ?: return
        viewModelScope.launch {
            val mainboard = context.workingMainboard
            val weights = userPreferences.observeScoreWeightOverrides().first().toScoreWeights()
            val health = evaluateDeckUseCase(
                mainboard = mainboard,
                format = context.format,
                commanderIdentity = context.commanderIdentity,
                seedTags = context.seedTags,
                weights = weights,
            )
            val cuts = suggestCutsUseCase(
                mainboard = mainboard,
                profile = health.profile,
                protectedIds = setOfNotNull(context.commanderId),
                weights = weights,
            )
            _uiState.update {
                it.copy(
                    health = withUnresolvedWarning(health, context.unresolvedCount),
                    cuts = cuts,
                )
            }

            val newSignature = gapSignatureOf(health)
            val gapsUnchanged = newSignature == context.gapSignature
            context.gapSignature = newSignature
            // Gaps unchanged → reuse the cached external pool (no Scryfall). Gaps changed → fetch.
            recomputeAdds(externalOverride = if (gapsUnchanged) context.externalPool else null)
        }
    }

    /** Updates the budget filters and recomputes the ADD suggestions (no full re-analysis). */
    fun onBudgetChanged(budget: BudgetConstraints) {
        _uiState.update { it.copy(budget = budget) }
        // A budget change can alter the external USD pre-filter, so re-fetch the pool (override null).
        recomputeAdds(constraints = budget, externalOverride = null)
    }

    /** Resolves a Scryfall id to a full [Card], or null when it is not available. */
    private suspend fun resolveCard(scryfallId: String): Card? =
        when (val res = cardRepository.getCardById(scryfallId)) {
            is DataResult.Success -> res.data
            else -> null
        }

    /** Switches the active Deck Doctor tab (Health / Cut / Add). */
    fun onTabSelected(tab: DeckDoctorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Removes a cut-candidate card from the deck's mainboard. Persists the change, mutates the
     * in-memory working list, and recomputes the analysis INCREMENTALLY (plan E4) — no deck fetch,
     * no collection/wishlist re-read, and no Scryfall call unless the gap set changed. Falls back to
     * a full [loadAnalysis] only when the incremental cache is missing (e.g. a cut before the first
     * analysis finished).
     */
    fun onCut(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            val context = analysisCache
            // C1: cut ONE copy, not the whole slot. A 3-of must become a 2-of. Previously this
            // always removed the entire slot, silently dropping every copy of a multi-copy card
            // — a data-loss bug. The current quantity comes from the in-memory working list
            // (the only quantity source this VM holds); with no cache yet we fall back to a full
            // slot removal, which then triggers a fresh loadAnalysis below.
            val currentQty = context?.workingMainboard
                ?.firstOrNull { it.card.scryfallId == scryfallId }?.quantity
                ?: 1
            if (currentQty <= 1) {
                deckRepository.removeCardFromDeck(deckId, scryfallId, isSideboard = false)
            } else {
                deckRepository.addCardToDeck(deckId, scryfallId, currentQty - 1, false)
            }
            _events.send(DeckImprovementEvent.CardCut(cardName))

            if (context == null) {
                loadAnalysis()
                return@launch
            }
            // Mutate the working list in memory: decrement one copy, dropping the entry at qty 0.
            context.workingMainboard = context.workingMainboard.mapNotNull { entry ->
                if (entry.card.scryfallId != scryfallId) entry
                else if (entry.quantity <= 1) null
                else entry.copy(quantity = entry.quantity - 1)
            }
            recomputeIncremental()
        }
    }

    /**
     * Adds a suggested card (one copy) to the deck's mainboard. Persists the change, mutates the
     * in-memory working list, and recomputes INCREMENTALLY (plan E4). The added card was already a
     * resolved suggestion, so it is appended directly to the working list — no card re-resolution and
     * no full re-analysis. Falls back to [loadAnalysis] only when the card cannot be found in any
     * cached source (it then needs a fresh resolve).
     */
    fun onAdd(scryfallId: String, cardName: String) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, scryfallId, 1, false)
            _events.send(DeckImprovementEvent.CardAdded(cardName))

            val context = analysisCache
            if (context == null) {
                loadAnalysis()
                return@launch
            }
            // The added card came from a suggestion fed by the collection/wishlist/external pool, so
            // it is already resolved in one of the cached sources or the current add list.
            val added = findCachedCard(context, scryfallId)
            if (added == null) {
                // Unknown source (defensive) → fall back to a full reload that resolves it from the repo.
                loadAnalysis()
                return@launch
            }
            // Merge a second copy into an existing entry, or append a new one.
            val existing = context.workingMainboard.firstOrNull { it.card.scryfallId == scryfallId }
            context.workingMainboard = if (existing != null) {
                context.workingMainboard.map {
                    if (it.card.scryfallId == scryfallId) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                context.workingMainboard + DeckEntry(card = added, quantity = 1, isOwned = false, isSideboard = false)
            }
            recomputeIncremental()
        }
    }

    /** Looks up a resolved [Card] in the cached add list / resolved-by-id map (no repository call). */
    private fun findCachedCard(context: AnalysisCache, scryfallId: String): Card? =
        _uiState.value.adds.firstOrNull { it.fit.card.scryfallId == scryfallId }?.fit?.card
            ?: context.resolvedById[scryfallId]

    /**
     * Picks the inference seed cards (plan B4): the commander (when present) plus the deck's
     * highest-weight identity cards — those carrying the most STRATEGY / ARCHETYPE / TRIBAL tags,
     * which are the same identity categories the scorer fingerprints on. Capped at [MAX_SEED_CARDS]
     * so a single off-theme card never skews the seed; the commander is always included first.
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

    /** Number of identity-category (STRATEGY / ARCHETYPE / TRIBAL) tags a card carries. */
    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    /** The queryable gap set + identity/format that drives the external Scryfall pool (E4). */
    private fun gapSignatureOf(health: DeckHealth): GapSignature = GapSignature(
        gapRoles = health.evaluation.roleCoverage
            .filter { it.gap > 0 && it.role.queryFragment() != null }
            .map { it.role }
            .toSet(),
        colorIdentity = health.profile.colorIdentity,
        format = health.profile.format,
    )

    /**
     * Appends a [DeckWarning.UnresolvedCards] to the evaluation's warnings when one or more mainboard
     * slots failed to resolve (plan E6). Keeps the engine string-free and unaware of resolution — the
     * warning is purely a ViewModel concern.
     */
    private fun withUnresolvedWarning(health: DeckHealth, unresolvedCount: Int): DeckHealth {
        if (unresolvedCount <= 0) return health
        val withWarning = health.evaluation.copy(
            warnings = health.evaluation.warnings + DeckWarning.UnresolvedCards(unresolvedCount)
        )
        return health.copy(evaluation = withWarning)
    }

    // The profile/evaluation the ADD pipeline last consumed; recomputed every analysis pass.
    private val lastProfile: DeckProfile? get() = _uiState.value.health?.profile
    private val lastEvaluation: DeckEvaluation? get() = _uiState.value.health?.evaluation

    private companion object {
        /** Identity tag categories used to rank inference seed cards (mirrors the scorer's set). */
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /** Cap on auto-selected identity seed cards (plus the commander) so one card can't skew the seed. */
        const val MAX_SEED_CARDS = 8
    }
}
