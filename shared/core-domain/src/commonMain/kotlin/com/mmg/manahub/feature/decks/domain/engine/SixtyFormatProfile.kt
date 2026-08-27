package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat

/**
 * Per-DeckFormat modulation over the shared SIXTY skeleton. Applied by [ArchetypeSkeletonResolver
 * .resolveWithColor] AFTER archetype+theme+color(count+identity) resolution — the same layering
 * position color modulation already occupies (see that file's KDoc). Only STANDARD has a
 * non-default entry — Modern/Pioneer/Pauper/Legacy/Vintage are FUTURE DEBT and MUST default to
 * PASSTHROUGH (zero deltas) until each gets its own calibrated entry (Wave 2 plan §B0: this is
 * one of only TWO DeckFormat-keyed extension points in the whole engine — everything else stays
 * ArchetypeFormat-keyed; do not add more DeckFormat-keyed tables without updating that contract).
 */
data class SixtyFormatProfile(
    val landsDelta: Int = 0,          // shifts min/ideal/max together
    val curveDelta: Double = 0.0,     // shifts the CurveBand
) {
    companion object {
        /**
         * JUDGMENT CALL (2026-08-20, Wave 2 B2): the generic SIXTY skeleton reads Modern-ish
         * (ArchetypeData.kt AGGRO: lands 19-21-23, curve 1.4-1.8-2.2). Standard is slower and
         * less mana-efficient than Modern -- it lacks Modern's efficient 1-mana interaction/fetch
         * lands/cheap value staples. General 60-card constructed guidance (draftsim.com "How Many
         * Lands Should You Really Play in a 60-Card Deck?") pegs aggro at 20-22 lands and control
         * at 26-28 lands as FORMAT-AGNOSTIC baselines (the article explicitly does NOT split by
         * format) -- cross-checked against current (Aug 2026) Standard decklists on mtggoldfish.com
         * or mtgdecks.net for mono-red/mono-white aggro (~22-23 lands) and Azorius control
         * (~26-27 lands), both landing inside this shifted band (mtggoldfish's archetype pages
         * returned 403 to automated fetch during this pass -- the cross-check could not be
         * independently re-confirmed live; numbers kept as documented from the prior pass since
         * they are internally consistent with the draftsim.com baseline and not contradicted by
         * anything found). +2 lands / +0.3 avg-CMC is a deliberate BLANKET shift applied across
         * every archetype (not derived from a per-archetype published source) -- same WS5
         * judgment-call discipline Wave 1 used for its weights/cap.
         *
         * Deck Analysis Engine v3, Phase 3 (2026-08-26): the dead `roleBandScale` field (declared,
         * never applied by [ArchetypeSkeletonResolver]) was REMOVED rather than wired -- deriving a
         * real per-format role-band scale factor would be a genuine new calibration exercise (no
         * existing citation covers it), out of this phase's scope; leaving it declared-but-unused
         * was flagged as a trap for the next reader, so it is gone rather than left in place.
         */
        val STANDARD = SixtyFormatProfile(landsDelta = 2, curveDelta = 0.3)

        private val BY_FORMAT: Map<DeckFormat, SixtyFormatProfile> = mapOf(DeckFormat.STANDARD to STANDARD)

        /** Every [DeckFormat] besides STANDARD (including CASUAL, which is already live) returns
         * PASSTHROUGH (all-zero deltas) -- see class KDoc for why. */
        fun forDeckFormat(format: DeckFormat): SixtyFormatProfile = BY_FORMAT[format] ?: SixtyFormatProfile()
    }
}
