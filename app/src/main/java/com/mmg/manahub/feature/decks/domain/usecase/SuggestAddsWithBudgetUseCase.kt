package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 6 add pipeline: composes THREE candidate sources into a single ranked, budget-filtered list.
 *
 * Sources, by origin:
 *  - [AddOrigin.COLLECTION] — the user's owned cards not already in the deck (cost 0 €).
 *  - [AddOrigin.WISHLIST] — cards on the user's wishlist, resolved to full [Card]s via [CardRepository].
 *  - [AddOrigin.NEW] — external Scryfall recommendations from [CandidatePoolGenerator] aimed at role gaps.
 *
 * The union is scored ONCE by [DeckScorer.rankAdds] (so the HARD legality + color filter and the power
 * floor apply uniformly), then de-duplicated by `scryfallId` with origin priority
 * **COLLECTION > WISHLIST > NEW** (a card you already own is always shown as owned/free, even if it
 * also matched the wishlist or the external pool). Finally [BudgetOptimizer] trims the list to the UI's
 * [BudgetConstraints].
 *
 * Network resilience: the external pool is best-effort. If [CandidatePoolGenerator] throws (Scryfall
 * down / offline), the pipeline falls back to collection + wishlist only and never crashes. The whole
 * call is suspendable and cancellable via [withContext].
 */
class SuggestAddsWithBudgetUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    private val candidatePoolGenerator: CandidatePoolGenerator,
    private val budgetOptimizer: BudgetOptimizer,
    private val cardRepository: CardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param collection the user's owned cards (one [Card] per distinct scryfallId).
     * @param wishlistIds scryfallIds on the user's wishlist (resolved here to full cards).
     * @param mainboardIds scryfallIds already in the deck mainboard (excluded from every source).
     * @param mainboardCopiesByName for multi-copy adds (plan D3): how many copies of each card NAME
     *        are already in the deck, so a 60-card-format suggestion can top a card up to a playset
     *        (`4 − ownedCopies`). Empty (the default) keeps every suggestion at 1 copy. Keyed by
     *        card name because the 4-copy rule is by name, not printing.
     * @param profile the deck profile (color identity, format, fingerprint).
     * @param evaluation the deck evaluation (its role gaps drive the external query set).
     * @param constraints the active budget filters.
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param limit maximum number of ranked suggestions BEFORE budget filtering.
     * @param externalCardsOverride when non-null, the use case SKIPS [CandidatePoolGenerator] entirely
     *        and reuses this pre-fetched external pool (plan E4). The Deck Doctor ViewModel passes the
     *        previously-cached pool here when an add/cut leaves the deck's queryable **gap set**
     *        unchanged, so a 99-card cut→add round-trip triggers ZERO Scryfall calls. Null = fetch a
     *        fresh pool (the normal path).
     * @return the budget-filtered ranked suggestions, the cost summary, and the external pool used
     *         (so the caller can cache it for the next E4 reuse).
     */
    suspend operator fun invoke(
        collection: List<Card>,
        wishlistIds: Set<String>,
        mainboardIds: Set<String>,
        profile: DeckProfile,
        evaluation: DeckEvaluation,
        constraints: BudgetConstraints,
        weights: ScoreWeights = ScoreWeights(),
        limit: Int = 50,
        externalCardsOverride: List<Card>? = null,
        mainboardCopiesByName: Map<String, Int> = emptyMap(),
    ): BudgetSelection = withContext(ioDispatcher) {
        // ── Collection source ────────────────────────────────────────────────────
        val collectionCards = collection
            .filterNot { it.scryfallId in mainboardIds }
            .distinctBy { it.scryfallId }
        val ownedIds = collectionCards.mapTo(HashSet()) { it.scryfallId }

        // ── Wishlist source (resolve ids → full cards, skip ones we already own/added) ─
        val wishlistTargets = wishlistIds
            .filterNot { it in mainboardIds || it in ownedIds }
            .toSet()
        val wishlistCards = resolveCards(wishlistTargets)
        val wishlistResolvedIds = wishlistCards.mapTo(HashSet()) { it.scryfallId }

        // ── External source (best-effort; never fatal) ───────────────────────────
        // E4: when the caller supplies a pre-fetched pool (gap set unchanged), reuse it and DO NOT
        // touch CandidatePoolGenerator / Scryfall. The raw (un-filtered) pool is what we cache and
        // re-emit, so it stays valid across deck mutations; the per-deck exclusion below is applied
        // every time against the CURRENT mainboard/owned/wishlist state.
        val usdCap = constraints.maxPerCardEur?.let { eurToUsdLooseCap(it) }
        val rawExternalPool = externalCardsOverride ?: runCatching {
            candidatePoolGenerator(profile = profile, evaluation = evaluation, usdCap = usdCap)
        }.getOrDefault(emptyList())
        val externalCards = rawExternalPool
            .filterNot {
                it.scryfallId in mainboardIds ||
                    it.scryfallId in ownedIds ||
                    it.scryfallId in wishlistResolvedIds
            }

        // ── Union + single ranking pass ──────────────────────────────────────────
        // Track each card's origin so we can re-attach it after the engine drops the concept.
        val originById = HashMap<String, AddOrigin>()
        collectionCards.forEach { originById[it.scryfallId] = AddOrigin.COLLECTION }
        wishlistCards.forEach { originById.putIfAbsent(it.scryfallId, AddOrigin.WISHLIST) }
        externalCards.forEach { originById.putIfAbsent(it.scryfallId, AddOrigin.NEW) }

        // De-dup the union by id with COLLECTION > WISHLIST > NEW priority (LinkedHashMap keeps order).
        val unionById = LinkedHashMap<String, Card>()
        (collectionCards + wishlistCards + externalCards).forEach { card ->
            unionById.putIfAbsent(card.scryfallId, card)
        }

        val ranked: List<CardFit> = deckScorer.rankAdds(
            candidates = unionById.values.toList(),
            profile = profile,
            ownedIds = ownedIds,
            weights = weights,
            limit = limit,
        )

        // D3 — multi-copy adds. For 60-card constructed formats, suggest topping a card up to a
        // playset: `maxCopies − copies already in the deck` (clamped to ≥1). Commander/singleton
        // and Draft keep 1. Basic lands are never multi-copy-suggested here (they are not external
        // role candidates). Keyed by card name to honour the by-name 4-copy rule.
        val multiCopyFormat = profile.format.isSixtyCardConstructed
        val maxCopies = profile.format.maxCopies
        val suggestions = ranked.map { fit ->
            val copies = if (multiCopyFormat) {
                val already = mainboardCopiesByName[fit.card.name] ?: 0
                (maxCopies - already).coerceIn(1, maxCopies)
            } else {
                1
            }
            AddSuggestion(
                fit = fit,
                origin = originById[fit.card.scryfallId] ?: AddOrigin.NEW,
                suggestedCopies = copies,
            )
        }

        budgetOptimizer(
            suggestions = suggestions,
            constraints = constraints,
            externalPool = rawExternalPool,
        )
    }

    /** Resolves a set of scryfallIds to full [Card]s, dropping ids that fail to resolve. */
    private suspend fun resolveCards(ids: Set<String>): List<Card> =
        ids.mapNotNull { id ->
            when (val res = cardRepository.getCardById(id)) {
                is DataResult.Success -> res.data
                else -> null
            }
        }

    /**
     * Converts a EUR per-card cap into a LOOSE USD pre-filter for the Scryfall `usd<=` operator.
     * Scryfall does not support `eur<=` reliably, so we apply a generous multiplier (USD prices run
     * higher than EUR) — this is only a coarse pre-trim; the exact € filtering happens in
     * [BudgetOptimizer]. The +1 keeps a small epsilon so borderline cards are not lost to rounding.
     */
    private fun eurToUsdLooseCap(eurCap: Double): Double = eurCap * EUR_TO_USD_LOOSE_FACTOR + 1.0

    private companion object {
        /** Deliberately generous so the loose pre-filter never drops a card the € filter would keep. */
        const val EUR_TO_USD_LOOSE_FACTOR = 1.5
    }
}
