package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════════════
//  SectionSearchQueryTest — Deck Analysis Category Sections rework, W3/W8.
//
//  Covers [SectionSearchQuery]: full [RoleKey] coverage from [ArchetypeData], the
//  [com.mmg.manahub.core.model.DetectionRule] translator's exact output for the 10
//  dictionary-translated roles, identity/legality composition per format, and the
//  collection-tag-key exception list. See [SectionSearchQuery]'s file header for the two
//  documented deviations from the plan text (module layering + 3 stale W3-table rows) this
//  suite locks in as the CURRENT correct behavior.
// ═══════════════════════════════════════════════════════════════════════════════

class SectionSearchQueryTest {

    private fun ctx(
        colors: Set<ManaColor> = setOf(ManaColor.W, ManaColor.U),
        format: DeckFormat = DeckFormat.COMMANDER,
        tribe: String? = null,
    ) = SectionQueryContext(colorIdentity = colors, format = format, dominantTribe = tribe)

    // ── Full RoleKey coverage ───────────────────────────────────────────────────────────────

    /** Every [RoleKey] any [ArchetypeData] band (generic/archetype/theme) references, gathered
     * from the live data rather than hand-copied so this test stays honest if a future band adds
     * a new key. */
    private val allArchetypeDataRoleKeys: Set<RoleKey> = buildSet {
        add(ArchetypeData.MANA_FIX_KEY)
        ArchetypeData.generic(ArchetypeFormat.COMMANDER).roleTargets.keys.forEach { add(it) }
        ArchetypeData.generic(ArchetypeFormat.SIXTY).roleTargets.keys.forEach { add(it) }
        ArchetypeData.ARCHETYPES.values.forEach { perFormat ->
            perFormat.values.forEach { def -> def.roleTargets.keys.forEach { add(it) } }
        }
        ArchetypeData.THEMES.values.forEach { theme ->
            theme.adds.keys.forEach { add(it) }
            theme.relaxes.keys.forEach { add(it) }
        }
    }

    @Test
    fun everyArchetypeDataRoleKey_yieldsANonBlankFragment() {
        // tribe_members needs a dominantTribe to resolve -- supply one so this is a genuine
        // coverage check, not a false failure on the one context-dependent key.
        val context = ctx(tribe = "elf")
        val missing = allArchetypeDataRoleKeys.filter { key ->
            SectionSearchQuery.fragmentFor("role:$key", context).isNullOrBlank()
        }
        assertTrue(missing.isEmpty(), "RoleKeys with no fragmentFor mapping: $missing")
    }

    @Test
    fun roleTribeMembers_withNoDominantTribe_returnsNull() {
        assertNull(SectionSearchQuery.fragmentFor("role:tribe_members", ctx(tribe = null)))
    }

    @Test
    fun roleTribeMembers_withDominantTribe_returnsTypeClause() {
        assertEquals("t:goblin", SectionSearchQuery.fragmentFor("role:tribe_members", ctx(tribe = "goblin")))
    }

    // ── Direct oracle tag + structural fragments (spot checks) ─────────────────────────────

    @Test
    fun directOracleTagFragments_matchPlanTable() {
        val context = ctx()
        assertEquals("function:spot-removal", SectionSearchQuery.fragmentFor("role:removal_spot", context))
        assertEquals("function:board-wipe", SectionSearchQuery.fragmentFor("role:removal_mass", context))
        assertEquals("function:tax", SectionSearchQuery.fragmentFor("role:stax_piece", context))
        assertEquals("function:group-hug", SectionSearchQuery.fragmentFor("role:group_effect", context))
    }

    @Test
    fun structuralFragments_matchPlanTable() {
        val context = ctx()
        assertEquals("produces>=2", SectionSearchQuery.fragmentFor("role:mana_fix", context))
        assertEquals("(t:equipment or t:aura)", SectionSearchQuery.fragmentFor("role:equipment_or_aura", context))
        assertEquals("t:creature mv<=3 pow>=3", SectionSearchQuery.fragmentFor("role:threat_early", context))
        assertEquals("t:creature mv>=5 pow>=4", SectionSearchQuery.fragmentFor("role:finisher", context))
        assertEquals("t:planeswalker", SectionSearchQuery.fragmentFor("role:planeswalker", context))
        assertEquals("t:vehicle", SectionSearchQuery.fragmentFor("role:vehicle", context))
    }

    // ── DetectionRule translator: exact strings for the 10 dictionary-translated roles ─────
    //    7 of 10 match the plan's W3 table byte-for-byte (token_generator, aura_buff, etb_payoff
    //    match exactly; artifact_payoff/enchantment_payoff/spell_payoff match modulo the quoting
    //    normalization documented in SectionSearchQuery's file header; graveyard_enabler matches
    //    exactly, confirming the single-paren OR-of-rules behavior). 3 (sac_outlet,
    //    clone_theft_effect, tribe_payoff) intentionally diverge from the plan's now-stale W3
    //    table because TagDictionary.kt's rules for those 3 keys grew MORE anyOf terms since the
    //    plan's live-verification pass -- see the file header for why the CURRENT, larger set is
    //    correct here, not the plan's frozen one.

    @Test
    fun translatedFragment_tokenGenerator_matchesPlanExactly() {
        assertEquals(
            "oracle:\"create\" oracle:\"token\"",
            SectionSearchQuery.fragmentFor("role:token_generator", ctx()),
        )
    }

    @Test
    fun translatedFragment_auraBuff_matchesPlanExactly() {
        assertEquals(
            "oracle:\"enchanted creature gets\" t:aura",
            SectionSearchQuery.fragmentFor("role:aura_buff", ctx()),
        )
    }

    @Test
    fun translatedFragment_etbPayoff_matchesPlanExactly() {
        assertEquals(
            "(oracle:\"whenever a creature you control enters\" or oracle:\"whenever another creature you control enters\")",
            SectionSearchQuery.fragmentFor("role:etb_payoff", ctx()),
        )
    }

    @Test
    fun translatedFragment_graveyardEnabler_matchesPlanExactly_singleParenOrOfRules() {
        // Two separate DetectionRules in TagDictionary, each a single allOf term -- must combine
        // as ONE set of parens, not nested "((r1) or (r2))".
        assertEquals(
            "(oracle:\"of your library into your graveyard\" or oracle:\"you may discard a card\")",
            SectionSearchQuery.fragmentFor("role:graveyard_enabler", ctx()),
        )
    }

    @Test
    fun translatedFragment_artifactPayoff_matchesPlanModuloQuoting() {
        assertEquals(
            "(oracle:\"whenever an artifact you control enters\" or oracle:\"artifacts you control\" " +
                "or oracle:\"for each artifact you control\" or oracle:\"whenever you cast an artifact spell\" " +
                "or oracle:\"metalcraft\")",
            SectionSearchQuery.fragmentFor("role:artifact_payoff", ctx()),
        )
    }

    @Test
    fun translatedFragment_enchantmentPayoff_matchesPlanModuloQuoting() {
        assertEquals(
            "(oracle:\"whenever you cast an enchantment spell\" or oracle:\"enchantments you control\" " +
                "or oracle:\"for each enchantment you control\" or oracle:\"constellation\")",
            SectionSearchQuery.fragmentFor("role:enchantment_payoff", ctx()),
        )
    }

    @Test
    fun translatedFragment_spellPayoff_matchesPlanModuloQuoting() {
        assertEquals(
            "(oracle:\"whenever you cast an instant or sorcery spell\" " +
                "or oracle:\"instant and sorcery spells you cast cost\" or oracle:\"magecraft\")",
            SectionSearchQuery.fragmentFor("role:spell_payoff", ctx()),
        )
    }

    @Test
    fun translatedFragment_sacOutlet_usesCurrentDictionaryFiveTerms_notPlansStaleThree() {
        assertEquals(
            "(oracle:\"sacrifice a creature:\" or oracle:\"sacrifice another creature:\" " +
                "or oracle:\"sacrifice a permanent:\" or oracle:\"sacrifice an artifact:\" " +
                "or oracle:\"sacrifice a token:\")",
            SectionSearchQuery.fragmentFor("role:sac_outlet", ctx()),
        )
    }

    @Test
    fun translatedFragment_cloneTheftEffect_usesCurrentDictionaryFourTerms_notPlansStaleThree() {
        assertEquals(
            "(oracle:\"gain control of target\" or oracle:\"gain control of all\" " +
                "or oracle:\"as a copy of\" or oracle:\"becomes a copy of\")",
            SectionSearchQuery.fragmentFor("role:clone_theft_effect", ctx()),
        )
    }

    @Test
    fun translatedFragment_tribePayoff_usesCurrentDictionaryFourTerms_notPlansStaleTwo() {
        assertEquals(
            "(oracle:\"creatures you control of the chosen type\" " +
                "or oracle:\"other creatures you control of the chosen type\" " +
                "or oracle:\"that share a creature type with\" or oracle:\"choose a creature type\")",
            SectionSearchQuery.fragmentFor("role:tribe_payoff", ctx()),
        )
    }

    // ── engine:<axis>:producers/payoffs (Deck Wizard Commander v5) ──────────────────────────

    @Test
    fun engineFragment_everyEngineAxis_yieldsNonNullBothSides() {
        val context = ctx()
        val axes = listOf(
            "LIFE", "DEATH", "TOKENS", "COUNTERS", "LANDFALL", "GRAVEYARD", "ETB", "SPELLS",
            "ARTIFACTS", "ENCHANTMENTS", "ATTACHED", "ATTACK", "PLANESWALKERS", "GROUP", "ENGINE",
        )
        val missing = axes.filter { axis ->
            SectionSearchQuery.fragmentFor("engine:$axis:producers", context) == null ||
                SectionSearchQuery.fragmentFor("engine:$axis:payoffs", context) == null
        }
        assertTrue(missing.isEmpty(), "engine axes with no fragment on one side: $missing")
    }

    @Test
    fun engineFragment_unknownAxis_returnsNull() {
        assertNull(SectionSearchQuery.fragmentFor("engine:NOT_AN_AXIS:producers", ctx()))
    }

    // ── Curve / mana-base / tribe / legality-pair / offplan structural ids ─────────────────

    @Test
    fun curveFragments_matchPlanTable() {
        val context = ctx()
        assertEquals("mv=0 -t:land", SectionSearchQuery.fragmentFor("mv:0", context))
        assertEquals("mv=6 -t:land", SectionSearchQuery.fragmentFor("mv:6", context))
        assertEquals("mv>=7 -t:land", SectionSearchQuery.fragmentFor("mv:7plus", context))
    }

    @Test
    fun manaBaseFragments_matchPlanTable() {
        val context = ctx()
        assertEquals("t:land produces:B", SectionSearchQuery.fragmentFor("produces:B", context))
        assertEquals("function:mana-rock", SectionSearchQuery.fragmentFor("mana_rock", context))
        assertEquals("function:mana-dork", SectionSearchQuery.fragmentFor("mana_dork", context))
        // mana_fix is shared verbatim between the PLAN_ROLES role: id and the bare MANA_BASE id.
        assertEquals("produces>=2", SectionSearchQuery.fragmentFor("role:mana_fix", context))
    }

    @Test
    fun tribeFragment_sanitizesToLettersOnlyLowercase() {
        assertEquals("t:elf", SectionSearchQuery.fragmentFor("tribe:elf", ctx()))
    }

    @Test
    fun tribeFragment_emptyAfterSanitization_returnsNull() {
        assertNull(SectionSearchQuery.fragmentFor("tribe:123", ctx()))
    }

    @Test
    fun offplanAndLegalityPair_haveNoFragment() {
        val context = ctx()
        assertNull(SectionSearchQuery.fragmentFor("offplan", context))
        assertNull(SectionSearchQuery.fragmentFor("legal", context))
        assertNull(SectionSearchQuery.fragmentFor("illegal", context))
    }

    // ── buildFor: null-fragment ids never produce a half-built query ───────────────────────

    @Test
    fun buildFor_nullFragmentIds_returnNullNotHalfBuiltQuery() {
        val context = ctx()
        assertNull(SectionSearchQuery.buildFor("offplan", context))
        assertNull(SectionSearchQuery.buildFor("legal", context))
        assertNull(SectionSearchQuery.buildFor("illegal", context))
        assertNull(SectionSearchQuery.buildFor("role:tribe_members", ctx(tribe = null)))
    }

    // ── buildFor: identity composition ──────────────────────────────────────────────────────

    @Test
    fun buildFor_commander_appendsColorIdentityInWubrgOrder() {
        val context = ctx(colors = setOf(ManaColor.G, ManaColor.W, ManaColor.B), format = DeckFormat.COMMANDER)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertTrue(built.contains("id<=WBG"), "expected WUBRG-ordered id<= clause, got: $built")
    }

    @Test
    fun buildFor_commanderCasual_getsIdentityButNoLegalityClause() {
        // Deck Wizard v4, W0.1 (G13/E9/R7): legality is ignored entirely for Casual formats, so
        // Commander Casual keeps the identity bound but drops `legal:commander` -- the same rule
        // isLegalForFormat now enforces at the predicate level.
        val context = ctx(colors = setOf(ManaColor.U), format = DeckFormat.COMMANDER_CASUAL)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertTrue(built.contains("id<=U"))
        assertFalse(built.contains("legal:commander"), "Commander Casual must not filter by legality (R7), got: $built")
    }

    @Test
    fun buildFor_standard_omitsColorIdentityClause() {
        val context = ctx(colors = setOf(ManaColor.W, ManaColor.U), format = DeckFormat.STANDARD)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertFalse(built.contains("id<="), "60-card formats must not be identity-bounded, got: $built")
        assertTrue(built.contains("legal:standard"))
    }

    @Test
    fun buildFor_colorlessOnlyIdentity_boundsToColorless() {
        // N4: a colourless commander is a real constraint -- both tabs must list only colourless cards.
        val context = ctx(colors = setOf(ManaColor.C), format = DeckFormat.COMMANDER)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertTrue(built.contains("id<=c"), "a C-only identity must bound to colourless, got: $built")
        assertTrue(SectionSearchQuery.buildFor("role:removal_spot", ctx(colors = emptySet(), format = DeckFormat.COMMANDER))!!.contains("id<=c"))
    }

    @Test
    fun toAdvancedQuery_commanderEmptyIdentity_addsColorlessIdentityCriterion() {
        val context = ctx(colors = emptySet(), format = DeckFormat.COMMANDER)
        val query = SectionSearchQuery.toAdvancedQuery("role:ramp", context)!!
        val criterion = query.criteria.filterIsInstance<com.mmg.manahub.core.model.SearchCriterion.ColorIdentity>().single()
        assertEquals(setOf("C"), criterion.colors)
        assertEquals(com.mmg.manahub.core.model.ColorMatchMode.AT_MOST, criterion.mode)
        // The shared renderer spells a C-only AT_MOST set as `id=c` -- the same colourless-only pool.
        assertEquals("id=c", com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase()
            .let { it(com.mmg.manahub.core.model.AdvancedSearchQuery(listOf(criterion))) })
        val gate = SectionSearchQuery.localStructuralGate(context)
        assertTrue(gate(card(id = "rock", name = "Rock", colorIdentity = emptyList())))
        assertFalse(gate(card(id = "bolt", name = "Bolt", colorIdentity = listOf("R"))))
        assertFalse(SectionSearchQuery.toAdvancedQuery("role:ramp", ctx(colors = emptySet(), format = DeckFormat.STANDARD))!!
            .criteria.any { it is com.mmg.manahub.core.model.SearchCriterion.ColorIdentity })
    }

    // ── buildFor: legality composition ──────────────────────────────────────────────────────

    @Test
    fun buildFor_casualAndDraft_neverGetLegalClause() {
        val casual = SectionSearchQuery.buildFor("role:removal_spot", ctx(format = DeckFormat.CASUAL))
        val draft = SectionSearchQuery.buildFor("role:removal_spot", ctx(format = DeckFormat.DRAFT))
        assertNotNull(casual)
        assertNotNull(draft)
        assertFalse(casual.contains("legal:"), "CASUAL must never get a legal: clause, got: $casual")
        assertFalse(draft.contains("legal:"), "DRAFT must never get a legal: clause, got: $draft")
    }

    @Test
    fun buildFor_everySixtyCardFormat_getsItsOwnLegalityToken() {
        val expectations = mapOf(
            DeckFormat.STANDARD to "legal:standard",
            DeckFormat.PIONEER to "legal:pioneer",
            DeckFormat.MODERN to "legal:modern",
            DeckFormat.LEGACY to "legal:legacy",
            DeckFormat.VINTAGE to "legal:vintage",
            DeckFormat.PAUPER to "legal:pauper",
        )
        expectations.forEach { (format, token) ->
            val built = SectionSearchQuery.buildFor("role:removal_spot", ctx(colors = emptySet(), format = format))
            assertNotNull(built, "format=$format")
            assertTrue(built.contains(token), "format=$format expected '$token' in: $built")
        }
    }

    @Test
    fun buildFor_curveAndManaBaseIds_neverDoubleTLand() {
        val context = ctx(format = DeckFormat.STANDARD)
        val mv = SectionSearchQuery.buildFor("mv:3", context)
        val produces = SectionSearchQuery.buildFor("produces:R", context)
        assertNotNull(mv)
        assertNotNull(produces)
        assertEquals(1, Regex("t:land").findAll(produces).count(), "produces: id must carry exactly one t:land, got: $produces")
        assertEquals(1, Regex("-t:land").findAll(mv).count(), "mv: id must carry exactly one -t:land, got: $mv")
    }

    // ── translate() as a standalone, directly-testable function ────────────────────────────

    @Test
    fun translate_multipleRulesOfMultipleClausesEach_wrapsEachRuleThenOrsOnce() {
        val rules = listOf(
            com.mmg.manahub.core.model.DetectionRule(allOf = listOf("a", "b")),
            com.mmg.manahub.core.model.DetectionRule(allOf = listOf("c")),
        )
        assertEquals(
            "(oracle:\"a\" oracle:\"b\" or oracle:\"c\")",
            SectionSearchQuery.translate(rules),
        )
    }

    @Test
    fun translate_noneOfAndTypeLineNoneOf_negate() {
        val rules = listOf(
            com.mmg.manahub.core.model.DetectionRule(
                allOf = listOf("a"),
                noneOf = listOf("b"),
                typeLineNoneOf = listOf("land"),
            ),
        )
        assertEquals(
            "oracle:\"a\" -oracle:\"b\" -t:land",
            SectionSearchQuery.translate(rules),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  toAdvancedQuery — Suggestions Tab UI Polish plan, W11/D8. This is the acceptance bar for
    //  the "100% structured translation, no raw-text fallback" mandate: every section id with a
    //  real fragmentFor()/buildFor() result must resolve to a non-degenerate, correctly-populated
    //  AdvancedSearchQuery, and (where directly comparable) the SAME Scryfall string
    //  BuildScryfallQueryUseCase renders from that structured query must be functionally
    //  equivalent to the original curated fragment -- not just "some criteria exist".
    // ═══════════════════════════════════════════════════════════════════════════════

    private val buildQuery = com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase()

    /** Every RoleKey ArchetypeData references (36, live-derived, same set
     * [everyArchetypeDataRoleKey_yieldsANonBlankFragment] already covers for the string path) plus
     * `tribe_members` -- every `role:<key>`/`fingerprint:<key>` id must yield a non-null,
     * non-empty AdvancedSearchQuery when the context can resolve it (tribe_members needs a
     * dominantTribe, supplied here for a genuine coverage check). */
    @Test
    fun toAdvancedQuery_everyRoleKey_yieldsNonDegenerateQuery() {
        val context = ctx(tribe = "elf")
        val missing = (allArchetypeDataRoleKeys + "tribe_members").filter { key ->
            val query = SectionSearchQuery.toAdvancedQuery("role:$key", context)
            query == null || query.isEmpty()
        }
        assertTrue(missing.isEmpty(), "RoleKeys with no toAdvancedQuery mapping: $missing")
    }

    @Test
    fun toAdvancedQuery_everyEngineAxis_yieldsNonDegenerateQueryBothSides() {
        val context = ctx()
        val axes = listOf(
            "LIFE", "DEATH", "TOKENS", "COUNTERS", "LANDFALL", "GRAVEYARD", "ETB", "SPELLS",
            "ARTIFACTS", "ENCHANTMENTS", "ATTACHED", "ATTACK", "PLANESWALKERS", "GROUP", "ENGINE",
        )
        val missing = axes.filter { axis ->
            val producers = SectionSearchQuery.toAdvancedQuery("engine:$axis:producers", context)
            val payoffs = SectionSearchQuery.toAdvancedQuery("engine:$axis:payoffs", context)
            producers == null || producers.isEmpty() || payoffs == null || payoffs.isEmpty()
        }
        assertTrue(missing.isEmpty(), "engine axes with no toAdvancedQuery mapping on one side: $missing")
    }

    @Test
    fun toAdvancedQuery_roleTribeMembers_withNoDominantTribe_returnsNull() {
        assertNull(SectionSearchQuery.toAdvancedQuery("role:tribe_members", ctx(tribe = null)))
    }

    @Test
    fun toAdvancedQuery_manaBaseCurveAndTribeSections_yieldNonDegenerateQueries() {
        val context = ctx(tribe = "elf")
        val ids = buildList {
            add("mana_rock"); add("mana_dork")
            ManaColor.entries.forEach { add("produces:${it.symbol}") }
            (0..6).forEach { add("mv:$it") }
            add("mv:7plus")
            add("${TribeDeriver.TRIBE_PREFIX}elf")
        }
        val missing = ids.filter { id ->
            val query = SectionSearchQuery.toAdvancedQuery(id, context)
            query == null || query.isEmpty()
        }
        assertTrue(missing.isEmpty(), "Section ids with no toAdvancedQuery mapping: $missing")
    }

    @Test
    fun toAdvancedQuery_offplanLegalIllegal_returnNull_sameAsFragmentFor() {
        val context = ctx()
        assertNull(SectionSearchQuery.toAdvancedQuery("offplan", context))
        assertNull(SectionSearchQuery.toAdvancedQuery("legal", context))
        assertNull(SectionSearchQuery.toAdvancedQuery("illegal", context))
    }

    // ── Equivalence: BuildScryfallQueryUseCase(toAdvancedQuery(...)) vs. the curated fragment ──

    // Every equivalence test below uses DRAFT format -- it has no Scryfall legality list AND
    // (per ArchetypeFormat.of) is never COMMANDER, so BOTH identityCriterion/identityClause and
    // legalityCriterion/legalityClause resolve to null regardless of the context's colors. This
    // isolates the CATEGORY criteria alone for a true byte-for-byte comparison against fragmentFor
    // (which never includes identity/legality either) -- ctx()'s own DEFAULT colors (W, U) and
    // format (COMMANDER) would otherwise silently add extra criteria to every one of these.

    @Test
    fun toAdvancedQuery_curveBuckets_matchCuratedFragmentByteForByte() {
        val context = ctx(format = DeckFormat.DRAFT)
        (0..6).forEach { mv ->
            val query = SectionSearchQuery.toAdvancedQuery("mv:$mv", context)!!
            assertEquals("mv=$mv -t:land", buildQuery(query))
        }
        val query7plus = SectionSearchQuery.toAdvancedQuery("mv:7plus", context)!!
        assertEquals("mv>=7 -t:land", buildQuery(query7plus))
    }

    @Test
    fun toAdvancedQuery_manaFix_matchesCuratedFragmentByteForByte() {
        val query = SectionSearchQuery.toAdvancedQuery("role:mana_fix", ctx(format = DeckFormat.DRAFT))!!
        assertEquals("produces>=2", buildQuery(query))
    }

    @Test
    fun toAdvancedQuery_producesColor_rendersLandAndColorClause() {
        // Case-insensitive on Scryfall (existing ColorIdentity/Colors criteria already lowercase
        // by the same convention) -- not byte-identical to the curated "t:land produces:W", but
        // functionally equivalent.
        val query = SectionSearchQuery.toAdvancedQuery("produces:W", ctx(format = DeckFormat.DRAFT))!!
        assertEquals("t:land produces:w", buildQuery(query))
    }

    @Test
    fun toAdvancedQuery_directOracleTags_routeThroughCardFunction() {
        val context = ctx(format = DeckFormat.DRAFT)
        val removalSpot = SectionSearchQuery.toAdvancedQuery("role:removal_spot", context)!!
        assertEquals(
            listOf(com.mmg.manahub.core.model.SearchCriterion.CardFunction(setOf("spot-removal"))),
            removalSpot.criteria,
        )
    }

    /** The 3 dictionary-translated roles re-verified (W11 Step 1) to now have a real, closed-list
     * `function:` tag -- must route through CardFunction, NOT OracleTerms. */
    @Test
    fun toAdvancedQuery_reVerifiedRealTagRoles_routeThroughCardFunction() {
        val context = ctx(format = DeckFormat.DRAFT)
        assertEquals(
            listOf(com.mmg.manahub.core.model.SearchCriterion.CardFunction(setOf("sacrifice-outlet"))),
            SectionSearchQuery.toAdvancedQuery("role:sac_outlet", context)!!.criteria,
        )
        assertEquals(
            listOf(com.mmg.manahub.core.model.SearchCriterion.CardFunction(setOf("theft"))),
            SectionSearchQuery.toAdvancedQuery("role:clone_theft_effect", context)!!.criteria,
        )
        assertEquals(
            listOf(com.mmg.manahub.core.model.SearchCriterion.CardFunction(setOf("typal"))),
            SectionSearchQuery.toAdvancedQuery("role:tribe_payoff", context)!!.criteria,
        )
    }

    /** The remaining 7 dictionary-translated roles: OracleTerms rendered through
     * BuildScryfallQueryUseCase must be BYTE-IDENTICAL to SectionSearchQuery.translate's own
     * output for the SAME DetectionRule list -- proves the structured path and the string path
     * stay in lockstep (both read DICTIONARY_TRANSLATED_RULES, never a second hand-typed copy). */
    @Test
    fun toAdvancedQuery_oracleTermsRoles_matchTranslateByteForByte() {
        val context = ctx(format = DeckFormat.DRAFT)
        val stillOracleTerms = listOf(
            "token_generator", "artifact_payoff", "enchantment_payoff", "spell_payoff",
            "graveyard_enabler", "aura_buff", "etb_payoff",
        )
        stillOracleTerms.forEach { key ->
            val expected = SectionSearchQuery.fragmentFor("role:$key", context)
            val query = SectionSearchQuery.toAdvancedQuery("role:$key", context)!!
            assertEquals(expected, buildQuery(query), "role:$key structured output diverged from the string path")
        }
    }

    // ── Identity + legality composition (mirrors buildFor's own extra clauses) ──────────────

    @Test
    fun toAdvancedQuery_commanderWithColorIdentity_addsColorIdentityCriterion() {
        val context = ctx(colors = setOf(ManaColor.W, ManaColor.U), format = DeckFormat.COMMANDER)
        val query = SectionSearchQuery.toAdvancedQuery("role:ramp", context)!!
        assertTrue(
            query.criteria.contains(
                com.mmg.manahub.core.model.SearchCriterion.ColorIdentity(
                    colors = setOf("W", "U"),
                    mode = com.mmg.manahub.core.model.ColorMatchMode.AT_MOST,
                )
            ),
            "expected a ColorIdentity(W,U) criterion in $query",
        )
        // The subset meaning is now carried by the mode and rendered as an EXPLICIT operator, so
        // this matches buildFor's own "id<=WU" character for character instead of relying on `id:`
        // being Scryfall's at-most alias for identity searches.
        assertEquals("id<=wu", com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase()
            .let { it(com.mmg.manahub.core.model.AdvancedSearchQuery(listOf(query.criteria.first { c -> c is com.mmg.manahub.core.model.SearchCriterion.ColorIdentity }))) })
    }

    @Test
    fun toAdvancedQuery_standardFormat_addsFormatCriterion() {
        val context = ctx(format = DeckFormat.STANDARD)
        val query = SectionSearchQuery.toAdvancedQuery("role:ramp", context)!!
        assertTrue(
            query.criteria.contains(com.mmg.manahub.core.model.SearchCriterion.Format(format = listOf("standard"), legal = true)),
            "expected a Format(standard) criterion in $query",
        )
    }

    @Test
    fun toAdvancedQuery_draftFormat_addsNoFormatCriterion() {
        val context = ctx(format = DeckFormat.DRAFT)
        val query = SectionSearchQuery.toAdvancedQuery("role:ramp", context)!!
        assertFalse(query.criteria.any { it is com.mmg.manahub.core.model.SearchCriterion.Format })
    }

    // ── Deck Wizard 60-card wave (v6), plan §5 Phase 3.2 -- the new "lands" section ─────────────

    @Test
    fun landsSection_fragmentIsPlainLandType() {
        assertEquals("t:land", SectionSearchQuery.fragmentFor("lands", ctx()))
    }

    @Test
    fun landsSection_criteriaIsCardType_land() {
        val query = SectionSearchQuery.toAdvancedQuery("lands", ctx())
        assertNotNull(query)
        // Mirrors tribeCriteria's own shape: the FIRST criterion (category clause) is a single
        // CardType(setOf("land")), no exclude flag -- NOT curveCriteria's exclude=true variant.
        assertEquals(com.mmg.manahub.core.model.SearchCriterion.CardType(setOf("land")), query.criteria.first())
    }

}
