package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-15

// ═══════════════════════════════════════════════════════════════════════════════
//  CategoryVocabulary — Deck Wizard Commander v5, X0 (H2/H4/S1/S2).
//
//  The one authoritative table for "which CardTag keys mean membership in category X", read by
//  BOTH the analysis classifier ([ArchetypeRoleClassifier.tagMatcher]) and the Collection-browse
//  filter ([SectionSearchQuery.collectionTagKeysFor], consumed by
//  [com.mmg.manahub.core.domain.search.StructuredCardSearch.matchesForCategoryBrowse]) so the two
//  agree by construction: a card the analysis attributes to a category is exactly the set Browse's
//  Collection filter returns for that category (S2).
//
//  [com.mmg.manahub.core.model.CardFunctionOption.collectionTagKeys] is a SEPARATE, intentionally
//  different vocabulary for the general Advanced Search "Card function" facet (audited
//  2026-08-24, feature/decks/CLAUDE.md) — genuinely a different key space (Scryfall function-tag
//  strings vs. this table's RoleKey/CardTag space), not read from here, and Browse-from-a-section
//  must never route its Collection filter through it again (that was H2's root cause: "Counters
//  Payoff" browsed via `SearchCriterion.CardFunction("counters-matter")`, which locally resolves
//  through `CardFunctionOption.collectionTagKeys = {counters_payoff, plus_counters}` — a WIDER set
//  than the classifier's own `{counters_payoff}` check, so Browse found cards the analysis ignored).
// ═══════════════════════════════════════════════════════════════════════════════

object CategoryVocabulary {

    /**
     * Widened memberships — two (or more) CardTag keys detect the SAME production concept via the
     * SAME (or a proven-superset) detection pattern, so a card confirmed under EITHER key is the
     * same real-world signal for classification and Browse alike. Every entry below is cited
     * against `TagDictionary.kt`'s current rule text, not assumed:
     *
     * - `counters_payoff` (TagDictionary.kt:646-648, `strat(..., TagCategory.ROLE, ...,
     *   rule(allOf = listOf("+1/+1 counter")))`) and `plus_counters` (TagDictionary.kt:365-367,
     *   `strat(..., TagCategory.STRATEGY, ..., rule(allOf = listOf("+1/+1 counter")))`) run the
     *   IDENTICAL oracle-text rule — only the tag's category (ROLE vs STRATEGY) and declared
     *   confidence (0.85 vs 0.95) differ. Since a card auto-confirms each tag independently against
     *   the user's OWN `autoThreshold`, a card can end up with one confirmed and the other only
     *   SUGGESTED (or below the suggestion floor) even though the detector fired on both from the
     *   exact same oracle text. Same underlying signal — both keys count for "Counters Payoff".
     * - `landfall_payoff` (TagDictionary.kt:640-642, `rule(allOf = listOf("landfall"))`) and the
     *   `landfall` keyword tag (TagDictionary.kt:201, `kw("landfall", "Landfall")` — the literal
     *   ability word) are the same signal: the printed "Landfall" ability word's own oracle text
     *   always contains the substring "landfall" (e.g. "Landfall — Whenever a land you control
     *   enters, ..."), so every keyword-landfall card already satisfies `landfall_payoff`'s own
     *   rule too — the keyword tag is a strict subset of what the payoff rule already matches.
     *
     * Every OTHER `CardFunctionOption` multi-key mapping this run audited (`typal` → tribal +
     * tribe_payoff, `death-trigger` → death_triggers + death_payoff, `reanimate` → reanimator +
     * reanimation, `mill` → mill + mill_engine) uses genuinely DIFFERENT detection rules (a broader
     * "cares about the theme" STRATEGY flag vs. a narrower specific payoff/producer pattern, or —
     * for `tribal` — no `DetectionRule` at all, `plain(...)`) — those categories intentionally stay
     * at their own bare RoleKey below, narrowing Browse to match the analysis rather than widening
     * the analysis to match Browse's old, looser behavior. See ADR-009 for the per-key evidence.
     */
    private val WIDENED_MEMBERSHIP: Map<RoleKey, Set<String>> = mapOf(
        "counters_payoff" to setOf("counters_payoff", "plus_counters"),
        "landfall_payoff" to setOf("landfall_payoff", "landfall"),
    )

    /**
     * RoleKeys with NO CardTag equivalent — Browse has nothing meaningful to pre-filter the
     * Collection tab by. `removal_spot`/`removal_mass` only have a generic "removal" tag (would
     * conflate spot removal with board wipes); `finisher` has no tag at all; `equipment_or_aura`
     * only has "equipment"/"equipment_matters" (neither an exact match); `tribe_members` is a
     * runtime [TribeDeriver.subtypeKeys] structural fact, not a static tag.
     *
     * The 5 [ArchetypeRoleClassifier] `LEGACY_ROLE_MAP` roles (`ramp`, `card_draw`, `removal_spot`,
     * `removal_mass`, `tutor`) deliberately stay OUTSIDE this table's widening/narrowing decisions:
     * they are detected by `RoleClassifier`'s own tag + oracle-fallback classification (reused
     * wholesale, not re-implemented here — see `ArchetypeRoleClassifier`'s file header). `ramp`/
     * `card_draw`/`tutor` already carry a real `TagCategory.ROLE` tag of the identical key, which
     * `RoleClassifier` reads as its primary signal, so this table's default (a singleton set of the
     * bare key) already agrees with the analysis on every TAG-confirmed card. `RoleClassifier`'s
     * additional oracle-text fallback (private to that class) is a narrower, already-validated
     * superset the Collection filter cannot see without exposing that regex publicly — a known,
     * accepted asymmetry (a tag-less-but-oracle-detected ramp spell is counted by the analysis but
     * not offered by Browse), tracked as open debt in ADR-009 rather than closed in this run.
     */
    private val NO_TAG_EQUIVALENT: Set<RoleKey> = setOf(
        "removal_spot", "removal_mass", "finisher", "equipment_or_aura", "tribe_members",
    )

    /** The full [CardTag][com.mmg.manahub.core.model.CardTag] key set that means membership in
     * [roleKey] — read by [ArchetypeRoleClassifier.tagMatcher] (classification) and
     * [SectionSearchQuery.collectionTagKeysFor] (Browse's Collection filter), so the two can never
     * drift apart for a role this table knows about. Any [roleKey] not explicitly narrowed or
     * widened above defaults to its own bare key — the common, unambiguous case (one tag, one
     * role). */
    fun cardTagKeysFor(roleKey: RoleKey): Set<String> = when {
        roleKey in NO_TAG_EQUIVALENT -> emptySet()
        roleKey in WIDENED_MEMBERSHIP -> WIDENED_MEMBERSHIP.getValue(roleKey)
        else -> setOf(roleKey)
    }
}
