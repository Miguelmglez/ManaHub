package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import javax.inject.Inject

/**
 * Inferred deck identity derived purely from a set of seed cards.
 *
 * @property colorIdentity union of the seeds' WUBRG color identities (C / unknown symbols dropped).
 * @property strategy the [SeedStrategy] whose `primaryTags` best overlap the seeds' tags, or null when
 *           no archetype matches at all (the seeds carry no recognizable strategy/tribal signal).
 * @property seedTags the strategy fingerprint to seed the scorer with: the matched strategy's
 *           `primaryTags` unioned with the seeds' own STRATEGY / ARCHETYPE / TRIBAL tags.
 */
data class InferredIdentity(
    val colorIdentity: Set<ManaColor>,
    val strategy: SeedStrategy?,
    val seedTags: List<CardTag>,
)

/**
 * Pure, dependency-free inference of a deck's color identity + archetype from 1+ seed cards.
 *
 * ## Algorithm (heuristic — the user always reviews the result)
 *  1. **Color identity** = the union of every seed's [Card.colorIdentity], mapping the five WUBRG
 *     letters to [ManaColor]; "C" and any unknown symbol are dropped (so an empty result means "no
 *     color restriction", consistent with [com.mmg.manahub.feature.decks.domain.engine.DeckScorer]).
 *  2. **Strategy** = the [SeedStrategy] whose `primaryTags` have the highest overlap (counted by tag
 *     [CardTag.key]) with the union of the seeds' `tags` + `userTags`. Ties break on
 *     [SeedStrategy] declaration order (the `maxByOrNull` keeps the first max). Zero overlap → null.
 *  3. **seedTags** = the matched strategy's `primaryTags` ∪ the seeds' own tags whose category is
 *     STRATEGY / ARCHETYPE / TRIBAL (the identity categories the scorer fingerprints on). De-duplicated
 *     by [CardTag.key]. With no strategy, only the seeds' identity tags are returned.
 *
 * Empty seeds yield an empty identity, null strategy and empty seedTags — a safe no-op.
 */
class InferDeckIdentityUseCase @Inject constructor() {

    operator fun invoke(seeds: List<Card>): InferredIdentity {
        if (seeds.isEmpty()) {
            return InferredIdentity(colorIdentity = emptySet(), strategy = null, seedTags = emptyList())
        }

        val colorIdentity = seeds
            .flatMap { it.colorIdentity }
            .mapNotNull(::symbolToColor)
            .toSet()

        // ALL the seeds' tags (any category) drive strategy matching: SeedStrategy.primaryTags mixes
        // ARCHETYPE/STRATEGY tags with ROLE tags (e.g. CONTROL pairs with COUNTERSPELL/WRATH/REMOVAL),
        // so restricting the match to identity categories alone would miss those signals.
        val allSeedTags = seeds.flatMap { it.tags + it.userTags }
        val allSeedTagKeys = allSeedTags.mapTo(HashSet()) { it.key }

        // Strategy = best overlap of primaryTags against the seeds' tags (by key). 0 overlap → null.
        val strategy = SeedStrategy.entries
            .map { it to it.primaryTags.count { tag -> tag.key in allSeedTagKeys } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first

        // seedTags (the scorer fingerprint) = strategy primaryTags ∪ the seeds' own STRATEGY /
        // ARCHETYPE / TRIBAL tags (the identity categories the scorer fingerprints on), de-duped by key.
        val seedIdentityTags = allSeedTags.filter { it.category in IDENTITY_CATEGORIES }
        val seedTags = LinkedHashMap<String, CardTag>()
        strategy?.primaryTags?.forEach { seedTags.putIfAbsent(it.key, it) }
        seedIdentityTags.forEach { seedTags.putIfAbsent(it.key, it) }

        return InferredIdentity(
            colorIdentity = colorIdentity,
            strategy = strategy,
            seedTags = seedTags.values.toList(),
        )
    }

    /** Maps a single color symbol to a [ManaColor]; only WUBRG count, everything else is dropped. */
    private fun symbolToColor(symbol: String): ManaColor? = when (symbol.uppercase()) {
        ManaColor.W.symbol -> ManaColor.W
        ManaColor.U.symbol -> ManaColor.U
        ManaColor.B.symbol -> ManaColor.B
        ManaColor.R.symbol -> ManaColor.R
        ManaColor.G.symbol -> ManaColor.G
        else -> null
    }

    private companion object {
        /** Categories that define a deck's strategic fingerprint (same set the scorer uses). */
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)
    }
}
