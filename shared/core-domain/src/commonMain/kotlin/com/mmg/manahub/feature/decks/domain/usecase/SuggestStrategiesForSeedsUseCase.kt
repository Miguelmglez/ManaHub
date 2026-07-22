package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/** One rankable [StrategyProfile] candidate the seeds support, with a human-readable [label] and a
 * 0f..1f [fitScore] (higher = better identity-tag overlap with the seeds). */
data class SeedStrategyCandidate(
    val profile: StrategyProfile,
    val label: String,
    val fitScore: Float,
)

/** Result of [SuggestStrategiesForSeedsUseCase] — top-level (not nested) so callers can reference it
 * without qualifying through the use case class. */
data class SeedStrategySuggestion(
    /** `false` when the seeds' own identity-tag/tribe fingerprints barely overlap each other —
     * the caller should explain this inline rather than silently ranking (an incoherent seed set
     * still yields [candidates], just with a low-confidence signal). */
    val isCoherent: Boolean,
    /** Average pairwise Jaccard overlap across every seed pair, 0f..1f. A single seed (or zero)
     * is trivially coherent (1f) — there is nothing to disagree with yet. */
    val coherenceScore: Float,
    /** Ranked (best-first) [StrategyProfile] candidates the seeds support; candidates with zero
     * overlap are filtered out entirely (never a dead/inert suggestion). */
    val candidates: List<SeedStrategyCandidate>,
)

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.2, Flow A —
 * seed-first): given the user's hand-picked seed cards (RC5 — the user chooses WHICH exact cards,
 * never a whole synergy group), (a) checks whether the seeds are internally coherent (pairwise
 * identity-tag / tribe overlap) and (b) ranks every [StrategyProfile] candidate (archetype, theme, or
 * a tribe derived straight from the seeds) by how well it fits the seeds, hiding anything with zero
 * overlap. The caller must show [SeedStrategySuggestion.isCoherent] == false as an inline explanation
 * rather than silently proceeding to a build the seeds don't actually support (plan requirement).
 *
 * ## Why a lightweight tag-overlap heuristic, not [com.mmg.manahub.feature.decks.domain.engine
 * .DeckScorer.fit]
 * The full scorer needs a materialized [com.mmg.manahub.feature.decks.domain.engine.DeckProfile]
 * (format, mainboard-so-far, resolved skeleton) that does not exist yet at this point in the wizard
 * — the user hasn't picked a strategy, let alone built anything. A simple identity-tag-key overlap
 * against [DeckIdentitySeedTags]'s SAME per-taxonomy-member tag tables the build/Doctor engine
 * ultimately seeds itself with is enough to rank plausible candidates without inventing a second,
 * divergent scoring formula.
 *
 * Pure, dependency-free, and side-effect-free — easily unit-testable.
 */
class SuggestStrategiesForSeedsUseCase {

    operator fun invoke(seeds: List<Card>): SeedStrategySuggestion {
        if (seeds.isEmpty()) return SeedStrategySuggestion(isCoherent = true, coherenceScore = 1f, candidates = emptyList())

        val seedTagSets = seeds.map { seedIdentityKeys(it) }
        val coherenceScore = pairwiseCoherence(seedTagSets)
        val isCoherent = seeds.size < 2 || coherenceScore >= COHERENCE_THRESHOLD
        val unionKeys = seedTagSets.flatten().toSet()

        val archetypeCandidates = ArchetypeId.entries
            .filter { it != ArchetypeId.GENERIC }
            .mapNotNull { archetype ->
                val keys = DeckIdentitySeedTags.archetypeSeedTags(archetype).map { it.key }.toSet()
                val fit = overlapScore(unionKeys, keys)
                if (fit <= 0f) null else SeedStrategyCandidate(StrategyProfile(archetype = archetype), archetype.displayName, fit)
            }

        val themeCandidates = ThemeId.entries.mapNotNull { theme ->
            val keys = DeckIdentitySeedTags.themeSeedTags(listOf(theme)).map { it.key }.toSet()
            val fit = overlapScore(unionKeys, keys)
            if (fit <= 0f) null else SeedStrategyCandidate(StrategyProfile(themes = listOf(theme)), theme.displayName, fit)
        }

        // Tribe candidates are derived straight from the seeds themselves (there is no fixed tribe
        // enum to rank against) -- a subtype shared by more than one seed (or the seed's own type
        // when there is only one seed) becomes a pickable tribe candidate.
        val tribeCandidates = seeds
            .flatMap { TribeDeriver.subtypeKeys(it) }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) ->
                val label = key.removePrefix(TribeDeriver.TRIBE_PREFIX).replaceFirstChar { it.uppercase() }
                SeedStrategyCandidate(StrategyProfile(tribe = key), label, count.toFloat() / seeds.size)
            }

        val candidates = (archetypeCandidates + themeCandidates + tribeCandidates)
            .sortedWith(compareByDescending<SeedStrategyCandidate> { it.fitScore }.thenBy { it.label })
            .take(MAX_CANDIDATES)

        return SeedStrategySuggestion(isCoherent, coherenceScore, candidates)
    }

    /** A card's identity fingerprint: its own STRATEGY/ARCHETYPE/TRIBAL tags plus its runtime-derived
     * [TribeDeriver] subtype keys (never persisted, mirrors [DeckScorer][com.mmg.manahub.feature
     * .decks.domain.engine.DeckScorer]'s own tribe-key contract). */
    private fun seedIdentityKeys(card: Card): Set<String> =
        (card.tags + card.userTags).filter { it.category in IDENTITY_CATEGORIES }.mapTo(mutableSetOf()) { it.key } +
            TribeDeriver.subtypeKeys(card)

    /** Overlap coefficient: how much of [candidateKeys] is present in the seeds' [unionKeys]. Scoped
     * to the candidate's own (usually small) tag set rather than a symmetric Jaccard so a taxonomy
     * member with few tags isn't unfairly penalized against seeds carrying many unrelated tags. */
    private fun overlapScore(unionKeys: Set<String>, candidateKeys: Set<String>): Float {
        if (candidateKeys.isEmpty()) return 0f
        return unionKeys.intersect(candidateKeys).size.toFloat() / candidateKeys.size
    }

    /** Average pairwise Jaccard overlap across every seed pair. A pair that BOTH carry zero identity
     * keys (`union.isEmpty()`) is scored 1f (trivially coherent), not 0f -- two fully-untagged seeds
     * (e.g. two vanilla French-vanilla creatures) aren't actually in TENSION with each other, they
     * just carry no signal at all; scoring "no data" as "conflicting data" produced the confusing UI
     * state of a "seeds don't cohere" warning showing alongside an empty candidates list for what is
     * really just an under-tagged seed pair. Consistent with this function's own single-seed/
     * zero-seed "nothing to disagree with" precedent (edge-case audit follow-up, RUN 3b QA fix). */
    private fun pairwiseCoherence(tagSets: List<Set<String>>): Float {
        if (tagSets.size < 2) return 1f
        var total = 0f
        var pairs = 0
        for (i in tagSets.indices) {
            for (j in i + 1 until tagSets.size) {
                val union = tagSets[i] union tagSets[j]
                total += if (union.isEmpty()) 1f else (tagSets[i] intersect tagSets[j]).size.toFloat() / union.size
                pairs++
            }
        }
        return if (pairs == 0) 1f else total / pairs
    }

    private companion object {
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /** Tunable heuristic bar, same spirit as `HarnessMetricsCalculator.COHERENCE_SWAP_MARGIN` --
         * small enough that two seeds sharing even ONE meaningful identity tag (e.g. both TOKENS)
         * out of otherwise-disjoint fingerprints still reads as coherent, but zero-overlap seed pairs
         * (e.g. a pure Reanimator piece + a pure Voltron piece) correctly flag as incoherent. */
        const val COHERENCE_THRESHOLD = 0.12f
        const val MAX_CANDIDATES = 8
    }
}
