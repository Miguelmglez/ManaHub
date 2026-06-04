package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.feature.decks.presentation.engine.CardFit
import com.mmg.manahub.feature.decks.presentation.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.presentation.engine.DeckProfile
import com.mmg.manahub.feature.decks.presentation.engine.DeckScorer
import com.mmg.manahub.feature.decks.presentation.engine.ScoreWeights
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
     * @param profile the deck profile (color identity, format, fingerprint).
     * @param evaluation the deck evaluation (its role gaps drive the external query set).
     * @param constraints the active budget filters.
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param limit maximum number of ranked suggestions BEFORE budget filtering.
     * @return the budget-filtered ranked suggestions plus the cost summary.
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
        val usdCap = constraints.maxPerCardEur?.let { eurToUsdLooseCap(it) }
        val externalCards = runCatching {
            candidatePoolGenerator(profile = profile, evaluation = evaluation, usdCap = usdCap)
        }.getOrDefault(emptyList())
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

        val suggestions = ranked.map { fit ->
            AddSuggestion(
                fit = fit,
                origin = originById[fit.card.scryfallId] ?: AddOrigin.NEW,
            )
        }

        budgetOptimizer(suggestions = suggestions, constraints = constraints)
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
