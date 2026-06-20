package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.MagicCard
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Result of [BuildDeckFromSeedsUseCase].
 *
 * @property mainboard the generated non-land mainboard (seeds first, then ranked fills) as
 *           [MagicCard]s — the type the active Deck Magic builder's REVIEW step consumes. Basic-land
 *           slots are intentionally NOT materialized here (see the class doc); [reservedLandSlots]
 *           reports how many land slots the format expects so the caller/UI can surface the gap.
 * @property reservedLandSlots the number of land slots reserved for the format (from the skeleton's
 *           LAND ideal, falling back to [DeckFormat.targetLandCount]).
 * @property usedExternalCandidates true when at least one Scryfall (NEW-origin) card made the cut —
 *           false when the network was unavailable and the deck was built from collection + seeds only.
 */
data class SeedDeckResult(
    val mainboard: List<MagicCard>,
    val reservedLandSlots: Int,
    val usedExternalCandidates: Boolean,
)

/**
 * Builds a deck mainboard from 1+ seed cards (Deck Doctor Phase 7).
 *
 * ## Inputs
 * Seeds, an [InferredIdentity] (from [InferDeckIdentityUseCase]), the target [DeckFormat], the user's
 * [BudgetConstraints] and their owned collection.
 *
 * ## Algorithm (heuristic — the user reviews the generated list)
 *  1. **Profile** — seeds become [DeckEntry]s and feed [DeckScorer.profile] with the inferred color
 *     identity + seedTags, so every candidate is scored as if the seeds were already in the deck.
 *  2. **Candidate pool** — union of (a) the owned collection minus the seeds, and (b) a best-effort
 *     external Scryfall pool from [CandidatePoolGenerator] aimed at the skeleton's role gaps (origin
 *     NEW). The whole external fetch is wrapped in `runCatching`: if Scryfall is down/offline the deck
 *     is built from collection + seeds only ([SeedDeckResult.usedExternalCandidates] = false).
 *  3. **Rank** — the union is scored once by [DeckScorer.rankAdds] (HARD legality + color filter, power
 *     floor) producing ranked [CardFit]s.
 *  4. **Budget** — [BudgetOptimizer] trims the ranked list to the active [BudgetConstraints] (owned
 *     cards are free), so over-budget external cards drop out before selection.
 *  5. **Fill toward the skeleton** — non-land slots are filled greedily in fit order, but:
 *       - **owned cards are preferred** at equal usefulness (owned-first stable partition), and
 *       - a card is only taken for a role while that role is still **below its skeleton ideal**; once a
 *         role is satisfied, further cards for it are deferred. After the gap pass, any remaining slots
 *         up to the format's non-land count are topped up with the best remaining cards (PAYOFF /
 *         SYNERGY / THREAT and anything else), seeds always included first.
 *  6. **Lands** — land slots are reserved ([SeedDeckResult.reservedLandSlots]) but NOT materialized
 *     here: picking specific nonbasic lands is out of scope; the existing builder/basic-land flow fills
 *     the mana base. This keeps the use case focused on the spell selection problem.
 *
 * The result is heuristic and deterministic given the same inputs (no randomness).
 */
class BuildDeckFromSeedsUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    private val roleClassifier: RoleClassifier,
    private val candidatePoolGenerator: CandidatePoolGenerator,
    private val budgetOptimizer: BudgetOptimizer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param seeds the user-picked seed cards (always included in the mainboard, never re-ranked out).
     * @param identity the inferred color identity + strategy + seedTags.
     * @param format the target deck format.
     * @param constraints the active budget filters.
     * @param collection the user's owned cards (one [Card] per distinct scryfallId is enough).
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     */
    suspend operator fun invoke(
        seeds: List<Card>,
        identity: InferredIdentity,
        format: DeckFormat,
        constraints: BudgetConstraints,
        collection: List<Card>,
        weights: ScoreWeights = ScoreWeights(),
    ): SeedDeckResult = withContext(ioDispatcher) {
        val seeds = seeds.distinctBy { it.scryfallId }
        val seedIds = seeds.mapTo(HashSet()) { it.scryfallId }

        // ── 1. Profile (seeds as if already in the deck) ─────────────────────────
        val seedEntries = seeds.map { DeckEntry(card = it, quantity = 1, isOwned = true) }
        val profile = deckScorer.profile(
            mainboard = seedEntries,
            format = format,
            colorIdentity = identity.colorIdentity,
            seedTags = identity.seedTags,
        )
        val evaluation = deckScorer.evaluate(
            profile = profile,
            nonLand = seedEntries.filterNot { BasicLandCalculator.isLand(it.card) },
        )

        // ── 2. Candidate pool ────────────────────────────────────────────────────
        val ownedCandidates = collection
            .filterNot { it.scryfallId in seedIds }
            .distinctBy { it.scryfallId }
        val ownedIds = ownedCandidates.mapTo(HashSet()) { it.scryfallId }

        val usdCap = constraints.maxPerCardEur?.let { it * EUR_TO_USD_LOOSE_FACTOR + 1.0 }
        val externalCards = runCatching {
            candidatePoolGenerator(profile = profile, evaluation = evaluation, usdCap = usdCap)
        }.getOrDefault(emptyList())
            .filterNot { it.scryfallId in seedIds || it.scryfallId in ownedIds }
        val usedExternal = externalCards.isNotEmpty()

        // De-dup the union by id, owned first so an owned printing wins over an external one.
        val unionById = LinkedHashMap<String, Card>()
        (ownedCandidates + externalCards).forEach { unionById.putIfAbsent(it.scryfallId, it) }

        // ── 3. Rank ────────────────────────────────────────────────────────────────
        val ranked: List<CardFit> = deckScorer.rankAdds(
            candidates = unionById.values.toList(),
            profile = profile,
            ownedIds = ownedIds,
            weights = weights,
            limit = RANK_LIMIT,
        )

        // ── 4. Budget filter (owned = free) ─────────────────────────────────────────
        val budgeted = budgetOptimizer(
            suggestions = ranked.map { AddSuggestion(fit = it, origin = originFor(it, ownedIds)) },
            constraints = constraints,
        ).selected

        // ── 5. Fill toward the skeleton ─────────────────────────────────────────────
        // Prefer owned cards at equal usefulness: stable owned-first partition keeps fit order within.
        val ordered = budgeted.sortedWith(
            compareByDescending<AddSuggestion> { it.fit.isOwned }
                .thenByDescending { it.fit.score },
        )

        val targetNonLand = nonLandTarget(format)
        val roleCounts = HashMap<DeckRole, Int>()
        val picked = LinkedHashMap<String, Card>()

        // Gap pass: take a card only while one of its functional roles is still under its ideal.
        for (suggestion in ordered) {
            if (picked.size >= targetNonLand) break
            val card = suggestion.fit.card
            if (card.scryfallId in picked) continue
            val roles = suggestion.fit.roles.filter { it.isFunctional }
            val fillsGap = roles.any { role ->
                val ideal = profile.skeleton.idealFor(role)
                ideal > 0 && (roleCounts[role] ?: 0) < ideal
            }
            if (fillsGap) {
                picked[card.scryfallId] = card
                roles.forEach { roleCounts[it] = (roleCounts[it] ?: 0) + 1 }
            }
        }

        // Top-up pass: fill the remaining non-land slots with the best leftovers (PAYOFF/SYNERGY/etc.).
        for (suggestion in ordered) {
            if (picked.size >= targetNonLand) break
            val card = suggestion.fit.card
            if (card.scryfallId in picked) continue
            picked[card.scryfallId] = card
        }

        // ── Assemble mainboard: seeds first (always), then the picked fills ─────────
        // Seeds report their real ownership (the seed may or may not be in the collection).
        val collectionIds = collection.mapTo(HashSet()) { it.scryfallId }
        val mainboard = buildList {
            seeds.forEach { add(MagicCard(card = it, isOwned = it.scryfallId in collectionIds)) }
            picked.values.forEach { add(MagicCard(card = it, isOwned = it.scryfallId in ownedIds)) }
        }

        SeedDeckResult(
            mainboard = mainboard,
            reservedLandSlots = reservedLandSlots(profile, format),
            usedExternalCandidates = usedExternal,
        )
    }

    /** Non-land mainboard target = format deck size minus the reserved land slots. */
    private fun nonLandTarget(format: DeckFormat): Int =
        (format.targetDeckSize - format.targetLandCount).coerceAtLeast(0)

    /** Land slots reserved for the mana base: skeleton LAND ideal, fallback to the format target. */
    private fun reservedLandSlots(
        profile: com.mmg.manahub.feature.decks.domain.engine.DeckProfile,
        format: DeckFormat,
    ): Int {
        val skeletonIdeal = profile.skeleton.idealFor(DeckRole.LAND)
        return if (skeletonIdeal > 0) skeletonIdeal else format.targetLandCount
    }

    private fun originFor(fit: CardFit, ownedIds: Set<String>): AddOrigin =
        if (fit.card.scryfallId in ownedIds) AddOrigin.COLLECTION else AddOrigin.NEW

    private companion object {
        /** Rank a generous pool so the gap+top-up passes have enough candidates to choose from. */
        const val RANK_LIMIT = 200
        /** Generous EUR→USD multiplier for the loose Scryfall pre-filter (mirrors the budget pipeline). */
        const val EUR_TO_USD_LOOSE_FACTOR = 1.5
    }
}
