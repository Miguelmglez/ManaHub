package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DetectionRule
import com.mmg.manahub.core.model.SearchCriterion

// ═══════════════════════════════════════════════════════════════════════════════
//  SectionSearchQuery — Deck Analysis Category Sections rework, W3.
//  (docs/plans/deck-analysis-category-sections-plan.md)
//
//  Pure `commonMain` lookup table + composer that turns a [CardSection.id] into a Scryfall
//  search query for the section's "Browse for <Category>" button, plus the [CardTag] key set
//  used to pre-filter the local Collection tab. Deliberately kept OUTSIDE [AnalysisEngine] (D6 /
//  Architecture section) so the engine stays free of Scryfall syntax and this table can be tested
//  independently.
//
//  Every fragment string below is a CURATED, COMPILE-TIME CONSTANT -- never user input, never
//  passed through [com.mmg.manahub.feature.decks.domain.usecase.search.BuildScryfallQueryUseCase]
//  .escapeValue() (which strips to `[a-zA-Z0-9\-',.\s]` and would corrupt `oracle:"+1/+1 counter"`
//  into `"11 counter"` and drop the `{1}` stax pattern). Verified by grep after writing this file:
//  zero references to `escapeValue`/`BuildScryfallQueryUseCase` anywhere below.
//
//  ── ARCHITECTURAL DEVIATION FROM THE PLAN TEXT (flag this prominently) ──
//  The plan's W3 section says to "prefer generating [the DetectionRule-translated fragments] from
//  TagDictionary.rules at build/first-use ... rather than hardcoding". That is not possible from
//  this file's location: `TagDictionary` lives in `:shared:core-data`, and `:shared:core-domain`
//  (this module) does NOT depend on `:shared:core-data` -- only on `:shared:core-model` and
//  `:shared:core-common` (see this module's `build.gradle.kts`). Reaching into `TagDictionary` from
//  here would invert Clean Architecture layering (domain depending on data). This is the EXACT same
//  fact [AnalysisEngine.synergyStrategyLabel] documents a few lines above `evaluateSynergy` (W2,
//  running in parallel with this workstream) for the identical reason -- both workstreams hit the
//  same wall independently. Resolution, mirroring that precedent: [DICTIONARY_TRANSLATED_RULES]
//  below hardcodes a snapshot of the 10 relevant `TagDictionary` entries' [DetectionRule]s (copied
//  verbatim from `TagDictionary.kt`, cited by key), and [translate] is a REAL, independently-tested
//  function against the [DetectionRule] shape -- so the translation LOGIC still lives in one place
//  and is exercised by a real test, only the DATA is duplicated. If `TagDictionary`'s rules for
//  these 10 keys change, this file's snapshot needs a manual re-sync (known limitation, not
//  automatic propagation as the plan hoped -- flagged in the decisions doc).
//
//  ── A SECOND DEVIATION: the plan's own W3 table is STALE for 3 of the 10 translated roles ──
//  Diffing the plan's validated strings against `TagDictionary.kt`'s CURRENT rules (as read while
//  writing this file) found `sac_outlet`, `clone_theft_effect`, and `tribe_payoff` have each grown
//  MORE `anyOf` terms since the plan's live-verification pass (tag-dictionary hardening work landed
//  in between). This file's snapshot uses the CURRENT (larger) term lists, not the plan's frozen
//  ones -- using the stale, narrower fragment would violate D6's whole point ("the same text
//  patterns that tag the local collection, so remote and local results agree by construction"): a
//  card newly tagged `sac_outlet` locally via "sacrifice an artifact:" would silently NOT show up
//  in that section's Scryfall browse results if this file kept the plan's old 3-term fragment.
//  Also normalizes quoting: every `oracle:` term is quoted uniformly (`oracle:"metalcraft"`, not
//  the plan table's inconsistent bare `oracle:constellation`/`oracle:magecraft` on 2 of the 10 rows)
//  -- functionally identical on Scryfall for a single-word term, just consistent output.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The color identity + format + dominant-tribe context a [CardSection]'s browse query is built
 * against. [dominantTribe] is the [TribeDeriver]-derived subtype key WITHOUT the `tribe:` prefix
 * (e.g. `"elf"`), or `null` when the deck has no dominant tribe (non-TRIBAL skeletons, or a TRIBAL
 * skeleton the resolver could not pin one for).
 */
data class SectionQueryContext(
    val colorIdentity: Set<ManaColor>,
    val format: DeckFormat,
    val dominantTribe: String?,
)

/**
 * Curated, compile-time-constant Scryfall fragment lookup + composer for [CardSection] browse
 * buttons, plus the local-collection tag-key equivalent. See the file header for the two
 * documented deviations from the plan's W3 text (module-layering + a stale sub-table).
 */
object SectionSearchQuery {

    /**
     * The section's own category clause, e.g. `function:spot-removal`. `null` means "no Browse
     * button" -- either the section has no sensible add-more action (`offplan`, `legal`,
     * `illegal` -- see the file's [fragmentFor] KDoc below for why the legality pair is included
     * here) or [context] is missing a fact the fragment needs (`role:tribe_members` with no
     * [SectionQueryContext.dominantTribe]).
     *
     * ## `legal` / `illegal` resolution (flagged per the task brief -- this was genuinely
     * ambiguous in the plan text)
     * The plan's Architecture section states "`fragmentFor` is the section's own clause; legality
     * composition (`legal:<format>`) belongs in `buildFor`, not `fragmentFor`" but does not say
     * what the LEGALITY pillar's own `legal`/`illegal` section ids should return from
     * `fragmentFor` itself. Both resolve to `null` here: a "Browse for Legal" button is redundant
     * with the `legal:<format>` clause [buildFor] already appends to EVERY other section's query
     * (browsing for more legal removal spells already only returns legal ones), and a "Browse for
     * Illegal" button would invite the user to add more illegal cards to their own deck -- neither
     * is a sensible action, matching `offplan`'s existing "no sensible add-more action" precedent.
     */
    fun fragmentFor(sectionId: String, context: SectionQueryContext): String? = when {
        // Deck Analysis Engine v3, PHASE 4 (spec §7) -- the offplan 3-way split's other two new ids
        // ("interaction"/"standalone"). Additive only, per this workstream's own instruction: same
        // "no sensible add-more action" precedent as offplan/legal/illegal directly below.
        sectionId == "interaction" -> null
        sectionId == "standalone" -> null
        sectionId == "offplan" -> null
        sectionId == "legal" -> null
        sectionId == "illegal" -> null
        sectionId == "mana_rock" -> "function:mana-rock"
        sectionId == "mana_dork" -> "function:mana-dork"
        sectionId.startsWith("produces:") ->
            "t:land produces:${sectionId.removePrefix("produces:")}"
        sectionId.startsWith("mv:") -> curveFragment(sectionId.removePrefix("mv:"))
        sectionId.startsWith("role:") -> roleFragment(sectionId.removePrefix("role:"), context)
        sectionId.startsWith("fingerprint:") -> ROLE_ORACLE_FRAGMENTS[sectionId.removePrefix("fingerprint:")]
        sectionId.startsWith(TribeDeriver.TRIBE_PREFIX) -> tribeFragment(sectionId)
        else -> null
    }

    /**
     * Full query: [fragmentFor]'s category clause + color identity + format legality. `null`
     * whenever [fragmentFor] is `null` -- never a half-built query with only identity/legality
     * and no category clause.
     */
    fun buildFor(sectionId: String, context: SectionQueryContext): String? {
        val category = fragmentFor(sectionId, context) ?: return null
        val parts = mutableListOf(category)
        identityClause(context)?.let(parts::add)
        legalityClause(context.format)?.let(parts::add)
        return parts.joinToString(" ")
    }

    /**
     * Suggestions Tab UI Polish plan (W11, D8, 2026-08-25) — structured equivalent of [buildFor]:
     * decomposes a section's Browse query into real [AdvancedSearchQuery] criteria instead of one
     * flat Scryfall string, so "Browse for X" can open [AdvancedSearchSheet]
     * [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet] pre-populated with the
     * translated filters ALREADY CHECKED, instead of dumping raw Scryfall syntax into
     * [CardSearchSheet][com.mmg.manahub.core.ui.components.CardSearchSheet]'s plain search bar
     * (the bug this workstream exists to fix — D8's "esto es un error gravísimo").
     *
     * `null` exactly when [fragmentFor] is `null` for the same `sectionId`/[context] (no Browse
     * button exists for that section in the first place — `offplan`, `legal`, `illegal`, or a
     * `role:tribe_members` section on a deck with no dominant tribe) — never a half-built/empty
     * query. For every section that DOES have a real Browse button, this is a REAL, non-degenerate
     * `AdvancedSearchQuery`: 100% structured, zero raw-text fallback into the plain search bar. The
     * ONLY "soft" landing spot is [SearchCriterion.OracleTerms] for the handful of TagDictionary-
     * translated roles with no real Scryfall `function:` tag — that still counts as fully
     * structured (a real, always-visible `AdvancedSearchSheet` field), not a workaround; see
     * [roleCriteria]'s own comment for which 3 of the original 10 now route through
     * [SearchCriterion.CardFunction] instead, after re-verifying live that a real tag now exists.
     *
     * Composition mirrors [buildFor] exactly: the section's own category criteria, then color
     * identity ([identityCriterion]), then format legality ([legalityCriterion]) — same 3-part
     * shape, same order, just as typed criteria instead of joined string fragments.
     */
    fun toAdvancedQuery(sectionId: String, context: SectionQueryContext): AdvancedSearchQuery? {
        val category = categoryCriteriaFor(sectionId, context) ?: return null
        val criteria = mutableListOf<SearchCriterion>()
        criteria += category
        identityCriterion(context)?.let(criteria::add)
        legalityCriterion(context.format)?.let(criteria::add)
        return AdvancedSearchQuery(criteria = criteria)
    }

    /**
     * [SearchCriterion] mirror of [identityClause] — `null`/non-null under the EXACT same
     * conditions, same WUBRG-ordered color set, and now the same explicit operator: the clause
     * builds `id<=`, so the criterion carries [ColorMatchMode.AT_MOST].
     *
     * This used to pass `exactly = false` and rely on that meaning "at most" — true of the `id:`
     * Scryfall renders it as, but the local matcher read the same bare form as a SUPERSET test, so
     * a Commander deck's Collection tab collapsed to zero while All Cards worked (2026-09-07).
     */
    private fun identityCriterion(context: SectionQueryContext): SearchCriterion? {
        if (ArchetypeFormat.of(context.format) != ArchetypeFormat.COMMANDER) return null
        val colored = WUBRG_ORDER.filter { it in context.colorIdentity }
        if (colored.isEmpty()) return null
        return SearchCriterion.ColorIdentity(
            colors = colored.map { it.symbol }.toSet(),
            mode = ColorMatchMode.AT_MOST,
        )
    }

    /** [SearchCriterion] mirror of [legalityClause] — same format→Scryfall-format-name mapping,
     * `null` for the same 2 formats. [SearchCriterion.Format]'s `f:<x>` rendering is a confirmed
     * live alias of `legal:<x>` (both return identical result sets on Scryfall). */
    private fun legalityCriterion(format: DeckFormat): SearchCriterion? {
        val scryfallFormat = when (format) {
            DeckFormat.STANDARD -> "standard"
            DeckFormat.PIONEER -> "pioneer"
            DeckFormat.MODERN -> "modern"
            DeckFormat.LEGACY -> "legacy"
            DeckFormat.VINTAGE -> "vintage"
            DeckFormat.PAUPER -> "pauper"
            DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL -> "commander"
            DeckFormat.CASUAL, DeckFormat.DRAFT -> null
        } ?: return null
        return SearchCriterion.Format(format = listOf(scryfallFormat), legal = true)
    }

    /** [SearchCriterion] mirror of [fragmentFor] — same `when` shape, same null cases. */
    private fun categoryCriteriaFor(sectionId: String, context: SectionQueryContext): List<SearchCriterion>? = when {
        // Deck Analysis Engine v3, PHASE 4 -- see the matching comment in [fragmentFor] above.
        sectionId == "interaction" -> null
        sectionId == "standalone" -> null
        sectionId == "offplan" -> null
        sectionId == "legal" -> null
        sectionId == "illegal" -> null
        sectionId == "mana_rock" -> listOf(SearchCriterion.CardFunction(setOf("mana-rock")))
        sectionId == "mana_dork" -> listOf(SearchCriterion.CardFunction(setOf("mana-dork")))
        sectionId.startsWith("produces:") -> listOf(
            SearchCriterion.ManaProduction(colors = setOf(sectionId.removePrefix("produces:")), requireLand = true),
        )
        sectionId.startsWith("mv:") -> curveCriteria(sectionId.removePrefix("mv:"))
        sectionId.startsWith("role:") -> roleCriteria(sectionId.removePrefix("role:"), context)
        sectionId.startsWith("fingerprint:") -> ROLE_CRITERIA[sectionId.removePrefix("fingerprint:")]
        sectionId.startsWith(TribeDeriver.TRIBE_PREFIX) -> tribeCriteria(sectionId)
        else -> null
    }

    /** [SearchCriterion] mirror of [curveFragment] — `mv=N`/`mv>=7` plus the SAME `-t:land`
     * exclusion (via [SearchCriterion.CardType]'s new `exclude` flag, W11), same 2-part shape. */
    private fun curveCriteria(mvToken: String): List<SearchCriterion>? {
        val manaCost = when {
            mvToken == "7plus" -> SearchCriterion.ManaCost(7, ComparisonOperator.GREATER_OR_EQUAL)
            else -> mvToken.toIntOrNull()?.let { SearchCriterion.ManaCost(it, ComparisonOperator.EQUAL) } ?: return null
        }
        return listOf(manaCost, SearchCriterion.CardType(setOf("land"), exclude = true))
    }

    /** [SearchCriterion] mirror of [tribeFragment] — identical sanitization. */
    private fun tribeCriteria(sectionId: String): List<SearchCriterion>? {
        val sanitized = sectionId.removePrefix(TribeDeriver.TRIBE_PREFIX).filter { it.isLetter() }.lowercase()
        return if (sanitized.isEmpty()) null else listOf(SearchCriterion.CardType(setOf(sanitized)))
    }

    /** [SearchCriterion] mirror of [roleFragment]. */
    private fun roleCriteria(key: RoleKey, context: SectionQueryContext): List<SearchCriterion>? =
        if (key == "tribe_members") context.dominantTribe?.let { listOf(SearchCriterion.CardType(setOf(it))) }
        else ROLE_CRITERIA[key]

    /**
     * Converts a [DetectionRule] list from [DICTIONARY_TRANSLATED_RULES] into ONE
     * [SearchCriterion.OracleTerms] — reuses the SAME rule data [translate]/[renderRule] already
     * render into a plain string, so a future edit to [DICTIONARY_TRANSLATED_RULES] propagates to
     * BOTH the string path (`fragmentFor`/`buildFor`) and this structured path without a second
     * manual edit (the exact drift this file's header already flags as a known risk for the
     * string path alone — not widening it to a second copy here).
     *
     * Handles the two rule shapes [DICTIONARY_TRANSLATED_RULES] actually contains: a single rule
     * (its `allOf`/`anyOf`/`typeLineAnyOf` map directly onto the criterion's own fields) and 2+
     * single-`allOf`-term rules OR'd together (`graveyard_enabler`'s shape) — folded into ONE
     * `anyOfGroups` entry, since ORing single-term `allOf` rules together is semantically identical
     * to one OR group (see [translate]'s own KDoc for why `graveyard_enabler` renders as a single
     * level of parens, not nested).
     */
    private fun List<DetectionRule>.toOracleTermsCriterion(): SearchCriterion.OracleTerms {
        if (size == 1) {
            val rule = first()
            return SearchCriterion.OracleTerms(
                allOf = rule.allOf,
                anyOfGroups = if (rule.anyOf.isNotEmpty()) listOf(rule.anyOf) else emptyList(),
                typeLineAnyOf = rule.typeLineAnyOf,
            )
        }
        val terms = flatMap { it.allOf + it.anyOf }
        return SearchCriterion.OracleTerms(anyOfGroups = listOf(terms))
    }

    /**
     * Local collection filter: the [com.mmg.manahub.core.model.CardTag] keys equivalent to this
     * section, for pre-filtering `CardSearchSheet`'s Collection tab.
     *
     * `role:*`/`fingerprint:*` ids: per D6 the [RoleKey] vocabulary and the tagging engine's
     * [com.mmg.manahub.core.model.CardTag] vocabulary are largely the SAME string keys -- BUT the
     * task brief explicitly asked to verify this, not trust it. All 37 [RoleKey]s referenced from
     * `ArchetypeData`'s bands were spot-checked against `TagDictionary.kt`'s current entries; 5 do
     * NOT have a matching `CardTag` key: `removal_spot` / `removal_mass` (only a generic
     * `"removal"` tag exists, which would incorrectly conflate spot removal with board wipes),
     * `finisher` (no such tag at all), `equipment_or_aura` (only `"equipment"` and
     * `"equipment_matters"` exist, neither an exact match), and `tribe_members` (tribe membership
     * is a runtime [TribeDeriver.subtypeKeys] structural fact, not a static tag). Those 5 return
     * `emptySet()` rather than guessing a near-miss key. [NO_COLLECTION_TAG_EQUIVALENT] is that
     * exception list.
     *
     * `tribe:*` ids also return `emptySet()`: a `tribe:elf` fingerprint key is a runtime-derived
     * label ([TribeDeriver.tribeKeys]), never a literal [com.mmg.manahub.core.model.CardTag] on
     * any card -- the local equivalent is the same structural subtype predicate the curve/mana/
     * legality/offplan ids already fall back to (per the plan's own instruction for those).
     */
    fun collectionTagKeysFor(sectionId: String): Set<String> = when {
        sectionId.startsWith("role:") -> tagKeyOrEmpty(sectionId.removePrefix("role:"))
        sectionId.startsWith("fingerprint:") -> tagKeyOrEmpty(sectionId.removePrefix("fingerprint:"))
        sectionId == "mana_rock" -> setOf("mana_rock")
        sectionId == "mana_dork" -> setOf("mana_dork")
        else -> emptySet() // tribe:*, produces:*, mv:*, legal, illegal, offplan, interaction, standalone -- structural, not tag-keyed
    }

    // ── Identity / legality composition ─────────────────────────────────────────────────────

    /** WUBRG canonical order for `id<=` clauses (Scryfall accepts any order but this reads best). */
    private val WUBRG_ORDER = listOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G)

    /**
     * `id<=WUBG` for Commander-family formats (COMMANDER + COMMANDER_CASUAL, via
     * [ArchetypeFormat.of] -- the same distinction [AnalysisEngine] already uses), omitted for
     * 60-card/Draft formats whose legal pool is not identity-bounded. Colorless-only identity
     * (`{C}` with no WUBRG colors, or a genuinely colorless deck) omits the clause entirely --
     * `id<=C` is never useful ("colorless or less" adds no filtering `id<=` doesn't already do by
     * being empty).
     */
    private fun identityClause(context: SectionQueryContext): String? {
        if (ArchetypeFormat.of(context.format) != ArchetypeFormat.COMMANDER) return null
        val colored = WUBRG_ORDER.filter { it in context.colorIdentity }
        if (colored.isEmpty()) return null
        return "id<=" + colored.joinToString("") { it.symbol }
    }

    /**
     * `legal:<format>` for every format with a live Scryfall legality list. [DeckFormat
     * .COMMANDER_CASUAL] maps to the SAME `legal:commander` pool as [DeckFormat.COMMANDER] --
     * "Casual" here is this app's own relaxed deck-BUILDING rules (no format legality difference
     * from Scryfall's point of view). [DeckFormat.CASUAL]/[DeckFormat.DRAFT] have no Scryfall
     * legality list to filter by and are omitted, per the plan.
     */
    private fun legalityClause(format: DeckFormat): String? = when (format) {
        DeckFormat.STANDARD -> "legal:standard"
        DeckFormat.PIONEER -> "legal:pioneer"
        DeckFormat.MODERN -> "legal:modern"
        DeckFormat.LEGACY -> "legal:legacy"
        DeckFormat.VINTAGE -> "legal:vintage"
        DeckFormat.PAUPER -> "legal:pauper"
        DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL -> "legal:commander"
        DeckFormat.CASUAL, DeckFormat.DRAFT -> null
    }

    // ── Curve ────────────────────────────────────────────────────────────────────────────────

    private fun curveFragment(mvToken: String): String? = when (mvToken) {
        "7plus" -> "mv>=7 -t:land"
        else -> mvToken.toIntOrNull()?.let { "mv=$it -t:land" }
    }

    // ── Tribe (SYNERGY tribe:<x> sections) ──────────────────────────────────────────────────

    /** `"tribe:elf"` -> `"t:elf"`. Sanitizes the subtype token to LETTERS ONLY, lowercase --
     * the exact same sanitization [com.mmg.manahub.feature.decks.domain.usecase
     * .CandidatePoolGenerator.tribeFragment] already applies (`.filter { it.isLetter() }
     * .lowercase()`), matched deliberately so both call sites treat a tribe token identically. */
    private fun tribeFragment(sectionId: String): String? {
        val sanitized = sectionId.removePrefix(TribeDeriver.TRIBE_PREFIX).filter { it.isLetter() }.lowercase()
        return if (sanitized.isEmpty()) null else "t:$sanitized"
    }

    // ── Plan roles (role:<key> in PLAN_ROLES / MANA_BASE) + fingerprint:<key> (SYNERGY) ────────

    private fun roleFragment(key: RoleKey, context: SectionQueryContext): String? =
        if (key == "tribe_members") context.dominantTribe?.let { "t:$it" }
        else ROLE_ORACLE_FRAGMENTS[key]

    private fun tagKeyOrEmpty(key: RoleKey): Set<String> =
        if (key in NO_COLLECTION_TAG_EQUIVALENT) emptySet() else setOf(key)

    /** Role keys spot-checked against `TagDictionary.kt` with NO matching `CardTag` key (see
     * [collectionTagKeysFor] KDoc). All 37 [RoleKey]s referenced by [ArchetypeData]'s bands were
     * checked; these 5 are the only misses. */
    private val NO_COLLECTION_TAG_EQUIVALENT: Set<RoleKey> = setOf(
        "removal_spot", "removal_mass", "finisher", "equipment_or_aura", "tribe_members",
    )

    // ── Plan roles -- direct oracle tag (W3 table 1, 20 rows) ───────────────────────────────

    private val DIRECT_ORACLE_TAGS: Map<RoleKey, String> = mapOf(
        "removal_spot" to "function:spot-removal",
        "removal_mass" to "function:board-wipe",
        "card_draw" to "function:card-advantage",
        "ramp" to "function:ramp",
        "tutor" to "function:tutor",
        "counterspell" to "function:counterspell",
        "protection" to "function:protection",
        "recursion" to "function:recursion",
        "evasion" to "function:evasion",
        "graveyard_hate" to "function:graveyard-hate",
        "reanimation" to "function:reanimate",
        "self_mill_payoff" to "function:self-mill",
        "mill_engine" to "function:mill",
        "wheel" to "function:wheel",
        "landfall_payoff" to "function:landfall",
        "lifegain_payoff" to "function:lifegain-matters",
        "counters_payoff" to "function:counters-matter",
        "death_payoff" to "function:death-trigger",
        "blink_effect" to "function:blink",
        "group_effect" to "function:group-hug",
        "stax_piece" to "function:tax",
    )

    // ── Plan roles -- structural, constant (W3 table 2, minus tribe_members which needs
    //    [SectionQueryContext.dominantTribe] and is handled in [roleFragment] directly) ────────

    private val STRUCTURAL_ORACLE_FRAGMENTS: Map<RoleKey, String> = mapOf(
        "mana_fix" to "produces>=2",
        "equipment_or_aura" to "(t:equipment or t:aura)",
        "threat_early" to "t:creature mv<=3 pow>=3",
        "finisher" to "t:creature mv>=5 pow>=4",
        "planeswalker" to "t:planeswalker",
        "vehicle" to "t:vehicle",
    )

    // ── Plan roles -- translated from TagDictionary DetectionRules (W3 table 3, 10 rows) ──────
    //    Snapshot of `TagDictionary.kt`'s current rules for these 10 keys -- see file header for
    //    why this can't be a live TagDictionary.rules lookup, and which 3 rows differ from the
    //    plan's own (now-stale) validated strings.

    private val DICTIONARY_TRANSLATED_RULES: Map<RoleKey, List<DetectionRule>> = mapOf(
        // TagDictionary.kt:673-675
        "token_generator" to listOf(DetectionRule(allOf = listOf("create", "token"))),
        // TagDictionary.kt:709-714
        "artifact_payoff" to listOf(DetectionRule(anyOf = listOf(
            "whenever an artifact you control enters", "artifacts you control",
            "for each artifact you control", "whenever you cast an artifact spell", "metalcraft",
        ))),
        // TagDictionary.kt:703-708
        "enchantment_payoff" to listOf(DetectionRule(anyOf = listOf(
            "whenever you cast an enchantment spell", "enchantments you control",
            "for each enchantment you control", "constellation",
        ))),
        // TagDictionary.kt:649-654
        "spell_payoff" to listOf(DetectionRule(anyOf = listOf(
            "whenever you cast an instant or sorcery spell",
            "instant and sorcery spells you cast cost", "magecraft",
        ))),
        // TagDictionary.kt:619-622 -- TWO rules, OR'd together
        "graveyard_enabler" to listOf(
            DetectionRule(allOf = listOf("of your library into your graveyard"), confidence = 0.85f),
            DetectionRule(allOf = listOf("you may discard a card"), confidence = 0.60f),
        ),
        // TagDictionary.kt:667-672
        "aura_buff" to listOf(DetectionRule(
            allOf = listOf("enchanted creature gets"), typeLineAnyOf = listOf("aura"),
        )),
        // TagDictionary.kt:607-612 -- CURRENT dictionary has 5 anyOf terms (the plan's W3 table
        // was validated against an older 3-term version; the 2 extra terms
        // ("sacrifice an artifact:" / "sacrifice a token:") were added by later tag-dictionary
        // hardening work -- see file header).
        "sac_outlet" to listOf(DetectionRule(anyOf = listOf(
            "sacrifice a creature:", "sacrifice another creature:", "sacrifice a permanent:",
            "sacrifice an artifact:", "sacrifice a token:",
        ))),
        // TagDictionary.kt:681-685
        "etb_payoff" to listOf(DetectionRule(anyOf = listOf(
            "whenever a creature you control enters", "whenever another creature you control enters",
        ))),
        // TagDictionary.kt:692-696 -- CURRENT dictionary has 4 anyOf terms (the plan's W3 table
        // was validated against an older 3-term version missing "gain control of all" -- see
        // file header).
        "clone_theft_effect" to listOf(DetectionRule(anyOf = listOf(
            "gain control of target", "gain control of all", "as a copy of", "becomes a copy of",
        ))),
        // TagDictionary.kt:715-720 -- CURRENT dictionary has 4 anyOf terms (the plan's W3 table
        // was validated against an older 2-term version missing "creatures you control of the
        // chosen type" / "other creatures you control of the chosen type" -- see file header).
        "tribe_payoff" to listOf(DetectionRule(anyOf = listOf(
            "creatures you control of the chosen type", "other creatures you control of the chosen type",
            "that share a creature type with", "choose a creature type",
        ))),
    )

    private val DICTIONARY_TRANSLATED_FRAGMENTS: Map<RoleKey, String> =
        DICTIONARY_TRANSLATED_RULES.mapValues { (_, rules) -> translate(rules) }

    /** Merged lookup for every constant (context-free) role fragment: 20 direct + 6 structural
     * (excluding `tribe_members`, handled separately) + 10 dictionary-translated = 36 entries,
     * covering every [RoleKey] [ArchetypeData] references except `tribe_members`. Shared by both
     * `role:<key>` and `fingerprint:<key>` ids (plan: "same TagDictionary translation as the
     * roles, reuse your translator"). Widened `private` -> `internal` (W11) so
     * `SectionSearchQueryTest`'s full-coverage test can enumerate `.keys` as the single
     * authoritative RoleKey list, rather than hand-copying a second list that could drift. */
    internal val ROLE_ORACLE_FRAGMENTS: Map<RoleKey, String> =
        DIRECT_ORACLE_TAGS + STRUCTURAL_ORACLE_FRAGMENTS + DICTIONARY_TRANSLATED_FRAGMENTS

    // ── DetectionRule -> Scryfall oracle-text translator (D6) ───────────────────────────────

    /**
     * Mechanical [DetectionRule] -> Scryfall `oracle:`/`t:` translation (D6's translator table):
     * `allOf` terms AND together (space-joined); a single `anyOf`/`typeLineAnyOf` term is emitted
     * bare, 2+ terms are OR'd inside one set of parens; `noneOf`/`typeLineNoneOf` terms negate
     * (`-oracle:`/`-t:`). When [rules] has more than one entry (a key with several alternative
     * detection rules), each rule's own rendering is OR'd together inside ONE outer set of
     * parens -- see the `graveyard_enabler` entry's validated output in
     * [DICTIONARY_TRANSLATED_RULES] for why this is a SINGLE level of parens, not
     * `((r1) or (r2))`, when every individual rule itself renders to one atomic clause.
     *
     * Every oracle term is quoted (`oracle:"word"`), including single words -- a deliberate
     * normalization vs. the plan's W3 table, which left 2 of its 10 rows' single-word terms bare
     * (`oracle:constellation`, `oracle:magecraft`). Both forms are functionally identical on
     * Scryfall for a single token; quoting uniformly avoids a query with mixed quoting styles.
     */
    internal fun translate(rules: List<DetectionRule>): String =
        if (rules.size == 1) renderRule(rules[0])
        else "(" + rules.joinToString(" or ") { renderRule(it) } + ")"

    private fun renderRule(rule: DetectionRule): String {
        val parts = mutableListOf<String>()
        rule.allOf.forEach { parts += oracleTerm(it) }
        if (rule.anyOf.isNotEmpty()) parts += orGroup(rule.anyOf) { oracleTerm(it) }
        rule.noneOf.forEach { parts += "-" + oracleTerm(it) }
        if (rule.typeLineAnyOf.isNotEmpty()) parts += orGroup(rule.typeLineAnyOf) { "t:$it" }
        rule.typeLineNoneOf.forEach { parts += "-t:$it" }
        return parts.joinToString(" ")
    }

    private fun orGroup(terms: List<String>, render: (String) -> String): String =
        if (terms.size == 1) render(terms[0]) else "(" + terms.joinToString(" or ", transform = render) + ")"

    private fun oracleTerm(text: String): String = "oracle:\"$text\""

    // ── W11 structured (SearchCriterion) equivalent of ROLE_ORACLE_FRAGMENTS ────────────────
    //    Declared here (after every table it reads) rather than near roleCriteria/toAdvancedQuery
    //    above -- Kotlin `object` property initializers run in DECLARATION ORDER, and this table
    //    reads DIRECT_ORACLE_TAGS/DICTIONARY_TRANSLATED_RULES, both declared further up the file.

    /**
     * [SearchCriterion] mirror of [ROLE_ORACLE_FRAGMENTS] — one entry per [RoleKey] covering every
     * `role:<key>`/`fingerprint:<key>` id (excluding `tribe_members`, handled in [roleCriteria]
     * directly since it needs runtime [SectionQueryContext.dominantTribe]).
     *
     * ## W11 Step 1 re-verification: 3 of the 10 dictionary-translated roles now have a real tag
     * `sac_outlet`, `clone_theft_effect`, and `tribe_payoff` were translated oracle-text fragments
     * because no `function:` tag existed for them when [DICTIONARY_TRANSLATED_RULES] was written
     * (Category Sections rework, W3). Re-checked live against the CURRENT
     * [com.mmg.manahub.core.model.CardFunctionOption.allFunctions] (validated 2026-08-24, one day
     * before this check): `sacrifice-outlet`, `theft`, and `typal` each ALREADY exist in that closed
     * list AND their own `collectionTagKeys` explicitly name this exact [RoleKey]
     * (`setOf("sac_outlet")`, `setOf("theft", "clone_theft_effect")`,
     * `setOf("tribal", "tribe_payoff")` respectively) — strong, already-hand-verified evidence these
     * are the intended real-tag equivalents, not a coincidental naming overlap. Routed through
     * [SearchCriterion.CardFunction] for these 3 (a legitimate structured refinement, preferred per
     * the plan's own instruction) instead of [SearchCriterion.OracleTerms] — the remaining 7 keep
     * the oracle-text translation, built directly from [DICTIONARY_TRANSLATED_RULES] (never
     * re-typed, so a future dictionary snapshot edit propagates here too).
     */
    private val ROLE_CRITERIA: Map<RoleKey, List<SearchCriterion>> = buildMap {
        DIRECT_ORACLE_TAGS.forEach { (key, tag) ->
            put(key, listOf(SearchCriterion.CardFunction(setOf(tag.removePrefix("function:")))))
        }
        put("mana_fix", listOf(SearchCriterion.ManaProduction(minDistinctColors = 2)))
        put("equipment_or_aura", listOf(SearchCriterion.CardType(setOf("equipment", "aura"), matchAll = false)))
        put(
            "threat_early",
            listOf(
                SearchCriterion.CardType(setOf("creature")),
                SearchCriterion.ManaCost(3, ComparisonOperator.LESS_OR_EQUAL),
                SearchCriterion.Power(3, ComparisonOperator.GREATER_OR_EQUAL),
            ),
        )
        put(
            "finisher",
            listOf(
                SearchCriterion.CardType(setOf("creature")),
                SearchCriterion.ManaCost(5, ComparisonOperator.GREATER_OR_EQUAL),
                SearchCriterion.Power(4, ComparisonOperator.GREATER_OR_EQUAL),
            ),
        )
        put("planeswalker", listOf(SearchCriterion.CardType(setOf("planeswalker"))))
        put("vehicle", listOf(SearchCriterion.CardType(setOf("vehicle"))))
        // Re-verified real tags (see this property's own KDoc above).
        put("sac_outlet", listOf(SearchCriterion.CardFunction(setOf("sacrifice-outlet"))))
        put("clone_theft_effect", listOf(SearchCriterion.CardFunction(setOf("theft"))))
        put("tribe_payoff", listOf(SearchCriterion.CardFunction(setOf("typal"))))
        // The remaining 7 dictionary-translated roles: genuinely no function: tag exists.
        DICTIONARY_TRANSLATED_RULES
            .filterKeys { it !in setOf("sac_outlet", "clone_theft_effect", "tribe_payoff") }
            .forEach { (key, rules) -> put(key, listOf(rules.toOracleTermsCriterion())) }
    }
}
