package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory

/**
 * Wizard Quality Campaign Wave 4 (Task 1) — the SINGLE shared mapping from the archetype layer's
 * [ArchetypeId] / [ThemeId] vocabulary onto the [CardTag] seed vocabulary
 * [DeckScorer.profile] consumes for its identity fingerprint. Two independent consumers:
 *  - [com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase.recomputeProfile]
 *    (build-time, from the resolved
 *    [com.mmg.manahub.feature.decks.domain.template.DeckTemplateArchetypeInfo] the wizard just
 *    built for the in-progress deck).
 *  - [com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator.loadAnalysis] /
 *    the test harness's `HarnessDoctorPipeline.evaluate` (post-build, from a
 *    [com.mmg.manahub.core.model.Deck]'s PERSISTED `archetypeOverride`/`themesOverride`/
 *    `tribeOverride` pin — written by [com.mmg.manahub.core.domain.repository.DeckRepository
 *    .updateArchetypeOverride]/`.updateTribeOverride`, both by the wizard on build and by Deck
 *    Studio's "Deck plan" chip afterward for archetype/themes).
 *
 * Before Wave 4, `BuildDeckFromTemplateUseCase` had a PRIVATE `themeSeedTags` copy of (half of) this
 * table and the Doctor had no equivalent fold-in at all — a deck with a pinned identity could have
 * the SAME card rank above the wizard's category-fill floor while it was being built (explicit hint
 * tags contributed) and below the Doctor's floor once the deck existed (inference-only tags) — the
 * "wizard places it, Doctor wants to cut it right back out" class of bug the Wizard Quality Campaign
 * existed to close. This is the ONLY place either mapping lives — extend here, never re-duplicate a
 * table that can drift out of sync between call sites.
 *
 * Deck Engine Unification plan (D2) extended this to a TOTAL mapping (every [ArchetypeId] and all
 * 22 [ThemeId]s, [GENERIC] intentionally empty — see [archetypeSeedTags]'s KDoc) plus tribe
 * passthrough ([tribeSeedTag]), and added the REVERSE lookups ([archetypeForTag]/[themeForTag]) the
 * wizard's Direction-step chip taps use to resolve a collection-lean [CardTag] onto the new
 * [StrategyProfile] taxonomy — replacing the old `SeedStrategy.forTag` + raw-tagHint-fallback
 * mechanism (RC1). Both directions are derived from the SAME underlying tables so they can never
 * drift relative to each other.
 */
object DeckIdentitySeedTags {

    /**
     * Macro archetype → seed tags. `null` (Deck Analysis Engine v3 removed `ArchetypeId.GENERIC` —
     * "no macro pin" is now `null`, not a neutral enum value) intentionally contributes nothing (the
     * unpinned state has no identity signal of its own — its seed contribution comes entirely from
     * [themeSeedTags]/[tribeSeedTag] when present). Every REAL archetype yields >=1 tag (Deck Engine
     * Unification plan D2 "total mapping" requirement, still honored for the current 5-member
     * [ArchetypeId]) — AGGRO/CONTROL/COMBO/MIDRANGE read straight off [SeedStrategy.primaryTags] (a
     * reference, not a copy, so the two vocabularies can never drift); PRISON has no [SeedStrategy]
     * equivalent, so it reuses the old STAX theme's tag (PRISON absorbed STAX's identity, spec §4.1).
     * `RAMP`/`TEMPO` moved to [PostureId] — see [postureSeedTags].
     */
    fun archetypeSeedTags(archetype: ArchetypeId?): List<CardTag> =
        archetype?.let { MACRO_ARCHETYPE_TAGS[it].orEmpty() }.orEmpty()

    /**
     * Posture → seed tags (Deck Analysis Engine v3). Not yet wired to any production caller — the
     * posture layer is new this phase and the Wizard-facing identity-seeding flow ([forArchetype]/
     * [forProfile]) has not been extended to accept a posture pin yet (out of Phase 3a's scope, a
     * follow-up). Kept for symmetry/future use, mirroring [archetypeSeedTags]'s own shape.
     */
    fun postureSeedTags(posture: PostureId?): List<CardTag> =
        posture?.let { POSTURE_TAGS[it].orEmpty() }.orEmpty()

    /**
     * Theme → seed tags. TOTAL mapping over the current 21 [ThemeId]s (Deck Engine Unification plan
     * D2) — every theme yields >=1 [CardTag], several yield 2 for richer signal (see [THEME_TAGS]'s
     * KDoc for the per-theme rationale, including documented proxy/limitation cases: VEHICLES has no
     * dedicated `*_matter`/STRATEGY-category dictionary tag yet — see that entry's inline comment).
     */
    fun themeSeedTags(themes: List<ThemeId>): List<CardTag> = themes.flatMap { THEME_TAGS[it].orEmpty() }

    /** A runtime `tribe:<subtype>` key as a synthetic [TagCategory.TRIBAL] seed tag — mirrors
     * [DeckScorer.fingerprint]'s own tribe-key contract (NEVER persisted as a real [CardTag]).
     * Blank/null contributes nothing. */
    fun tribeSeedTag(tribe: String?): List<CardTag> =
        tribe?.takeIf { it.isNotBlank() }?.let { listOf(CardTag(key = it, category = TagCategory.TRIBAL)) }.orEmpty()

    /** Combines all three — the full seed-tag contribution of a resolved/pinned
     * (archetype, themes, tribe) triple. `archetype == null` = no macro pin. */
    fun forArchetype(archetype: ArchetypeId?, themes: List<ThemeId>, tribe: String? = null): List<CardTag> =
        (archetypeSeedTags(archetype) + themeSeedTags(themes) + tribeSeedTag(tribe)).distinct()

    /** Convenience overload taking a [StrategyProfile] directly. */
    fun forProfile(profile: StrategyProfile): List<CardTag> =
        forArchetype(profile.archetype, profile.themes, profile.tribe)

    /**
     * Reverse lookup: which [ArchetypeId] (if any) does [tag] represent? Used by the wizard's
     * Direction-step chip taps (a collection-lean [CardTag] resolves onto the new taxonomy instead
     * of the old `SeedStrategy.forTag` + raw-CardTag-fallback mechanism). Derived from
     * [MACRO_ARCHETYPE_TAGS] so it can never drift from [archetypeSeedTags] — first-declared
     * archetype wins on a tag shared by more than one (mirrors `SeedStrategy.forTag`'s own
     * documented tie-break).
     */
    fun archetypeForTag(tag: CardTag): ArchetypeId? = ARCHETYPE_TAG_REVERSE[tag.key]

    /** Reverse lookup: which [ThemeId] (if any) does [tag] represent? See [archetypeForTag]. */
    fun themeForTag(tag: CardTag): ThemeId? = THEME_TAG_REVERSE[tag.key]

    private val MACRO_ARCHETYPE_TAGS: Map<ArchetypeId, List<CardTag>> = mapOf(
        ArchetypeId.AGGRO to SeedStrategy.AGGRO.primaryTags,
        ArchetypeId.CONTROL to SeedStrategy.CONTROL.primaryTags,
        ArchetypeId.COMBO to SeedStrategy.COMBO.primaryTags,
        ArchetypeId.MIDRANGE to SeedStrategy.MIDRANGE.primaryTags,
        // PRISON reuses the old STAX theme's tag -- PRISON absorbed STAX's identity (spec §4.1).
        ArchetypeId.PRISON to listOf(CardTag.STAX),
        // RAMP/TEMPO moved to PostureId -- see POSTURE_TAGS.
    )

    /** [PostureId] → seed tags. See [postureSeedTags]'s KDoc — not yet wired to a production
     * caller. RAMP/TEMPO reuse their old macro-archetype tag lists verbatim; ATTRITION/TOOLBOX/
     * VOLTRON/GROUP_HUG/GROUP_SLUG reuse their old theme tag lists verbatim (both moves are pure
     * relabeling, per spec §3/§4.1 -- zero new tag-matching logic). ATTRITION is genuinely new (no
     * prior macro/theme owned this identity) and gets an inline STRATEGY tag that has no
     * TagDictionary registration yet -- same documented "honest no-op until Phase 5" limitation
     * [THEME_TAGS]'s own KDoc already accepts for VEHICLES. */
    private val POSTURE_TAGS: Map<PostureId, List<CardTag>> = mapOf(
        PostureId.RAMP to SeedStrategy.RAMP.primaryTags,
        PostureId.TEMPO to listOf(CardTag.TEMPO, CardTag.COUNTERSPELL, CardTag.PROTECTION),
        PostureId.ATTRITION to listOf(CardTag("attrition", TagCategory.STRATEGY)),
        PostureId.TOOLBOX to listOf(CardTag.TUTOR),
        PostureId.VOLTRON to listOf(
            CardTag("voltron", TagCategory.ARCHETYPE),
            CardTag("equipment_matters", TagCategory.STRATEGY),
        ),
        PostureId.GROUP_HUG to listOf(CardTag("group_hug", TagCategory.STRATEGY)),
        PostureId.GROUP_SLUG to listOf(CardTag("group_slug", TagCategory.STRATEGY)),
    )

    /**
     * Total theme -> tag mapping (Deck Engine Unification plan D2). Every entry uses ONLY
     * [TagCategory.STRATEGY]/[TagCategory.ARCHETYPE]/[TagCategory.TRIBAL] tags -- [DeckScorer
     * .synergyScore]'s `consider()` only ever accumulates a CANDIDATE CARD's own tag when its
     * category is one of those three (`IDENTITY_CATEGORIES`), so a ROLE/KEYWORD-category seed key
     * would sit in the fingerprint but never be matched by anything -- silently inert.
     *
     * The 10 pre-unification entries (REANIMATOR/SELF_MILL/ARISTOCRATS/TOKENS/STAX/LIFEGAIN/
     * TRIBAL/ENCHANTRESS/MILL/BLINK) keep their EXACT original single-tag mapping (a coarse
     * [CardTag.GRAVEYARD] for the graveyard-family themes) -- `BuildDeckFromTemplateUseCaseTest`'s
     * "a GRAVEYARD-hint deck's persisted archetype pin folds the same GRAVEYARD seed tag" test
     * depends on this exact tag surviving; a more "precise" per-theme tag (e.g. a dedicated
     * `reanimator` STRATEGY tag) is a real future improvement but is deliberately NOT made in this
     * pass to avoid an unrelated behavior change riding along with the D2 taxonomy unification.
     * ARISTOCRATS and BLINK gain a SECOND tag (never replacing the original) -- this is also how 2
     * of the 4 pre-unification "dead chip" tags (`death_triggers`, `etb`) get a taxonomy home; the
     * other 2 (`plus_counters`, `spellslinger`) get one via the 12 newly-added themes below.
     *
     * The 12 NEWLY MAPPED themes (SPELLSLINGER/VOLTRON/LANDFALL/PLUS1_COUNTERS/ARTIFACTS/WHEELS/
     * GROUP_HUG/GROUP_SLUG/SUPERFRIENDS/VEHICLES/TOOLBOX/CLONES_THEFT) had NO entry before this plan
     * (D2 "total mapping" requirement) — see the inline comments on SUPERFRIENDS/VEHICLES/TOOLBOX
     * for the 3 cases where no ideal dictionary tag exists yet and a documented proxy/limitation was
     * chosen instead of inventing a wrong signal.
     *
     * Tag keys not already exposed as [CardTag] companion constants are constructed inline
     * (`CardTag("key", TagCategory.STRATEGY)`) matching `TagDictionary`'s registered key EXACTLY
     * (verified against `core/tagging/TagDictionary.kt`, `:app`) -- this file stays commonMain/
     * resource-free, it does not (and must not) import the Android-only tagging dictionary.
     */
    private val THEME_TAGS: Map<ThemeId, List<CardTag>> = mapOf(
        // ── Pre-unification 10 -- unchanged mapping, see this map's own KDoc ──────────────────
        ThemeId.REANIMATOR to listOf(CardTag.GRAVEYARD),
        ThemeId.SELF_MILL to listOf(CardTag.GRAVEYARD),
        ThemeId.ARISTOCRATS to listOf(CardTag.SACRIFICE, CardTag("death_triggers", TagCategory.STRATEGY)),
        ThemeId.TOKENS to listOf(CardTag.TOKENS),
        // STAX: MOVED to ArchetypeId.PRISON's seed tags (see MACRO_ARCHETYPE_TAGS) -- STAX is no
        // longer a theme (spec §4.1).
        ThemeId.LIFEGAIN to listOf(CardTag.LIFEGAIN),
        ThemeId.TRIBAL to listOf(CardTag.TRIBAL),
        ThemeId.ENCHANTRESS to listOf(CardTag.ENCHANTRESS),
        // MILL -> MILL_OPPONENT (spec §4.2 split).
        ThemeId.MILL_OPPONENT to listOf(CardTag.GRAVEYARD),
        ThemeId.BLINK to listOf(CardTag.BLINK, CardTag("etb", TagCategory.STRATEGY)),
        // ── 12 newly mapped themes (D2 total-mapping requirement) ─────────────────────────────
        ThemeId.SPELLSLINGER to listOf(
            CardTag("spellslinger", TagCategory.STRATEGY),
            CardTag("spell_copy", TagCategory.STRATEGY),
        ),
        // VOLTRON: MOVED to PostureId.VOLTRON (see POSTURE_TAGS) -- no longer a theme (spec §4.1).
        ThemeId.LANDFALL to listOf(CardTag("lands_matter", TagCategory.STRATEGY)),
        ThemeId.PLUS1_COUNTERS to listOf(CardTag.PLUS_COUNTERS, CardTag.PROLIFERATE),
        ThemeId.ARTIFACTS to listOf(CardTag("artifacts_matter", TagCategory.STRATEGY)),
        ThemeId.WHEELS to listOf(CardTag("wheel", TagCategory.STRATEGY)),
        // GROUP_HUG/GROUP_SLUG: MOVED to PostureId (see POSTURE_TAGS) -- no longer themes (spec §4.1).
        // SUPERFRIENDS: no dedicated "planeswalkers_matter" STRATEGY tag exists in TagDictionary yet
        // (only a ROLE-category "planeswalker" card-type tag, which would be inert here -- see this
        // map's own KDoc). PROLIFERATE/doublers are the closest real STRATEGY-category proxies
        // (loyalty-counter growth is core to most superfriends shells) -- documented limitation,
        // tracked for the Phase 5 tag pipeline to add a real dedicated tag.
        ThemeId.SUPERFRIENDS to listOf(CardTag.PROLIFERATE, CardTag("doublers", TagCategory.STRATEGY)),
        // VEHICLES: no dedicated "vehicles_matter" tag exists -- only a ROLE-category "vehicle"
        // card-type tag. "artifacts_matter" is the nearest STRATEGY-category proxy (Vehicles are
        // artifacts).
        ThemeId.VEHICLES to listOf(CardTag("artifacts_matter", TagCategory.STRATEGY)),
        // TOOLBOX: MOVED to PostureId.TOOLBOX (see POSTURE_TAGS) -- no longer a theme (spec §4.1).
        ThemeId.CLONES_THEFT to listOf(
            CardTag("clones", TagCategory.STRATEGY),
            CardTag("theft", TagCategory.STRATEGY),
        ),
        // Deck Analysis Engine v3 (spec §4.2, NEW themes) -- no dedicated TagDictionary tag exists
        // yet for any of the 3; each gets the nearest real STRATEGY-category proxy already
        // registered, same documented-limitation discipline as VEHICLES/SUPERFRIENDS above.
        ThemeId.TREASURE to listOf(CardTag("artifacts_matter", TagCategory.STRATEGY)),
        ThemeId.EQUIPMENT to listOf(CardTag("equipment_matters", TagCategory.STRATEGY)),
        ThemeId.STORM to listOf(CardTag("spellslinger", TagCategory.STRATEGY)),
    )

    // putIfAbsent is JVM-only (java.util.Map) -- not part of the common kotlin.collections
    // MutableMap API, so a plain "insert only if missing" check is used instead (KMP-safe,
    // compiles on wasmJs too).
    private val ARCHETYPE_TAG_REVERSE: Map<String, ArchetypeId> = buildMap {
        MACRO_ARCHETYPE_TAGS.forEach { (archetype, tags) ->
            tags.forEach { tag -> if (tag.key !in this) this[tag.key] = archetype }
        }
    }

    private val THEME_TAG_REVERSE: Map<String, ThemeId> = buildMap {
        THEME_TAGS.forEach { (theme, tags) ->
            tags.forEach { tag -> if (tag.key !in this) this[tag.key] = theme }
        }
    }
}
