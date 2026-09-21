package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-20

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.DeckCard

/**
 * The one basic-land COUNT planner — Stage B (pip-weighted distribution) + Stage C (bounded Karsten
 * rebalance) — shared by [com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase]'s
 * `fillLandsV2` (Stage A, owned non-basic lands, stays there untouched) and
 * [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel]'s land-delta suggestion, so Studio
 * always agrees with what the wizard actually placed (Deck Wizard UX polish plan, Run 1 §1.1).
 *
 * Deliberately pure counts, no [com.mmg.manahub.core.model.Card] resolution — resolving a colour's
 * basic-land name to a real, owned `Card` stays each caller's own materialization concern (the wizard
 * resolves against its `ownedCollection`; Studio resolves however it needs to for a UI delta).
 */
object BasicLandPlanner {

    /** Mirrors the pre-extraction Stage C cap — kept in lockstep here, not re-derived per caller. */
    const val KARSTEN_REBALANCE_CAP = 5

    /**
     * @param identity the deck's colour identity (Commander identity, or the Sixty anchor's own
     *   identity / Studio's derived identity) — restricts which WUBRG colours basics may be assigned
     *   to; an EMPTY identity means every basic slot becomes Wastes
     *   ([BasicLandCalculator.calculateFromPips]'s own empty-identity/no-weight behaviour).
     * @param landTarget the land slots basics have LEFT to fill, i.e. the deck's overall land target
     *   minus any manual/non-basic lands already accounted for outside this planner — NOT the deck's
     *   raw land target. Matches [BasicLandCalculator.calculateFromPips]'s own `totalLandTarget`,
     *   which further subtracts `nonBasicLands.sumOf { it.quantity }` internally.
     * @param nonLandMainboard every nonland mainboard entry the deck's pip demand should be read from,
     *   INCLUDING a synthetic 1-copy commander entry when there is a commander (omit entirely for a
     *   commander-less build) — feeds [ManaBaseAnalyzer.pipDistribution] /
     *   [ManaBaseAnalyzer.maxSinglePipIntensity].
     * @param nonBasicLands non-basic lands already placed (the wizard's Stage A output, or Studio's
     *   persisted non-basic mainboard lands) — consumed both by [BasicLandCalculator.calculateFromPips]
     *   (slot math) and by this planner's own Stage C source count (via
     *   [ManaBaseAnalyzer.producedColors]).
     * @return basic-land COUNTS keyed by [ManaColor], always carrying all 6 keys in canonical
     *   `ManaColor.entries` order (W, U, B, R, G, C) regardless of internal computation order, so a
     *   materializing caller gets a deterministic Plains/Island/Swamp/Mountain/Forest/Wastes sequence.
     */
    fun planBasics(
        identity: Set<ManaColor>,
        landTarget: Int,
        nonLandMainboard: List<DeckEntry>,
        nonBasicLands: List<DeckCard>,
        manaBaseAnalyzer: ManaBaseAnalyzer,
    ): Map<ManaColor, Int> {
        val identitySymbols = identity.map { it.symbol }.toSet()

        // ── Stage B: commander/mainboard-pip-weighted basic distribution ───────────────────────
        val pipsRaw = manaBaseAnalyzer.pipDistribution(nonLandMainboard)
        val pipsByColor = pipsRaw.entries.associate { (color, count) -> color.symbol to count }
        val distribution = BasicLandCalculator.calculateFromPips(
            pipsByColor = pipsByColor,
            nonBasicLands = nonBasicLands,
            totalLandTarget = landTarget,
            commanderIdentity = identitySymbols,
        )
        val basicCounts = mutableMapOf(
            ManaColor.W to distribution.plains,
            ManaColor.U to distribution.islands,
            ManaColor.B to distribution.swamps,
            ManaColor.R to distribution.mountains,
            ManaColor.G to distribution.forests,
            ManaColor.C to distribution.wastes,
        )

        // ── Stage C: bounded Karsten rebalance, moving basic COUNTS only (never a non-basic
        //    source) between the most-oversupplied and most-undersupplied colour ────────────────
        val intensity = manaBaseAnalyzer.maxSinglePipIntensity(nonLandMainboard)
        val nonBasicSources = mutableMapOf<ManaColor, Int>()
        nonBasicLands.forEach { deckCard ->
            manaBaseAnalyzer.producedColors(deckCard.card, identity).intersect(identity).forEach { color ->
                nonBasicSources[color] = (nonBasicSources[color] ?: 0) + deckCard.quantity
            }
        }
        val sources = nonBasicSources.toMutableMap()
        basicCounts.forEach { (color, count) -> sources[color] = (sources[color] ?: 0) + count }
        val totalLands = nonBasicLands.sumOf { it.quantity } + basicCounts.values.sum()

        var moves = 0
        while (moves < KARSTEN_REBALANCE_CAP) {
            val shortages = intensity.mapNotNull { (color, need) ->
                if (need <= 0) return@mapNotNull null
                val required = manaBaseAnalyzer.requiredSources(need, totalLands)
                val have = sources[color] ?: 0
                if (have < required) color to (required - have) else null
            }
            if (shortages.isEmpty()) break
            val shortColor = shortages.maxByOrNull { it.second }?.first ?: break
            val excessColor = sources.entries
                .filter { (color, count) ->
                    color != shortColor && count > (intensity[color]?.let { manaBaseAnalyzer.requiredSources(it, totalLands) } ?: 0)
                }
                .maxByOrNull { it.value }?.key ?: break
            // A basic-land copy of the excess colour must actually exist to move — an excess
            // coming entirely from a non-basic source can't be redistributed this way, matching
            // the pre-extraction rebalance's own abort-on-no-basic-entry behaviour.
            if ((basicCounts[excessColor] ?: 0) <= 0) break

            basicCounts[excessColor] = (basicCounts[excessColor] ?: 0) - 1
            sources[excessColor] = (sources[excessColor] ?: 1) - 1
            basicCounts[shortColor] = (basicCounts[shortColor] ?: 0) + 1
            sources[shortColor] = (sources[shortColor] ?: 0) + 1
            moves++
        }

        return ManaColor.entries.associateWith { basicCounts[it] ?: 0 }
    }
}
