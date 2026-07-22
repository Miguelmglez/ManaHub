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
     * Macro archetype → seed tags. [ArchetypeId.GENERIC] intentionally contributes nothing (the
     * neutral default has no identity signal of its own — its seed contribution comes entirely from
     * [themeSeedTags]/[tribeSeedTag] when present). Every OTHER archetype yields >=1 tag (Deck Engine
     * Unification plan D2 "total mapping" requirement) — AGGRO/CONTROL/COMBO/MIDRANGE/RAMP read
     * straight off [SeedStrategy.primaryTags] (a reference, not a copy, so the two vocabularies can
     * never drift); TEMPO has no [SeedStrategy] equivalent, so it gets an explicit tag list here.
     */
    fun archetypeSeedTags(archetype: ArchetypeId): List<CardTag> = MACRO_ARCHETYPE_TAGS[archetype].orEmpty()

    /**
     * Theme → seed tags. TOTAL mapping over all 22 [ThemeId]s (Deck Engine Unification plan D2) —
     * every theme yields >=1 [CardTag], several yield 2 for richer signal (see [THEME_TAGS]'s KDoc
     * for the per-theme rationale, including two documented proxy/limitation cases: VEHICLES and
     * TOOLBOX have no dedicated `*_matter`/STRATEGY-category dictionary tag yet — see those entries'
     * inline comments).
     */
    fun themeSeedTags(themes: List<ThemeId>): List<CardTag> = themes.flatMap { THEME_TAGS[it].orEmpty() }

    /** A runtime `tribe:<subtype>` key as a synthetic [TagCategory.TRIBAL] seed tag — mirrors
     * [DeckScorer.fingerprint]'s own tribe-key contract (NEVER persisted as a real [CardTag]).
     * Blank/null contributes nothing. */
    fun tribeSeedTag(tribe: String?): List<CardTag> =
        tribe?.takeIf { it.isNotBlank() }?.let { listOf(CardTag(key = it, category = TagCategory.TRIBAL)) }.orEmpty()

    /** Combines all three — the full seed-tag contribution of a resolved/pinned
     * (archetype, themes, tribe) triple. */
    fun forArchetype(archetype: ArchetypeId, themes: List<ThemeId>, tribe: String? = null): List<CardTag> =
        (archetypeSeedTags(archetype) + themeSeedTags(themes) + tribeSeedTag(tribe)).distinct()

    /** Convenience overload taking a [StrategyProfile] directly. */
    fun forProfile(profile: StrategyProfile): List<CardTag> =
        forArchetype(profile.archetype ?: ArchetypeId.GENERIC, profile.themes, profile.tribe)

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
        ArchetypeId.RAMP to SeedStrategy.RAMP.primaryTags,
        // TEMPO has no SeedStrategy equivalent -- efficient threats + protection/counterspells to
        // hold the tempo advantage (D2: every non-GENERIC archetype must yield >=1 seed tag).
        ArchetypeId.TEMPO to listOf(CardTag.TEMPO, CardTag.COUNTERSPELL, CardTag.PROTECTION),
        // GENERIC intentionally omitted -- see archetypeSeedTags' KDoc.
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
        ThemeId.STAX to listOf(CardTag.STAX),
        ThemeId.LIFEGAIN to listOf(CardTag.LIFEGAIN),
        ThemeId.TRIBAL to listOf(CardTag.TRIBAL),
        ThemeId.ENCHANTRESS to listOf(CardTag.ENCHANTRESS),
        ThemeId.MILL to listOf(CardTag.GRAVEYARD),
        ThemeId.BLINK to listOf(CardTag.BLINK, CardTag("etb", TagCategory.STRATEGY)),
        // ── 12 newly mapped themes (D2 total-mapping requirement) ─────────────────────────────
        ThemeId.SPELLSLINGER to listOf(
            CardTag("spellslinger", TagCategory.STRATEGY),
            CardTag("spell_copy", TagCategory.STRATEGY),
        ),
        ThemeId.VOLTRON to listOf(
            CardTag("voltron", TagCategory.ARCHETYPE),
            CardTag("equipment_matters", TagCategory.STRATEGY),
        ),
        ThemeId.LANDFALL to listOf(CardTag("lands_matter", TagCategory.STRATEGY)),
        ThemeId.PLUS1_COUNTERS to listOf(CardTag.PLUS_COUNTERS, CardTag.PROLIFERATE),
        ThemeId.ARTIFACTS to listOf(CardTag("artifacts_matter", TagCategory.STRATEGY)),
        ThemeId.WHEELS to listOf(CardTag("wheel", TagCategory.STRATEGY)),
        ThemeId.GROUP_HUG to listOf(CardTag("group_hug", TagCategory.STRATEGY)),
        ThemeId.GROUP_SLUG to listOf(CardTag("group_slug", TagCategory.STRATEGY)),
        // SUPERFRIENDS: no dedicated "planeswalkers_matter" STRATEGY tag exists in TagDictionary yet
        // (only a ROLE-category "planeswalker" card-type tag, which would be inert here -- see this
        // map's own KDoc). PROLIFERATE/doublers are the closest real STRATEGY-category proxies
        // (loyalty-counter growth is core to most superfriends shells) -- documented limitation,
        // tracked for the Phase 5 tag pipeline to add a real dedicated tag.
        ThemeId.SUPERFRIENDS to listOf(CardTag.PROLIFERATE, CardTag("doublers", TagCategory.STRATEGY)),
        // VEHICLES: same limitation as SUPERFRIENDS -- only a ROLE-category "vehicle" card-type tag
        // exists. "artifacts_matter" is the nearest STRATEGY-category proxy (Vehicles are artifacts).
        ThemeId.VEHICLES to listOf(CardTag("artifacts_matter", TagCategory.STRATEGY)),
        // TOOLBOX: no STRATEGY/ARCHETYPE-category tag exists for "silver-bullet toolbox" decks at
        // all -- only the ROLE-category "tutor" tag, which is INERT here (see this map's own KDoc).
        // Deliberately NOT substituted with an unrelated IDENTITY-category tag (e.g. COMBO would
        // actively mis-seed the fingerprint toward combo pieces) -- an honest no-op is safer than a
        // wrong signal. Satisfies the D2 ">=1 seed tag" mapping without corrupting scoring; tracked
        // for the Phase 5 tag pipeline to add a real dedicated "toolbox" STRATEGY tag.
        ThemeId.TOOLBOX to listOf(CardTag.TUTOR),
        ThemeId.CLONES_THEFT to listOf(
            CardTag("clones", TagCategory.STRATEGY),
            CardTag("theft", TagCategory.STRATEGY),
        ),
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
