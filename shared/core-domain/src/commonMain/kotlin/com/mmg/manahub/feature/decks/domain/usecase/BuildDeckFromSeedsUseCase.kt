package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.MagicCard
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One community-aggregate card name + its relative pull weight (Deck Doctor Community/Archetype
 * plan, Phase 5). Deliberately name+weight ONLY (no [com.mmg.manahub.core.model.Card] resolution
 * inside this use case) — the caller (which already resolved the aggregate for the seeds'
 * commander/signature via [com.mmg.manahub.core.domain.repository.CommunityAggregateRepository])
 * supplies this as a pure PRIORITY signal over the candidate pool this use case already builds; see
 * [BuildDeckFromSeedsUseCase]'s class KDoc "Community priority" section.
 */
data class WeightedCardName(val name: String, val weight: Float)

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
 * @property usedCommunityPool true when a non-empty `communityPool` was supplied AND at least one of
 *           its cards was actually available to prioritize (Phase 5).
 */
data class SeedDeckResult(
    val mainboard: List<MagicCard>,
    val reservedLandSlots: Int,
    val usedExternalCandidates: Boolean,
    val usedCommunityPool: Boolean = false,
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
 * ## Community priority (Deck Doctor Community/Archetype plan, Phase 5)
 * An optional [WeightedCardName] list — the seeds' community-aggregate cards, resolved by the
 * CALLER before this use case runs (see [WeightedCardName]'s KDoc for why resolution stays outside
 * this use case) — RE-ORDERS the already-ranked-and-budgeted candidate list (step 4's output)
 * before the step-5 fill passes, per the plan's exact priority: (1) community-pool cards the user
 * ALREADY OWNS, (2) community-pool cards outside the collection, (3) the existing
 * owned-first/best-fit heuristic order, UNCHANGED, for everything else. This never widens the
 * candidate pool itself (a community-pool card that never made it into `unionById` — e.g. off-color
 * or already-budget-excluded — is simply never prioritized, it does not get a second chance to
 * enter); it only changes FILL ORDER among already-eligible candidates. An empty/default
 * `communityPool` reproduces the pre-Phase-5 fill order exactly (zero behavior change).
 *
 * The result is heuristic and deterministic given the same inputs (no randomness).
 */
class BuildDeckFromSeedsUseCase(
    private val deckScorer: DeckScorer,
    private val roleClassifier: RoleClassifier,
    private val candidatePoolGenerator: CandidatePoolGenerator,
    private val budgetOptimizer: BudgetOptimizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * @param seeds the user-picked seed cards (always included in the mainboard, never re-ranked out).
     * @param identity the inferred color identity + strategy + seedTags.
     * @param format the target deck format.
     * @param constraints the active budget filters.
     * @param collection the user's owned cards (one [Card] per distinct scryfallId is enough).
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param communityPool optional community-aggregate priority signal (Phase 5); see the class
     *        KDoc's "Community priority" section. Empty by default — zero behavior change.
     */
    suspend operator fun invoke(
        seeds: List<Card>,
        identity: InferredIdentity,
        format: DeckFormat,
        constraints: BudgetConstraints,
        collection: List<Card>,
        weights: ScoreWeights = ScoreWeights(),
        communityPool: List<WeightedCardName> = emptyList(),
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
        (ownedCandidates + externalCards).forEach { unionById.getOrPut(it.scryfallId) { it } }

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
        // Phase 5: community-pool cards are prioritized FIRST (owned-in-pool, then pool-outside-
        // collection), ahead of the pre-Phase-5 owned-first/score order — see the class KDoc.
        val communityWeightByName = communityPool.associate { it.name to it.weight }
        val usedCommunityPool = communityWeightByName.isNotEmpty() &&
            budgeted.any { it.fit.card.name in communityWeightByName }
        val ordered = budgeted.sortedWith(
            compareByDescending<AddSuggestion> { communityWeightByName.containsKey(it.fit.card.name) && it.fit.isOwned }
                .thenByDescending { communityWeightByName.containsKey(it.fit.card.name) }
                .thenByDescending { communityWeightByName[it.fit.card.name] ?: 0f }
                .thenByDescending { it.fit.isOwned }
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
            usedCommunityPool = usedCommunityPool,
        )
    }

    /** Non-land mainboard target = format deck size minus the reserved land slots. */
    private fun nonLandTarget(format: DeckFormat): Int =
        (format.targetDeckSize - format.targetLandCount).coerceAtLeast(0)

    /** Land slots reserved for the mana base: skeleton LAND ideal, fallback to the format target. */
    private fun reservedLandSlots(profile: DeckProfile, format: DeckFormat): Int {
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
