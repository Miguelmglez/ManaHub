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

    // ── fingerprint:<key> reuses the same translation as role:<key> ────────────────────────

    @Test
    fun fingerprintFragment_reusesRoleTranslation() {
        assertEquals(
            SectionSearchQuery.fragmentFor("role:sac_outlet", ctx()),
            SectionSearchQuery.fragmentFor("fingerprint:sac_outlet", ctx()),
        )
    }

    @Test
    fun fingerprintFragment_unknownKey_returnsNull() {
        assertNull(SectionSearchQuery.fragmentFor("fingerprint:equipment_matters", ctx()))
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
    fun buildFor_commanderCasual_alsoGetsIdentityAndCommanderLegality() {
        val context = ctx(colors = setOf(ManaColor.U), format = DeckFormat.COMMANDER_CASUAL)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertTrue(built.contains("id<=U"))
        assertTrue(built.contains("legal:commander"))
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
    fun buildFor_colorlessOnlyIdentity_omitsIdClause() {
        val context = ctx(colors = setOf(ManaColor.C), format = DeckFormat.COMMANDER)
        val built = SectionSearchQuery.buildFor("role:removal_spot", context)
        assertNotNull(built)
        assertFalse(built.contains("id<="), "a C-only identity must omit id<=, got: $built")
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

    // ── collectionTagKeysFor ─────────────────────────────────────────────────────────────────

    @Test
    fun collectionTagKeysFor_roleWithTagEquivalent_returnsTheKeyItself() {
        assertEquals(setOf("sac_outlet"), SectionSearchQuery.collectionTagKeysFor("role:sac_outlet"))
        assertEquals(setOf("mana_fix"), SectionSearchQuery.collectionTagKeysFor("role:mana_fix"))
    }

    @Test
    fun collectionTagKeysFor_roleWithNoTagEquivalent_returnsEmpty() {
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("role:removal_spot"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("role:removal_mass"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("role:finisher"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("role:equipment_or_aura"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("role:tribe_members"))
    }

    @Test
    fun collectionTagKeysFor_fingerprintDelegatesSameAsRole() {
        assertEquals(
            SectionSearchQuery.collectionTagKeysFor("role:token_generator"),
            SectionSearchQuery.collectionTagKeysFor("fingerprint:token_generator"),
        )
    }

    @Test
    fun collectionTagKeysFor_manaRockAndDork_returnOwnKey() {
        assertEquals(setOf("mana_rock"), SectionSearchQuery.collectionTagKeysFor("mana_rock"))
        assertEquals(setOf("mana_dork"), SectionSearchQuery.collectionTagKeysFor("mana_dork"))
    }

    @Test
    fun collectionTagKeysFor_structuralIds_returnEmpty() {
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("tribe:elf"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("produces:B"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("mv:3"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("legal"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("illegal"))
        assertEquals(emptySet(), SectionSearchQuery.collectionTagKeysFor("offplan"))
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
    fun toAdvancedQuery_everyRoleKey_asFingerprint_yieldsNonDegenerateQuery() {
        // fingerprint:<key> shares ROLE_CRITERIA with role:<key> for every key EXCEPT
        // tribe_members (SYNERGY never emits a "fingerprint:tribe_members" section id -- that
        // concept only exists under the PLAN_ROLES "role:" prefix, so it's excluded from the
        // iterated set here rather than expecting ROLE_CRITERIA to carry a meaningless entry).
        val context = ctx()
        val missing = (allArchetypeDataRoleKeys - "tribe_members").filter { key ->
            val query = SectionSearchQuery.toAdvancedQuery("fingerprint:$key", context)
            query == null || query.isEmpty()
        }
        assertTrue(missing.isEmpty(), "RoleKeys with no fingerprint toAdvancedQuery mapping: $missing")
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
}
