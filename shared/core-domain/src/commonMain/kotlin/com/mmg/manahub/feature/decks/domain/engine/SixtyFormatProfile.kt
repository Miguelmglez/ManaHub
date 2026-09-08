package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat

/**
 * Per-DeckFormat modulation over the shared SIXTY skeleton. Applied by [ArchetypeSkeletonResolver
 * .resolveWithColor] AFTER archetype+theme+color(count+identity) resolution — the same layering
 * position color modulation already occupies (see that file's KDoc). Every 60-card constructed
 * [DeckFormat] (STANDARD, MODERN, PIONEER, LEGACY, VINTAGE, PAUPER) now has its own calibrated
 * entry (Wave 2 covered STANDARD; this pass — "Wave 2 future-debt closeout", 2026-09-06 — covers
 * the remaining five). CASUAL and DRAFT intentionally have none and fall through to PASSTHROUGH
 * (zero deltas) -- Casual has no format-specific metagame to calibrate against, and Draft never
 * reaches this layer at all ([ArchetypeFormat.of] returns `null` for it). This remains one of only
 * TWO DeckFormat-keyed extension points in the whole engine — everything else stays
 * ArchetypeFormat-keyed; do not add more DeckFormat-keyed tables without updating that contract
 * (see [CuratedStrategy.formats]'s own KDoc for the other one).
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

        /**
         * JUDGMENT CALL (2026-09-06, Wave 2 future-debt closeout): deliberately PASSTHROUGH
         * (0, 0.0), not a missing entry -- an explicit finding, not an oversight. STANDARD's own
         * KDoc above already establishes that the generic SIXTY/AGGRO baseline (lands 19-21-23,
         * curve 1.4-1.8-2.2) "reads Modern-ish", i.e. it was calibrated FROM Modern's own
         * efficiency in the first place. General guidance cross-checked live today (TCGplayer "How
         * Many Lands Do You Need in Your Deck? An Updated Analysis", 2026; draftsim.com "How Many
         * Lands Should You Really Play in A 60-Card Deck?") pegs a Modern aggro-control shell at
         * ~22-24 lands and pure aggro at ~20-22 -- both land inside the existing unmodulated
         * archetype bands (e.g. generic AGGRO's own 19-21-23), so no additional blanket shift is
         * warranted on top of them. Kept as an explicit map entry (rather than omitted) so a future
         * reader sees "Modern was evaluated and found to need no shift" instead of mistaking the
         * gap for unfinished work.
         */
        val MODERN = SixtyFormatProfile(landsDelta = 0, curveDelta = 0.0)

        /**
         * JUDGMENT CALL (2026-09-06): Pioneer sits between Modern and Standard in raw power and
         * mana efficiency -- it has access to shocklands/fastlands (unlike Standard) but not
         * Modern's full fetch-land/1-mana-interaction suite. draftsim.com's "How Many Lands Should
         * You Really Play in A 60-Card Deck?" format-agnostic baseline (aggro 20-22, control 26-28)
         * and the general "40% of the deck, ~24 lands in 60" heuristic (TCGplayer, cross-checked
         * live 2026-09) both sit slightly above the Modern-calibrated generic baseline without
         * reaching Standard's own +2 shift. HALF of Standard's blanket shift -- +1 land / +0.15
         * avg-CMC -- is a deliberate, documented BLANKET judgment call placing Pioneer at the
         * midpoint between MODERN (0, 0.0) and STANDARD (+2, +0.3), same discipline STANDARD's own
         * entry used; no dedicated Pioneer-only land-count study was found to derive a sharper
         * number from.
         */
        val PIONEER = SixtyFormatProfile(landsDelta = 1, curveDelta = 0.15)

        /**
         * JUDGMENT CALL (2026-09-06): Legacy's efficient 1-mana interaction (Force of Will, Swords
         * to Plowshares), true dual lands, and fetch lands let decks run LEANER manabases than the
         * Modern-calibrated generic baseline -- cross-checked live (vintageisthenewold.com "How
         * many dual lands in a 60 card deck?", 2026; draftsim.com's dual/fetch-land guidance) which
         * both describe Legacy manabases as deliberately thinner than newer-format equivalents
         * because of how low Legacy curves run (cheap, efficient staples cap out around 2-3 mana
         * for most non-control shells). -1 land / -0.2 avg-CMC is a deliberate BLANKET shift below
         * the MODERN (0, 0.0) baseline -- not derived from a single per-archetype published number
         * (no source splits Legacy land counts by archetype the way draftsim's aggro/control split
         * does for newer formats), same judgment-call discipline as every other entry here.
         */
        val LEGACY = SixtyFormatProfile(landsDelta = -1, curveDelta = -0.2)

        /**
         * JUDGMENT CALL (2026-09-06): Vintage pushes the Legacy trend further -- Power Nine
         * (Black Lotus, the Moxen) and fast-mana artifacts (Sol Ring, Mana Crypt) let decks operate
         * on fewer actual lands than any other constructed format, and Workshop decks lean on
         * artifact mana instead of lands almost entirely. vintageisthenewold.com's "How many dual
         * lands in a 60 card deck?" (cross-checked live 2026-09) explicitly frames Vintage as
         * running the leanest manabases in the game, thinner even than Legacy's. -2 lands / -0.3
         * avg-CMC is a deliberate BLANKET shift, one full step below LEGACY's own (-1, -0.2) --
         * mirrors that entry's judgment-call discipline; no per-archetype Vintage land-count study
         * exists to derive a sharper number from.
         */
        val VINTAGE = SixtyFormatProfile(landsDelta = -2, curveDelta = -0.3)

        /**
         * JUDGMENT CALL (2026-09-06): Pauper's own [DeckFormat.PAUPER] `targetLandCount` (23, vs.
         * 24 for every other 60-card format) already reflects the format's real character in this
         * codebase -- commons-only removal/fixing is weaker than at higher rarities, so decks want
         * slightly MORE lands than the Modern-calibrated generic baseline for color consistency
         * despite running a low curve overall (thegamer.com "How To Build Your First Pauper Deck
         * In MTG", cross-checked live 2026-09: "20-23 lands is the typical range... curve as low as
         * 2 mana... most Pauper decks"). +1 land / -0.1 avg-CMC is a deliberate BLANKET shift --
         * slightly MORE lands than Modern's baseline but a LOWER curve than Modern's, reflecting
         * "more lands to hit fixing, cheaper spells overall" rather than either pure aggro or pure
         * control skew; not derived from a per-archetype published source.
         */
        val PAUPER = SixtyFormatProfile(landsDelta = 1, curveDelta = -0.1)

        private val BY_FORMAT: Map<DeckFormat, SixtyFormatProfile> = mapOf(
            DeckFormat.STANDARD to STANDARD,
            DeckFormat.MODERN to MODERN,
            DeckFormat.PIONEER to PIONEER,
            DeckFormat.LEGACY to LEGACY,
            DeckFormat.VINTAGE to VINTAGE,
            DeckFormat.PAUPER to PAUPER,
        )

        /** [DeckFormat.CASUAL] and [DeckFormat.DRAFT] (and any future format with no entry above)
         * return PASSTHROUGH (all-zero deltas) -- see class KDoc for why. */
        fun forDeckFormat(format: DeckFormat): SixtyFormatProfile = BY_FORMAT[format] ?: SixtyFormatProfile()
    }
}
