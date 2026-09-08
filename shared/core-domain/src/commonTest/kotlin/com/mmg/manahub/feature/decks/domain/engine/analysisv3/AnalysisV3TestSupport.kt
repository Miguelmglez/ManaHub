package com.mmg.manahub.feature.decks.domain.engine.analysisv3

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Engine v3, Phase 0 — shared test support for the 17-fixture
//  calibration corpus (docs/plans/deck-analysis-engine-v3-spec.md §9).
//
//  FIXTURE-FORMAT DEVIATION (documented, mirrors DeckAnalysisEngineGoldenTest's own
//  documented deviation): the spec's own wording ("resolve to Card objects through the
//  existing test card-data path used by DeckAnalysisEngineGoldenTest... imported once
//  offline, then commit the resulting JSON") describes a path that does not exist —
//  `DeckAnalysisEngineGoldenTest` hand-builds Card objects via the `card()`/`entry()`
//  helpers in EngineFixtures.kt, there is no JSON-fixture/resource-loading path
//  anywhere in this test suite, and `commonTest` has no portable resource-loading API
//  across JVM+wasmJs. DECISION (recorded in the Phase 0 report, not re-litigated here):
//  author the 17 fixtures as committed Kotlin source under this `analysisv3` package,
//  one file per fixture, following the EXACT `card()`/`entry()`/`withBasics` pattern
//  DeckAnalysisEngineGoldenTest already established. This preserves the actual intent
//  of "committed, offline, deterministic, never fetched at test time" without adding a
//  JSON parser or a resources/ dir this module has no use for anywhere else.
//
//  DENSITY CONVENTION (same accepted deviation as DeckAnalysisEngineGoldenTest's own
//  documented header): each fixture uses ~20-28 DISTINCT real staples (singleton in
//  Commander, up to 4-of in Modern) plus ONE bulk basic-land DeckEntry closing the gap
//  to the legal mainboard size (100 incl. commander / 60). This keeps every fixture
//  legible and auditable while still exercising real per-role signal on every
//  non-land card — the alternative (99/60 hand-typed distinct cards per fixture) would
//  make the corpus effectively unmaintainable and add no additional signal the
//  existing golden/calibration fixtures don't already demonstrate is a land-count
//  artifact, not a correctness concern (P1 calibration is explicitly out of this
//  phase's scope).
// ═══════════════════════════════════════════════════════════════════════════════

/** Shorthand for a `TagCategory.ROLE` tag by key — mirrors DeckAnalysisEngineGoldenTest's private
 * `roleTag` helper (duplicated here rather than shared cross-file to keep each fixture file
 * self-contained and independently readable). */
fun roleTag(key: String): CardTag = CardTag(key, TagCategory.ROLE)

/** A bulk basic-land entry, closing the mainboard to [quantity] copies of a single basic. */
private fun basics(landName: String, symbol: String, quantity: Int): DeckEntry = entry(
    card(
        id = "land-$landName-${symbol}",
        name = landName,
        typeLine = "Basic Land — $landName",
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = listOf(symbol),
        producedMana = symbol,
    ),
    quantity = quantity.coerceAtLeast(1),
)

/** Pads [nonland] (commander + spells) up to exactly 100 total (Commander) with a single bulk
 * basic-land entry keyed on [landName]/[symbol]. Mirrors DeckAnalysisEngineGoldenTest's private
 * `withBasics`. */
fun withBasicsCommander(nonland: List<DeckEntry>, landName: String, symbol: String): List<DeckEntry> {
    val nonlandCount = nonland.sumOf { it.quantity }
    return nonland + basics(landName, symbol, 100 - nonlandCount)
}

/** [withBasicsCommander]'s Modern/60-card sibling — pads [nonland] to exactly 60 cards. */
fun withBasicsSixty(nonland: List<DeckEntry>, landName: String, symbol: String): List<DeckEntry> {
    val nonlandCount = nonland.sumOf { it.quantity }
    return nonland + basics(landName, symbol, 60 - nonlandCount)
}

/**
 * One calibration-corpus fixture (spec §9's table, row-for-row). [expectedMacro]/[expectedPosture]/
 * [expectedThemes] are the SPEC's documented target values (free-text labels, since posture/PRISON/
 * the axis-based theme set don't exist as enum members until later phases) — recorded here for
 * later phases to diff against, NOT asserted in phase 0. `null`/`"—"` mirrors the spec table's own
 * em-dash for "none".
 */
data class AnalysisV3Fixture(
    val id: Int,
    val name: String,
    val anchor: String,
    val format: DeckFormat,
    val mainboard: List<DeckEntry>,
    val colorIdentity: Set<ManaColor>,
    val commanderTags: List<CardTag> = emptyList(),
    val expectedMacro: String,
    val expectedPosture: String = "—",
    val expectedThemes: String = "—",
) {
    /** `null` only for [DeckFormat.DRAFT] (never used by this corpus) — see [ArchetypeFormat.of]. */
    val archetypeFormat: ArchetypeFormat? get() = ArchetypeFormat.of(format)
}

/** Convenience constants so fixture files don't repeat magic strings for "no posture"/"no themes". */
const val NO_POSTURE = "—"
const val NO_THEMES = "—"
