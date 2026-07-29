package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ranks the deck's mainboard cards as cut candidates (worst fit first).
 *
 * The BASE ranking is a pure delegation to [DeckScorer.rankCuts]: the engine already excludes
 * lands, the [protectedIds] (typically the commander) and combo cores, and returns the list
 * sorted ascending by fit so the first entries are the strongest cut candidates.
 *
 * ## Workstream 9.5 (Deck Wizard & Engine Rework plan) -- manabase-aware cut penalty
 * On top of that base [CardFit.score] (never forking [DeckScorer]'s own algorithm — mirrors
 * [SuggestAddsFromCollectionUseCase]'s additive-layer-on-top-of-the-engine pattern), a card whose
 * heaviest single-colour pip cost the deck's OWN [ManaBaseAnalyzer]-measured manabase cannot
 * reliably support (`sources < required`) is penalized further and re-sorted — "a card whose pip
 * cost the manabase can't reliably support should rank as a BETTER cut than an equally-fit
 * castable card" (plan WS9.5). A [ScoreReason.UnsupportedPipCost] is attached so the UI can name
 * it ("triple blue is hard in your 4-color base"). A mono/no-color deck (`colorCount < 2`) is a
 * pure pass-through — there is no "shaky multicolor manabase" risk to flag.
 *
 * ## Workstream 8.3 (Deck Wizard & Engine Rework plan) -- gap-aware, simulate-before-suggest cuts
 * Two further additive layers, both gated on a non-null [resolvedSkeleton] (GENERIC/no-themes decks
 * are byte-identical to pre-WS8.3):
 *  - **(a) prefer cuts from OVER-max role bands**: a candidate whose dominant [ArchetypeRoleClassifier]
 *    role is currently running MORE copies than the resolved skeleton's `max` tolerance is penalized
 *    further (same multiplicative-penalty discipline as the pip-cost term above) and gets a
 *    [ScoreReason.OverArchetypeBand] attached ("worst fit among your 9 Board Wipes — Aggro wants at
 *    most 2") — freeing a slot from an already-overstuffed band is preferred over an equally-fit cut
 *    from a band the deck still needs.
 *  - **(b) simulate-before-suggest**: a candidate is DROPPED from the ranking entirely (never merely
 *    demoted) when removing it would push a role the deck is CURRENTLY meeting or exceeding its
 *    `min` for down BELOW that `min` — cutting a card must never recommend breaking a healthy band
 *    just because that specific card also happened to score low on raw fit.
 *
 * The use case stays free of repositories and Card resolution — the ViewModel resolves the
 * mainboard slots to a [DeckEntry] list and reuses the [DeckProfile] already built by
 * [EvaluateDeckUseCase] for the Health view. This keeps it trivially testable.
 */
class SuggestCutsUseCase(
    private val deckScorer: DeckScorer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Appended last (defaulted) so no existing positional-arg call site needs to change --
    // mirrors BuildDeckFromTemplateUseCase's own "Motor B appended last" precedent.
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
) {

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved Card.
     * @param profile the deck profile (reuse the one from [EvaluateDeckUseCase]).
     * @param protectedIds Scryfall ids that must never be suggested for a cut (e.g. the commander).
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param resolvedSkeleton the archetype/theme-resolved skeleton (mirrors
     *   [SuggestAddsFromCollectionUseCase]'s own param), or `null` for GENERIC/no-themes decks --
     *   `null` disables BOTH WS8.3 layers, so a GENERIC deck's cut ranking is byte-identical to
     *   pre-WS8.3 (base fit + WS9.5 manabase penalty only).
     * @return cut candidates sorted ascending by fit (worst first).
     */
    suspend operator fun invoke(
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        protectedIds: Set<String> = emptySet(),
        weights: ScoreWeights = ScoreWeights(),
        resolvedSkeleton: ResolvedArchetypeSkeleton? = null,
    ): List<CardFit> = withContext(ioDispatcher) {
        val baseCuts = deckScorer.rankCuts(
            mainboard = mainboard,
            profile = profile,
            protectedIds = protectedIds,
            weights = weights,
        )

        val colorCount = profile.colorIdentity.count { it != ManaColor.C }
        var cuts = if (colorCount < 2) {
            baseCuts
        } else {
            val report = manaBaseAnalyzer.analyze(mainboard, profile)
            if (report.shortages.isEmpty()) {
                baseCuts
            } else {
                val totalLands = mainboard.filter { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }
                baseCuts.map { fit -> applyManaBasePenalty(fit, report, totalLands) }
            }
        }

        if (resolvedSkeleton != null) {
            val deckRoleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
            cuts = cuts
                .map { fit -> applyOverArchetypeBandPenalty(fit, resolvedSkeleton, deckRoleCounts) }
                .filterNot { fit -> wouldBreakHealthyBand(fit, resolvedSkeleton, deckRoleCounts) }
        }

        cuts.sortedBy { it.score }
    }

    /**
     * A [fit] whose card's OWN heaviest single-colour pip demand isn't reliably supported by the
     * deck's OWN measured sources is penalized toward the bottom of the ranking (a BETTER cut
     * candidate) and gets [ScoreReason.UnsupportedPipCost] attached. Deliberately uses
     * [ManaBaseAnalyzer.requiredSources] keyed on THIS card's OWN pip intensity (single/double/
     * triple+ tier) rather than [ManaBaseAnalyzer.ManaBaseReport.requiredByColor] (which is keyed
     * on the DECK-WIDE heaviest card for that colour) — a single-pip card in a colour whose
     * deck-wide demand is triple-pip (from some OTHER card) must NOT be penalized as if IT were the
     * hard-to-cast one; only a card whose OWN pip cost the measured sources can't reliably support
     * ranks worse. Untouched (returned as-is) when the card has no colour pips, or its own colour
     * intensity is already adequately sourced.
     */
    private fun applyManaBasePenalty(fit: CardFit, report: ManaBaseAnalyzer.ManaBaseReport, totalLands: Int): CardFit {
        val ownEntry = listOf(DeckEntry(card = fit.card, quantity = 1, isOwned = fit.isOwned, isSideboard = false))
        val (color, intensity) = manaBaseAnalyzer.maxSinglePipIntensity(ownEntry).maxByOrNull { it.value } ?: return fit
        if (intensity <= 0) return fit
        val have = report.sourcesByColor[color] ?: 0
        val need = manaBaseAnalyzer.requiredSources(intensity, totalLands)
        if (have >= need) return fit
        val penalized = (fit.score * UNSUPPORTED_PIP_CUT_PENALTY).coerceAtLeast(0f)
        return fit.copy(score = penalized, reasons = fit.reasons + ScoreReason.UnsupportedPipCost(color, intensity))
    }

    /**
     * WS8.3(a) -- a [fit] whose DOMINANT [ArchetypeRoleClassifier] role currently runs MORE copies
     * than the resolved skeleton's `max` for that role is penalized further (same multiplicative
     * discipline as [applyManaBasePenalty]) and gets [ScoreReason.OverArchetypeBand] attached.
     * "Dominant" = the over-max role with the highest `confidence * overageRatio` product (mirrors
     * [SuggestAddsFromCollectionUseCase.themeRoleBonus]'s own "best of several candidate roles"
     * selection pattern). A card that contributes to no over-max role is returned unchanged.
     */
    private fun applyOverArchetypeBandPenalty(
        fit: CardFit,
        resolvedSkeleton: ResolvedArchetypeSkeleton,
        deckRoleCounts: Map<RoleKey, Int>,
    ): CardFit {
        val cardRoles = ArchetypeRoleClassifier.classify(fit.card)
        if (cardRoles.isEmpty()) return fit
        var bestScore = 0f
        var bestKey: RoleKey? = null
        var bestCurrent = 0
        var bestMax = 0
        resolvedSkeleton.roleTargets.forEach { (key, target) ->
            val confidence = cardRoles[key] ?: return@forEach
            if (confidence <= 0f) return@forEach
            val current = deckRoleCounts[key] ?: 0
            if (current <= target.max) return@forEach
            val overageRatio = (current - target.max).toFloat() / target.max.coerceAtLeast(1)
            val score = confidence * overageRatio
            if (score > bestScore) {
                bestScore = score
                bestKey = key
                bestCurrent = current
                bestMax = target.max
            }
        }
        val roleKey = bestKey ?: return fit
        val penalized = (fit.score * OVER_ARCHETYPE_BAND_CUT_PENALTY).coerceAtLeast(0f)
        return fit.copy(
            score = penalized,
            reasons = fit.reasons + ScoreReason.OverArchetypeBand(
                roleKey = roleKey,
                current = bestCurrent,
                max = bestMax,
                planLabel = resolvedSkeleton.planLabel(),
            ),
        )
    }

    /**
     * WS8.3(b) -- simulate-before-suggest: true when cutting ONE copy of [fit]'s card would push
     * ANY role it contributes to from currently meeting/exceeding the resolved skeleton's `min`
     * down BELOW that `min`. Approximates "one copy's worth" of a role's contribution as
     * `roundToInt(confidence)` (a card whose classifier confidence for a role is `< 0.5` rounds to
     * a zero-copy contribution, so cutting it can never be blamed for breaking that specific role
     * -- consistent with [ArchetypeRoleClassifier.deckRoleCounts]'s own `quantity * confidence`
     * rounding). Cards contributing to NO role, or to only roles the skeleton does not track at
     * `min > 0`, never trigger this guard.
     */
    private fun wouldBreakHealthyBand(
        fit: CardFit,
        resolvedSkeleton: ResolvedArchetypeSkeleton,
        deckRoleCounts: Map<RoleKey, Int>,
    ): Boolean {
        val cardRoles = ArchetypeRoleClassifier.classify(fit.card)
        if (cardRoles.isEmpty()) return false
        return resolvedSkeleton.roleTargets.any { (key, target) ->
            if (target.min <= 0) return@any false
            val confidence = cardRoles[key] ?: return@any false
            val contribution = kotlin.math.round(confidence).toInt()
            if (contribution <= 0) return@any false
            val current = deckRoleCounts[key] ?: 0
            val afterCut = current - contribution
            current >= target.min && afterCut < target.min
        }
    }

    private companion object {
        /** Multiplicative penalty applied to a cut candidate whose own colour the manabase can't
         * reliably support -- pushes it toward the front of the (ascending-by-score) cut ranking
         * without ever forcing it to rank #1 regardless of everything else (a soft nudge, same
         * discipline as [SuggestAddsFromCollectionUseCase]'s pip-intensity multiplier). */
        const val UNSUPPORTED_PIP_CUT_PENALTY = 0.5f

        /** WS8.3(a) -- same soft-nudge discipline as [UNSUPPORTED_PIP_CUT_PENALTY], applied when a
         * cut candidate's dominant role is currently over the resolved skeleton's `max`. */
        const val OVER_ARCHETYPE_BAND_CUT_PENALTY = 0.5f
    }
}
