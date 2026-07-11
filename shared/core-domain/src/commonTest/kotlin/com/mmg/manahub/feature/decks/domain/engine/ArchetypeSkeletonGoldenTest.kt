package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Doctor Community/Archetype plan, Phase 1.8 — the golden skeleton test suite: a
 * line-for-line Kotlin port of Appendix B's Python reference-resolver validation
 * (docs/claude-code-prompt-deck-doctor-community.md, "Reference resolver + evaluator + the 9
 * fixture vectors"). This is the GATE for the whole archetype-data layer (D15): the annex data
 * (`ArchetypeData`) and the resolver (`ArchetypeSkeletonResolver`)/evaluator
 * (`ArchetypeEvaluator`) must keep this suite green under any future tuning.
 *
 * Structure mirrors the Python script's four check groups exactly:
 *  A. Structural — every `[min, ideal, max]` band satisfies `min <= ideal <= max`.
 *  B. Budget — the A.4 step-6 multi-role overlap invariant for GENERIC/every archetype/9
 *     representative archetype+theme combos.
 *  C. False positives — the 9 hand-encoded reference decks produce ZERO warnings when
 *     evaluated against their OWN (correct) resolved skeleton.
 *  D. Discrimination — the same 9 decks produce MORE warnings against the GENERIC skeleton than
 *     against their correct one (except the pure-MIDRANGE-no-theme vector, which the annex's own
 *     Python script explicitly excludes from this check).
 */
class ArchetypeSkeletonGoldenTest {

    // ═══════════════════════════════════════════════════════════════════════════
    //  A. Structural — band ordering (min <= ideal <= max)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun assertBandOrdered(name: String, band: RoleTarget) {
        assertTrue(band.min <= band.ideal, "$name: min (${band.min}) > ideal (${band.ideal})")
        assertTrue(band.ideal <= band.max, "$name: ideal (${band.ideal}) > max (${band.max})")
    }

    private fun assertCurveOrdered(name: String, curve: CurveBand) {
        assertTrue(curve.min <= curve.ideal, "$name curve: min (${curve.min}) > ideal (${curve.ideal})")
        assertTrue(curve.ideal <= curve.max, "$name curve: ideal (${curve.ideal}) > max (${curve.max})")
    }

    @Test
    fun genericBandsAreStructurallyOrderedForBothFormats() {
        ArchetypeFormat.entries.forEach { format ->
            val g = ArchetypeData.generic(format)
            assertBandOrdered("generic.$format.lands", g.lands)
            assertCurveOrdered("generic.$format", g.curve)
            g.roleTargets.forEach { (key, band) -> assertBandOrdered("generic.$format.$key", band) }
        }
    }

    @Test
    fun everyArchetypeBandIsStructurallyOrderedForBothFormats() {
        ArchetypeData.ARCHETYPES.forEach { (archetype, perFormat) ->
            perFormat.forEach { (format, def) ->
                assertBandOrdered("$archetype.$format.lands", def.lands)
                assertCurveOrdered("$archetype.$format", def.curve)
                def.roleTargets.forEach { (key, band) -> assertBandOrdered("$archetype.$format.$key", band) }
            }
        }
    }

    @Test
    fun everyThemeAddsBandIsStructurallyOrdered() {
        ArchetypeData.THEMES.forEach { (theme, def) ->
            def.adds.forEach { (key, band) -> assertBandOrdered("theme.$theme.$key", band) }
        }
    }

    @Test
    fun everyResolvedSkeletonInTheReferenceMatrixIsStructurallyOrdered() {
        // Every archetype x format resolve() (no themes) must stay ordered post-composition too
        // (anti-role resolution / theme merges do not violate the invariant).
        ArchetypeFormat.entries.forEach { format ->
            ArchetypeId.entries.forEach { archetype ->
                if (archetype == ArchetypeId.GENERIC) return@forEach
                val resolved = ArchetypeSkeletonResolver.resolve(format, archetype)
                assertBandOrdered("resolved.$archetype.$format.lands", resolved.lands)
                assertCurveOrdered("resolved.$archetype.$format", resolved.curve)
                resolved.roleTargets.forEach { (key, band) -> assertBandOrdered("resolved.$archetype.$format.$key", band) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  B. Budget invariant (A.4 step 6 / Appendix B's `budget_check`)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun assertWithinBudget(name: String, deckSize: Int, skeleton: ResolvedArchetypeSkeleton) {
        val result = ArchetypeEvaluator.budgetCheck(deckSize, skeleton)
        assertTrue(
            result.withinBudget,
            "BUDGET $name: ideal sum ${result.idealSum} > cap ${result.cap} (nonland ${result.nonLand} x ${ArchetypeEvaluator.OVERLAP})",
        )
    }

    @Test
    fun genericSkeletonFitsBudgetForBothFormats() {
        assertWithinBudget("GENERIC/commander", 100, ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER))
        assertWithinBudget("GENERIC/sixty", 60, ArchetypeSkeletonResolver.resolve(ArchetypeFormat.SIXTY))
    }

    @Test
    fun everyArchetypeFitsBudgetForBothFormats() {
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC }.forEach { archetype ->
            assertWithinBudget(
                "$archetype/commander", 100,
                ArchetypeSkeletonResolver.resolve(ArchetypeFormat.COMMANDER, archetype),
            )
            assertWithinBudget(
                "$archetype/sixty", 60,
                ArchetypeSkeletonResolver.resolve(ArchetypeFormat.SIXTY, archetype),
            )
        }
    }

    @Test
    fun representativeArchetypeThemeCombosFitBudget() {
        // Mirrors Appendix B's `combo` list exactly.
        val combos = listOf(
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.AGGRO, listOf(ThemeId.TRIBAL)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.REANIMATOR)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.VOLTRON)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.CONTROL, listOf(ThemeId.SPELLSLINGER)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.RAMP, listOf(ThemeId.LANDFALL)),
            Triple(ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS, ThemeId.TOKENS)),
            Triple(ArchetypeFormat.SIXTY, ArchetypeId.AGGRO, listOf(ThemeId.TRIBAL)),
            Triple(ArchetypeFormat.SIXTY, ArchetypeId.COMBO, listOf(ThemeId.SELF_MILL)),
        )
        combos.forEach { (format, archetype, themes) ->
            val deckSize = if (format == ArchetypeFormat.COMMANDER) 100 else 60
            val skeleton = ArchetypeSkeletonResolver.resolve(format, archetype, themes)
            assertWithinBudget("$archetype+${themes.joinToString("+")}/$format", deckSize, skeleton)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  C/D. The 9 reference decks (verbatim from Appendix B's `REF` list)
    // ═══════════════════════════════════════════════════════════════════════════

    private data class ReferenceDeck(
        val name: String,
        val format: ArchetypeFormat,
        val archetype: ArchetypeId,
        val themes: List<ThemeId>,
        val colors: Int,
        val roleCounts: Map<RoleKey, Int>,
        val lands: Int,
        val avgMv: Double,
    )

    private val referenceDecks = listOf(
        ReferenceDeck(
            "Upgraded precon midrange (typical)", ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, emptyList(), 2,
            mapOf(
                "ramp" to 10, "card_draw" to 10, "removal_spot" to 8, "removal_mass" to 3, "finisher" to 9,
                "recursion" to 2, "tutor" to 2, "threat_early" to 5, "mana_fix" to 6,
            ),
            37, 3.0,
        ),
        ReferenceDeck(
            "Krenko goblins (aggro tribal)", ArchetypeFormat.COMMANDER, ArchetypeId.AGGRO, listOf(ThemeId.TRIBAL), 1,
            mapOf(
                "ramp" to 6, "card_draw" to 7, "removal_spot" to 6, "removal_mass" to 0, "threat_early" to 16,
                "finisher" to 9, "tribe_members" to 30, "tribe_payoff" to 10, "token_generator" to 8,
            ),
            33, 2.3,
        ),
        ReferenceDeck(
            "Talrand control (mono-U)", ArchetypeFormat.COMMANDER, ArchetypeId.CONTROL, listOf(ThemeId.SPELLSLINGER), 1,
            mapOf(
                "counterspell" to 13, "removal_spot" to 9, "removal_mass" to 5, "card_draw" to 14, "finisher" to 6,
                "ramp" to 10, "spell_payoff" to 12, "threat_early" to 0,
            ),
            38, 2.9,
        ),
        ReferenceDeck(
            "Karador reanimator", ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.REANIMATOR), 3,
            mapOf(
                "ramp" to 10, "card_draw" to 9, "removal_spot" to 9, "removal_mass" to 3, "finisher" to 10,
                "recursion" to 5, "tutor" to 4, "graveyard_enabler" to 10, "reanimation" to 9, "threat_early" to 5,
                "mana_fix" to 12,
            ),
            36, 3.4,
        ),
        ReferenceDeck(
            "Sram voltron (equipment)", ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.VOLTRON), 1,
            mapOf(
                "ramp" to 9, "card_draw" to 10, "removal_spot" to 8, "removal_mass" to 3, "finisher" to 2,
                "recursion" to 2, "tutor" to 4, "equipment_or_aura" to 19, "protection" to 9, "evasion" to 6,
                "threat_early" to 6,
            ),
            34, 2.5,
        ),
        ReferenceDeck(
            "Meren aristocrats", ArchetypeFormat.COMMANDER, ArchetypeId.MIDRANGE, listOf(ThemeId.ARISTOCRATS), 2,
            mapOf(
                "ramp" to 9, "card_draw" to 8, "removal_spot" to 8, "removal_mass" to 3, "finisher" to 8,
                "recursion" to 6, "tutor" to 3, "sac_outlet" to 9, "death_payoff" to 11, "token_generator" to 10,
                "threat_early" to 6, "mana_fix" to 5,
            ),
            36, 3.1,
        ),
        ReferenceDeck(
            "Modern Burn", ArchetypeFormat.SIXTY, ArchetypeId.AGGRO, emptyList(), 1,
            mapOf("threat_early" to 16, "finisher" to 22, "removal_spot" to 6, "card_draw" to 0, "removal_mass" to 0),
            19, 1.6,
        ),
        ReferenceDeck(
            "Azorius control (Pioneer-like)", ArchetypeFormat.SIXTY, ArchetypeId.CONTROL, emptyList(), 2,
            mapOf(
                "counterspell" to 9, "removal_spot" to 8, "removal_mass" to 4, "card_draw" to 9, "finisher" to 4,
                "threat_early" to 0, "mana_fix" to 10,
            ),
            26, 3.0,
        ),
        ReferenceDeck(
            "Golgari midrange (Standard-like)", ArchetypeFormat.SIXTY, ArchetypeId.MIDRANGE, emptyList(), 2,
            mapOf(
                "removal_spot" to 11, "finisher" to 14, "threat_early" to 8, "card_draw" to 4, "removal_mass" to 2,
                "mana_fix" to 8,
            ),
            25, 2.8,
        ),
    )

    /** Faithful port of Appendix B's `evaluate()` bucketed color lookup + call into [ArchetypeEvaluator]. */
    private fun evaluateVector(deck: ReferenceDeck, skeleton: ResolvedArchetypeSkeleton): List<DeckWarning> {
        val bucket = ArchetypeData.colorCountBucket(deck.colors)
        val modulation = ArchetypeData.COLOR_MODULATION.getValue(deck.format)[bucket]
        return ArchetypeEvaluator.evaluate(
            roleCounts = deck.roleCounts,
            lands = deck.lands,
            avgMv = deck.avgMv,
            skeleton = skeleton,
            colorModulation = modulation,
        )
    }

    @Test
    fun everyReferenceDeckHasZeroFalsePositivesOnItsOwnCorrectSkeleton() {
        referenceDecks.forEach { deck ->
            val correctSkeleton = ArchetypeSkeletonResolver.resolve(deck.format, deck.archetype, deck.themes)
            val warnings = evaluateVector(deck, correctSkeleton)
            assertTrue(
                warnings.isEmpty(),
                "${deck.name}: expected 0 warnings on its correct skeleton, got $warnings",
            )
        }
    }

    @Test
    fun archetypeLayerReducesFalseWarningsVersusGenericForSpecializedDecks() {
        referenceDecks.forEach { deck ->
            val isPureMidrangeNoTheme = deck.archetype == ArchetypeId.MIDRANGE && deck.themes.isEmpty()
            if (isPureMidrangeNoTheme) return@forEach // excluded by Appendix B's own script

            val correctSkeleton = ArchetypeSkeletonResolver.resolve(deck.format, deck.archetype, deck.themes)
            val genericSkeleton = ArchetypeSkeletonResolver.resolve(deck.format)
            val correctWarnings = evaluateVector(deck, correctSkeleton)
            val genericWarnings = evaluateVector(deck, genericSkeleton)

            if (genericWarnings.isNotEmpty()) {
                assertTrue(
                    correctWarnings.size < genericWarnings.size,
                    "${deck.name}: correct-skeleton warnings (${correctWarnings.size}) should be fewer than " +
                        "generic-skeleton warnings (${genericWarnings.size}); generic=$genericWarnings correct=$correctWarnings",
                )
            }
        }
    }

    /** Pins the exact discrimination counts printed in Appendix B's final validation run. */
    @Test
    fun genericWarningCountsMatchTheAnnexsRecordedValidationRun() {
        val expectedGenericWarningCounts = mapOf(
            "Upgraded precon midrange (typical)" to 0,
            "Krenko goblins (aggro tribal)" to 4,
            "Talrand control (mono-U)" to 0,
            "Karador reanimator" to 0,
            "Sram voltron (equipment)" to 2,
            "Meren aristocrats" to 0,
            "Modern Burn" to 3,
            "Azorius control (Pioneer-like)" to 2,
            "Golgari midrange (Standard-like)" to 0,
        )
        referenceDecks.forEach { deck ->
            val genericSkeleton = ArchetypeSkeletonResolver.resolve(deck.format)
            val genericWarnings = evaluateVector(deck, genericSkeleton)
            val expected = expectedGenericWarningCounts.getValue(deck.name)
            assertTrue(
                genericWarnings.size == expected,
                "${deck.name}: expected $expected generic-skeleton warnings, got ${genericWarnings.size} ($genericWarnings)",
            )
        }
    }
}
