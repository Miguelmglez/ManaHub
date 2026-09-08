package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/** One rankable [StrategyProfile] candidate the seeds support, with a human-readable [label] and a
 * 0f..1f [fitScore] (higher = better identity-tag overlap with the seeds).
 *
 * @property misfitSeeds Deck Wizard & Engine Rework plan, Workstream 3.1 (coherence hints) — the
 *   seeds among [SuggestStrategiesForSeedsUseCase]'s input whose OWN identity-key fingerprint has
 *   ZERO overlap with this candidate's tag set (i.e. seeds this specific candidate doesn't actually
 *   fit). Non-empty renders a "Better without: …" hint on the candidate — the candidate is NEVER
 *   hidden for having misfits, it still ranks and renders; removing one of these seeds and
 *   re-invoking the use case should raise (or at least not lower) this candidate's coherence with
 *   the remaining seeds.
 * @property misfitColors Same idea for the currently selected colors (this use case's own
 *   `selectedColors` param): colors picked that fall outside this candidate's BEST curated
 *   [ColorStrategyAffinity] combo. Always empty when no color is selected yet, or when the
 *   candidate has no curated color data to compare against at all (never guessed/invented).
 */
data class SeedStrategyCandidate(
    val profile: StrategyProfile,
    val label: String,
    val fitScore: Float,
    val misfitSeeds: List<Card> = emptyList(),
    val misfitColors: Set<ManaColor> = emptySet(),
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

    /**
     * @param selectedColors Deck Wizard & Engine Rework plan, Workstream 3.1 — the color set the
     *   user has picked so far in Flow A's direction step (may be empty before any pick). Feeds
     *   [SeedStrategyCandidate.misfitColors] only; never hides a candidate for a color mismatch.
     */
    operator fun invoke(seeds: List<Card>, selectedColors: Set<ManaColor> = emptySet()): SeedStrategySuggestion {
        if (seeds.isEmpty()) return SeedStrategySuggestion(isCoherent = true, coherenceScore = 1f, candidates = emptyList())

        val seedKeysByCard = seeds.map { it to seedIdentityKeys(it) }
        val coherenceScore = pairwiseCoherence(seedKeysByCard.map { it.second })
        val isCoherent = seeds.size < 2 || coherenceScore >= COHERENCE_THRESHOLD
        val unionKeys = seedKeysByCard.flatMap { it.second }.toSet()

        // WS3.1 -- seeds whose own identity fingerprint shares NOTHING with candidateKeys (i.e. this
        // specific candidate doesn't fit them at all). A candidate with no candidateKeys (shouldn't
        // happen for a real archetype/theme/tribe key, but defensive) has no misfits by definition.
        fun misfitSeedsFor(candidateKeys: Set<String>): List<Card> =
            if (candidateKeys.isEmpty()) emptyList()
            else seedKeysByCard.filter { (_, keys) -> keys.intersect(candidateKeys).isEmpty() }.map { it.first }

        // WS3.1 -- colors picked that fall outside this candidate's BEST curated ColorStrategyAffinity
        // combo (highest Jaccard overlap with selectedColors; ties keep the highest-weight combo,
        // since combosFor already returns its list weight-descending and maxByOrNull keeps the first
        // maximal element). Empty selectedColors or zero curated combos both mean "nothing to flag".
        fun misfitColorsFor(archetype: ArchetypeId?, theme: ThemeId?): Set<ManaColor> {
            if (selectedColors.isEmpty()) return emptySet()
            val combos = ColorStrategyAffinity.combosFor(archetype, theme)
            if (combos.isEmpty()) return emptySet()
            val best = combos.maxByOrNull { (colors, _) -> jaccard(colors, selectedColors) } ?: return emptySet()
            return selectedColors - best.first
        }

        // Deck Analysis Engine v3 removed ArchetypeId.GENERIC -- every entries value is now a real,
        // specialized macro, so no filter is needed (was previously excluding the neutral default).
        val archetypeCandidates = ArchetypeId.entries
            .mapNotNull { archetype ->
                val keys = DeckIdentitySeedTags.archetypeSeedTags(archetype).map { it.key }.toSet()
                val fit = overlapScore(unionKeys, keys)
                if (fit <= 0f) return@mapNotNull null
                SeedStrategyCandidate(
                    profile = StrategyProfile(archetype = archetype),
                    label = archetype.displayName,
                    fitScore = fit,
                    misfitSeeds = misfitSeedsFor(keys),
                    misfitColors = misfitColorsFor(archetype, null),
                )
            }

        val themeCandidates = ThemeId.entries.mapNotNull { theme ->
            val keys = DeckIdentitySeedTags.themeSeedTags(listOf(theme)).map { it.key }.toSet()
            val fit = overlapScore(unionKeys, keys)
            if (fit <= 0f) return@mapNotNull null
            SeedStrategyCandidate(
                profile = StrategyProfile(themes = listOf(theme)),
                label = theme.displayName,
                fitScore = fit,
                misfitSeeds = misfitSeedsFor(keys),
                misfitColors = misfitColorsFor(null, theme),
            )
        }

        // Tribe candidates are derived straight from the seeds themselves (there is no fixed tribe
        // enum to rank against) -- a subtype shared by more than one seed (or the seed's own type
        // when there is only one seed) becomes a pickable tribe candidate. WS3.1 fix: the profile now
        // ALSO carries `themes = [ThemeId.TRIBAL]` -- StrategyCatalog.isValidCombination (added by
        // WS1, after this use case was first written) rejects a bare tribe pick with no TRIBAL theme
        // as an orphaned pick, and this candidate was never updated to match. Without this, every
        // tribe candidate failed the WS3.1-mandated isValidCombination filter below and silently
        // vanished from the list.
        val tribeCandidates = seeds
            .flatMap { TribeDeriver.subtypeKeys(it) }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) ->
                val label = key.removePrefix(TribeDeriver.TRIBE_PREFIX).replaceFirstChar { it.uppercase() }
                SeedStrategyCandidate(
                    profile = StrategyProfile(themes = listOf(ThemeId.TRIBAL), tribe = key),
                    label = label,
                    fitScore = count.toFloat() / seeds.size,
                    misfitSeeds = seeds.filterNot { key in TribeDeriver.subtypeKeys(it) },
                    misfitColors = misfitColorsFor(null, ThemeId.TRIBAL),
                )
            }

        val candidates = (archetypeCandidates + themeCandidates + tribeCandidates)
            // WS3.1 -- every returned candidate must be a combination the shared taxonomy actually
            // accepts (StrategyCatalog.isValidCombination, WS1); this is a defensive backstop (no
            // known live case currently produces an invalid archetype/theme-only candidate) that
            // exists specifically because the tribe-candidate fix above closes the one case that DID
            // violate it.
            .filter { StrategyCatalog.isValidCombination(it.profile.archetype, it.profile.themes, it.profile.tribe) }
            .sortedWith(compareByDescending<SeedStrategyCandidate> { it.fitScore }.thenBy { it.label })
            .take(MAX_CANDIDATES)

        return SeedStrategySuggestion(isCoherent, coherenceScore, candidates)
    }

    private fun jaccard(a: Set<ManaColor>, b: Set<ManaColor>): Float {
        val union = (a + b).size
        return if (union == 0) 1f else (a intersect b).size.toFloat() / union
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
