package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry

/**
 * Wizard Quality Campaign Wave 2 (trim-excess): [BuildDeckFromTemplateUseCase.computeLandTarget] is
 * called with a DIFFERENT mainboard snapshot at each of its two call sites — the pre-top-up planning
 * estimate vs. `fillLands`'s own real materialization — so the two are not guaranteed monotonic and
 * the REAL total (nonland + lands) can land a card or two OVER the format target with nothing
 * upstream to pull it back down (the "61/60" bug, ~4/30 Casual harness specs). This is the safety-net
 * trim: a pure, standalone function so it is directly unit-testable without needing a full build
 * pipeline (no [com.mmg.manahub.feature.decks.domain.engine.DeckScorer]/`DeckTemplate`/coroutine
 * dependency — the caller supplies the fit score as a plain lambda).
 *
 * Extracted as a top-level `internal` object (rather than a private method) specifically so
 * `commonTest` can exercise the trim math directly with a small synthetic fixture, independent of
 * [BuildDeckFromTemplateUseCase]'s land-target non-monotonicity (which is what triggers overshoot in
 * production but is not itself what this function needs to reproduce for a test).
 */
internal object MainboardTrimmer {

    /**
     * Removes the WORST-fit surplus [entries] — ranked by [scoreOf], ascending (worst first), tied
     * by name then scryfallId for determinism — until [overshootCount] copies have been removed or
     * the trimmable pool (entries not in [protectedNames]/[protectedIds]) is exhausted.
     *
     * Never touches a protected entry (the wizard's own explicit seeds, by name, and the commander,
     * by scryfallId — defensive even though the commander is never placed as a mainboard [DeckEntry]
     * to begin with, see [BuildDeckFromTemplateUseCase.mainboardTargetSize]'s KDoc). Trimming only
     * ever REDUCES a placed quantity or removes an entry outright, so it can never violate the
     * "never place more copies than owned" invariant that governs PLACEMENT — there is nothing to
     * additionally guard here.
     *
     * @return [entries] unchanged when [overshootCount] <= 0, otherwise a new list with up to
     *   [overshootCount] total copies removed from the trimmable pool (fewer than [overshootCount]
     *   when that pool is smaller than the overshoot — e.g. an all-seeds/all-protected mainboard).
     */
    fun trim(
        entries: List<DeckEntry>,
        protectedNames: Set<String>,
        protectedIds: Set<String>,
        overshootCount: Int,
        scoreOf: (Card) -> Float,
    ): List<DeckEntry> {
        if (overshootCount <= 0) return entries

        var remaining = overshootCount
        val result = entries.toMutableList()
        val trimmable = entries
            .filterNot { it.card.name in protectedNames || it.card.scryfallId in protectedIds }
            .sortedWith(
                compareBy<DeckEntry> { scoreOf(it.card) }
                    .thenBy { it.card.name }
                    .thenBy { it.card.scryfallId }
            )

        for (candidate in trimmable) {
            if (remaining <= 0) break
            val index = result.indexOfFirst { it.card.scryfallId == candidate.card.scryfallId }
            if (index < 0) continue
            val entry = result[index]
            val removeQty = minOf(entry.quantity, remaining)
            if (removeQty >= entry.quantity) {
                result.removeAt(index)
            } else {
                result[index] = entry.copy(quantity = entry.quantity - removeQty)
            }
            remaining -= removeQty
        }
        return result
    }
}
