package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.SuggestedTag
import com.mmg.manahub.core.domain.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for DeckScorer:
 *   profile()     — fingerprint, roleCounts, avgCmc, histogram
 *   fit()         — synergy, roleNeed, curve, color (hard filter), redundancy, reasons
 *   rankAdds()    — hard filter + power floor + ordering
 *   rankCuts()    — land exclusion, protectedIds, combo protection, ascending order
 *   evaluate()    — role coverage, land warnings, CurveTooHigh/Low, LowSynergyDensity
 */
class DeckScorerTest {

    private lateinit var scorer: DeckScorer

    @Before
    fun setUp() {
        // Use a fixed power resolver (0.5 for all) so tests are deterministic.
        scorer = DeckScorer(RoleClassifier(), fixedPower(0.5f))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 1: profile() — basic correctness
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty mainboard when profile then avgCmc is 0 and nonLandCount is 0`() {
        // Arrange / Act
        val p = scorer.profile(
            mainboard     = emptyList(),
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            seedTags      = emptyList(),
        )

        // Assert
        assertEquals(0.0, p.avgCmc, 0.001)
        assertEquals(0, p.nonLandCount)
        assertTrue(p.roleCounts.isEmpty())
    }

    @Test
    fun `given mainboard with only lands when profile then nonLandCount is 0`() {
        val mainboard = List(37) { i -> entry(landCard(id = "land-$i"), quantity = 1) }
        val p = scorer.profile(mainboard, DeckFormat.COMMANDER, setOf(ManaColor.G), emptyList())

        assertEquals(0, p.nonLandCount)
        assertEquals(0.0, p.avgCmc, 0.001)
    }

    @Test
    fun `given mainboard with spells when profile then avgCmc is weighted average`() {
        // 2 x cmc-2 spell + 1 x cmc-4 spell = (2*2 + 1*4)/3 = 8/3 ≈ 2.667
        val mainboard = listOf(
            entry(card(id = "s1", cmc = 2.0), quantity = 2),
            entry(card(id = "s2", cmc = 4.0), quantity = 1),
        )
        val p = scorer.profile(mainboard, DeckFormat.COMMANDER, setOf(ManaColor.U), emptyList())

        assertEquals(8.0 / 3.0, p.avgCmc, 0.001)
        assertEquals(3, p.nonLandCount)
    }

    @Test
    fun `given mainboard with ramp cards when profile then RAMP count is sum of quantities`() {
        val mainboard = listOf(
            entry(card(id = "rock", tags = listOf(tagManaRock)), quantity = 3),
            entry(card(id = "dork", tags = listOf(tagManaDork), typeLine = "Creature", power = "1"), quantity = 2),
        )
        val p = scorer.profile(mainboard, DeckFormat.COMMANDER, setOf(ManaColor.G), emptyList())

        assertEquals(5f, p.roleCounts[DeckRole.RAMP]!!, 0.001f)
    }

    @Test
    fun `given seed tags when profile then tagFingerprint contains seed keys`() {
        val seeds = listOf(tagTokens, tagTribal)
        val p = scorer.profile(emptyList(), DeckFormat.COMMANDER, setOf(ManaColor.G), seeds)

        assertTrue(p.tagFingerprint.containsKey(tagTokens.key))
        assertTrue(p.tagFingerprint.containsKey(tagTribal.key))
    }

    @Test
    fun `given mainboard when profile then curveHistogram counts non-lands by CMC bucket`() {
        val mainboard = listOf(
            entry(card(id = "c1", cmc = 2.0), quantity = 3),
            entry(card(id = "c2", cmc = 3.0), quantity = 2),
            entry(landCard(id = "l1"), quantity = 5), // must be excluded
        )
        val p = scorer.profile(mainboard, DeckFormat.COMMANDER, setOf(ManaColor.U), emptyList())

        assertEquals(3, p.curveHistogram[2])
        assertEquals(2, p.curveHistogram[3])
        assertFalse("Lands must not appear in curve histogram", p.curveHistogram.containsKey(0))
    }

    @Test
    fun `given a CMC greater than 7 when profile then bucket is capped at 7`() {
        val mainboard = listOf(entry(card(id = "big", cmc = 10.0), quantity = 2))
        val p = scorer.profile(mainboard, DeckFormat.COMMANDER, setOf(ManaColor.U), emptyList())

        assertEquals(2, p.curveHistogram[7])
        assertFalse(p.curveHistogram.containsKey(10))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 2: fit() — synergy component
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card matching fingerprint tag when fit then synergy is positive and SynergyMatch reason added`() {
        // Arrange: profile fingerprint has tagTokens at weight 1.0
        val profile = minimalProfile(
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
            colorIdentity  = setOf(ManaColor.W),
        )
        val c = card(id = "s1", colorIdentity = listOf("W"), tags = listOf(tagTokens))

        // Act
        val fit = scorer.fit(c, profile, isOwned = false)

        // Assert
        assertTrue(fit.components.synergy > 0f)
        assertTrue(fit.reasons.any { it is ScoreReason.SynergyMatch })
    }

    @Test
    fun `given card with no matching tags when fit then OffStrategy reason added`() {
        // Profile about tokens; card has no matching tags
        val profile = minimalProfile(
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
            colorIdentity  = setOf(ManaColor.W),
        )
        val c = card(id = "off", colorIdentity = listOf("W"))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.reasons.any { it is ScoreReason.OffStrategy })
    }

    @Test
    fun `given empty fingerprint and no seed when fit then synergy is 0_5 and no OffStrategy`() {
        val profile = minimalProfile(tagFingerprint = emptyMap(), seedTags = emptyList())
        val c = card(id = "neutral")

        val fit = scorer.fit(c, profile, isOwned = false)

        // When fingerprint and seeds are both empty the scorer returns 0.5 (neutral)
        assertEquals(0.5f, fit.components.synergy, 0.001f)
        assertFalse(fit.reasons.any { it is ScoreReason.OffStrategy })
    }

    @Test
    fun `given userTag source weight is higher than auto tag source weight for same key`() {
        // A card with a userTag matching the fingerprint should score higher than the same
        // card with only an auto tag.
        //
        // Math (fp=0.5, single tag):
        //   userTag: weighted=1.2*0.5=0.6, ownWeight=1.2, score=0.6/sqrt(1.2)≈0.547
        //   autoTag: weighted=1.0*0.5=0.5, ownWeight=1.0, score=0.5/sqrt(1.0) =0.5
        //
        // fp=1.0 would coerce both to 1.0, hiding the difference — use fp=0.5 instead.
        val profile = minimalProfile(tagFingerprint = mapOf(tagTokens.key to 0.5f))

        val cUserTag = card(id = "u1", userTags = listOf(tagTokens))
        val cAutoTag = card(id = "u2", tags    = listOf(tagTokens))

        val fitUser = scorer.fit(cUserTag, profile, isOwned = false)
        val fitAuto = scorer.fit(cAutoTag, profile, isOwned = false)

        assertTrue(
            "userTag (w=1.2) should produce higher synergy than autoTag (w=1.0) at fp=0.5",
            fitUser.components.synergy > fitAuto.components.synergy
        )
    }

    @Test
    fun `given suggestedTag source weight scaled by confidence when fit then lower confidence reduces synergy`() {
        val profile = minimalProfile(tagFingerprint = mapOf(tagTokens.key to 1.0f))

        val cHighConf = card(id = "h", suggestedTags = listOf(suggestedTag(tagTokens, confidence = 1.0f)))
        val cLowConf  = card(id = "l", suggestedTags = listOf(suggestedTag(tagTokens, confidence = 0.4f)))

        val fitHigh = scorer.fit(cHighConf, profile, isOwned = false)
        val fitLow  = scorer.fit(cLowConf,  profile, isOwned = false)

        assertTrue(fitHigh.components.synergy > fitLow.components.synergy)
    }

    @Test
    fun `given duplicate tag key in userTags and tags when fit then only highest-weight source counted`() {
        // Both userTag and autoTag have the same key -> dedup keeps only userTag (1.2 weight)
        val profile = minimalProfile(tagFingerprint = mapOf(tagTokens.key to 1.0f))
        val cDedup  = card(id = "d1", tags = listOf(tagTokens), userTags = listOf(tagTokens))
        val cSingle = card(id = "d2", userTags = listOf(tagTokens))

        val fitDedup  = scorer.fit(cDedup,  profile, isOwned = false)
        val fitSingle = scorer.fit(cSingle, profile, isOwned = false)

        assertEquals(fitSingle.components.synergy, fitDedup.components.synergy, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 3: fit() — roleNeed component
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given RAMP role with big gap when fit then roleNeed is high and FillsGap reason added`() {
        // Arrange: Commander ideal RAMP = 11, current = 0 → need = 1.0
        val profile = minimalProfile(
            roleCounts    = mapOf(DeckRole.RAMP to 0),
            colorIdentity = setOf(ManaColor.G),
        )
        val c = card(id = "ramp1", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue("FillsGap reason expected", fit.reasons.any { it is ScoreReason.FillsGap })
        assertEquals(1.0f, fit.components.roleNeed, 0.001f)
    }

    @Test
    fun `given role already at ideal count when fit then roleNeed is 0`() {
        // Commander ideal RAMP = 11, current = 11 → need = 0
        val profile = minimalProfile(
            roleCounts    = mapOf(DeckRole.RAMP to 11),
            colorIdentity = setOf(ManaColor.G),
        )
        val c = card(id = "ramp2", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertEquals(0f, fit.components.roleNeed, 0.001f)
        assertFalse(fit.reasons.any { it is ScoreReason.FillsGap })
    }

    @Test
    fun `given gap below 0_4 threshold when fit then FillsGap reason NOT added`() {
        // need = (11 - 8) / 11 ≈ 0.27 which is < 0.4
        val profile = minimalProfile(roleCounts = mapOf(DeckRole.RAMP to 8))
        val c = card(id = "ramp3", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(fit.reasons.any { it is ScoreReason.FillsGap })
    }

    @Test
    fun `given FILLER role when fit then roleNeed is 0 because role is not functional`() {
        val profile = minimalProfile()
        val c = card(id = "filler1")  // no tags, no oracle, no creature body -> FILLER

        val fit = scorer.fit(c, profile, isOwned = false)

        assertEquals(0f, fit.components.roleNeed, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 4: fit() — curve component
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given low nonLandCount and low cmc when fit then curveScore is 0_9 and OnCurve reason`() {
        // nonLandCount < 10 AND cmc <= 2 → early-game bonus path
        val profile = minimalProfile(nonLandCount = 5, curveHistogram = emptyMap())
        val c = card(id = "c1", cmc = 1.0)

        val fit = scorer.fit(c, profile, isOwned = false)

        assertEquals(0.9f, fit.components.curve, 0.001f)
        assertTrue(fit.reasons.any { it is ScoreReason.OnCurve })
    }

    @Test
    fun `given empty bucket when fit then curveScore is 1_0 and CurveGap reason`() {
        // Histogram has only cmc=3 bucket occupied; cmc=2 bucket is empty → score=1.0
        val histogram = mapOf(3 to 10)
        val profile   = minimalProfile(nonLandCount = 20, curveHistogram = histogram)
        val c         = card(id = "c2", cmc = 2.0)

        val fit = scorer.fit(c, profile, isOwned = false)

        assertEquals(1.0f, fit.components.curve, 0.001f)
        assertTrue(fit.reasons.any { it is ScoreReason.CurveGap })
    }

    @Test
    fun `given crowded cmc bucket when fit then curveScore is near 0`() {
        // Card and histogram are both in bucket 2; here=10, maxBucket=10
        // score = 1 - 10/(10+1) ≈ 0.09
        val histogram = mapOf(2 to 10)
        val profile   = minimalProfile(nonLandCount = 20, curveHistogram = histogram)
        val c         = card(id = "c3", cmc = 2.0)

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue("Curve score should be very low", fit.components.curve < 0.15f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 5: fit() — color component (HARD filter)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card within color identity when fit then withinColorIdentity is true and colorSoft is 1_0`() {
        val profile = minimalProfile(colorIdentity = setOf(ManaColor.U, ManaColor.G))
        val c = card(id = "legal1", colorIdentity = listOf("U"))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.withinColorIdentity)
        assertEquals(1.0f, fit.components.color, 0.001f)
        assertFalse(fit.reasons.any { it is ScoreReason.OutOfColorIdentity })
    }

    @Test
    fun `given card outside color identity when fit then withinColorIdentity is false and colorSoft is 0_0`() {
        val profile = minimalProfile(colorIdentity = setOf(ManaColor.U, ManaColor.G))
        val c = card(id = "illegal1", colorIdentity = listOf("R"))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(fit.withinColorIdentity)
        assertEquals(0.0f, fit.components.color, 0.001f)
        assertTrue(fit.reasons.any { it is ScoreReason.OutOfColorIdentity })
    }

    @Test
    fun `given colorless card when fit then withinColorIdentity is true and colorSoft is 0_95`() {
        val profile = minimalProfile(colorIdentity = setOf(ManaColor.U))
        val c = card(id = "colorless1", colorIdentity = emptyList(), colors = emptyList())

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.withinColorIdentity)
        assertEquals(0.95f, fit.components.color, 0.001f)
        assertTrue(fit.reasons.any { it is ScoreReason.Colorless })
    }

    @Test
    fun `given empty deck color identity when fit then colorSoft is 1_0 for any card`() {
        // Empty identity = colorless commander or unfiltered
        val profile = minimalProfile(colorIdentity = emptySet())
        val c = card(id = "any1", colorIdentity = listOf("R", "G"))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.withinColorIdentity)
        assertEquals(1.0f, fit.components.color, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 6: fit() — redundancy component
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given role exactly at ideal when fit then OverCovered reason is NOT added`() {
        // Commander RAMP ideal = 11; current = 11 → overflow = (11-11)/11 = 0.
        // The guard is `if (overflow > worst)` where worst=0f: 0 > 0f is FALSE.
        // So OverCovered is NOT added when current == ideal — only when current > ideal.
        val profile = minimalProfile(roleCounts = mapOf(DeckRole.RAMP to 11))
        val c = card(id = "red1", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(
            "OverCovered must NOT be added when current exactly equals ideal (overflow=0)",
            fit.reasons.any { it is ScoreReason.OverCovered }
        )
        // roleNeed is also 0 at ideal coverage
        assertEquals(0f, fit.components.roleNeed, 0.001f)
    }

    @Test
    fun `given role over ideal when fit then OverCovered reason added and redundancy penalizes score`() {
        // RAMP ideal = 11; current = 22 → overflow = (22-11)/11 = 1.0
        val profile = minimalProfile(roleCounts = mapOf(DeckRole.RAMP to 22))
        val c = card(id = "red2", colorIdentity = listOf("G"), tags = listOf(tagManaRock))
        val cFresh = card(id = "red2b", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        // Same card with zero current ramp
        val profileFresh = minimalProfile(roleCounts = mapOf(DeckRole.RAMP to 0))
        val fitOver  = scorer.fit(c,       profile,      isOwned = false)
        val fitFresh = scorer.fit(cFresh,  profileFresh, isOwned = false)

        assertTrue("Over-covered card should score lower", fitOver.score < fitFresh.score)
        assertTrue(fitOver.reasons.any { it is ScoreReason.OverCovered })
    }

    @Test
    fun `given role under ideal when fit then no OverCovered reason`() {
        val profile = minimalProfile(roleCounts = mapOf(DeckRole.RAMP to 5))
        val c = card(id = "red3", colorIdentity = listOf("G"), tags = listOf(tagManaRock))

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(fit.reasons.any { it is ScoreReason.OverCovered })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 7: fit() — aggregation, power reasons, isOwned, score bounds
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given isOwned true when fit then InCollection reason is added`() {
        val profile = minimalProfile()
        val c = card(id = "owned1")

        val fit = scorer.fit(c, profile, isOwned = true)

        assertTrue(fit.reasons.any { it is ScoreReason.InCollection })
    }

    @Test
    fun `given isOwned false when fit then InCollection reason is NOT added`() {
        val profile = minimalProfile()
        val c = card(id = "notowned1")

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(fit.reasons.any { it is ScoreReason.InCollection })
    }

    @Test
    fun `given gameChanger card when fit then GameChanger reason added`() {
        val scorerGc = DeckScorer(RoleClassifier(), fixedPower(0.85f, isGameChanger = true))
        val profile  = minimalProfile()
        val c        = card(id = "gc1", gameChanger = true)

        val fit = scorerGc.fit(c, profile, isOwned = false)

        assertTrue(fit.reasons.any { it is ScoreReason.GameChanger })
    }

    @Test
    fun `given high power card when fit then HighPower reason added`() {
        val scorerHp = DeckScorer(RoleClassifier(), fixedPower(0.80f))
        val profile  = minimalProfile()
        val c        = card(id = "hp1")

        val fit = scorerHp.fit(c, profile, isOwned = false)

        assertTrue(fit.reasons.any { it is ScoreReason.HighPower })
    }

    @Test
    fun `given below power floor card when fit then BelowPowerFloor reason added`() {
        val weights  = ScoreWeights(powerFloor = 0.18f)
        val scorerLp = DeckScorer(RoleClassifier(), fixedPower(0.10f))
        val profile  = minimalProfile()
        val c        = card(id = "lp1")

        val fit = scorerLp.fit(c, profile, isOwned = false, weights = weights)

        assertTrue(fit.reasons.any { it is ScoreReason.BelowPowerFloor })
    }

    @Test
    fun `given any card when fit then score is coerced to 0_0 at minimum`() {
        // Force a card that would produce a negative raw score (high redundancy)
        val scorerLp = DeckScorer(RoleClassifier(), fixedPower(0.0f))
        val profile  = minimalProfile(
            roleCounts    = mapOf(DeckRole.RAMP to 30),  // massive overcoverage
            tagFingerprint = emptyMap(),
        )
        val c = card(id = "bad1", tags = listOf(tagManaRock))

        val fit = scorerLp.fit(c, profile, isOwned = false)

        assertTrue("Score must not be negative", fit.score >= 0f)
    }

    @Test
    fun `given any card when fit then score is coerced to 1_0 at maximum`() {
        val scorerTop = DeckScorer(RoleClassifier(), fixedPower(1.0f))
        val profile   = minimalProfile(
            tagFingerprint = mapOf(tagRamp.key to 1.0f),
            roleCounts     = mapOf(DeckRole.RAMP to 0),
            colorIdentity  = setOf(ManaColor.G),
        )
        val c = card(
            id           = "perfect1",
            colorIdentity = listOf("G"),
            tags          = listOf(tagRamp, tagManaRock),
            cmc           = 1.0,
        )

        val fit = scorerTop.fit(c, profile, isOwned = false)

        assertTrue("Score must not exceed 1.0", fit.score <= 1.0f)
    }

    @Test
    fun `given illegal card when fit then isLegal is false`() {
        val profile = minimalProfile(format = DeckFormat.STANDARD)
        val c = card(id = "banned1", legalityStandard = "banned")

        val fit = scorer.fit(c, profile, isOwned = false)

        assertFalse(fit.isLegal)
    }

    @Test
    fun `given legal commander card when fit then isLegal is true`() {
        val profile = minimalProfile(format = DeckFormat.COMMANDER)
        val c = card(id = "legal-cmd", legalityCommander = "legal")

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.isLegal)
    }

    @Test
    fun `given restricted commander card when fit then isLegal is true`() {
        val profile = minimalProfile(format = DeckFormat.COMMANDER)
        val c = card(id = "restricted-cmd", legalityCommander = "restricted")

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue(fit.isLegal)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 8: rankAdds()
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given out-of-color-identity card when rankAdds then card is excluded`() {
        val profile = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
        )
        val illegalColor = card(id = "illegal-color", colorIdentity = listOf("R"),
            legalityCommander = "legal")

        val result = scorer.rankAdds(listOf(illegalColor), profile, emptySet())

        assertTrue("Out-of-identity card must be excluded from adds", result.isEmpty())
    }

    @Test
    fun `given illegal card when rankAdds then card is excluded`() {
        val profile = minimalProfile(format = DeckFormat.STANDARD)
        val banned  = card(id = "banned2", legalityStandard = "banned",
            colorIdentity = listOf("U"))

        val result = scorer.rankAdds(listOf(banned), profile, emptySet())

        assertTrue("Illegal card must be excluded from adds", result.isEmpty())
    }

    @Test
    fun `given card below power floor with low synergy when rankAdds then card is excluded`() {
        val scorerLp = DeckScorer(RoleClassifier(), fixedPower(0.10f)) // below default floor 0.18
        val profile  = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
            tagFingerprint = emptyMap(),  // synergy will be 0.5 (neutral, no fingerprint/seeds)
        )
        // Card has no tags -> synergy = 0.5 (neutral), not >= 0.85
        // Power 0.10 < floor 0.18 -> excluded
        val c = card(id = "weak1", colorIdentity = listOf("U"), legalityCommander = "legal")

        val result = scorerLp.rankAdds(listOf(c), profile, emptySet())

        assertTrue("Low-power card without exceptional synergy must be excluded", result.isEmpty())
    }

    @Test
    fun `given card below power floor but with exceptional synergy when rankAdds then card is included`() {
        val scorerLp = DeckScorer(RoleClassifier(), fixedPower(0.10f))
        // Build a profile where a tag-perfect card will achieve synergy >= 0.85
        // Single tag match where fingerprint = 1.0: score = 1.2 / sqrt(1.2) ≈ 1.095 -> coerced to 1.0
        val profile  = minimalProfile(
            format         = DeckFormat.COMMANDER,
            colorIdentity  = setOf(ManaColor.U),
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
        )
        val c = card(id = "synergy-exception",
            colorIdentity     = listOf("U"),
            legalityCommander = "legal",
            userTags          = listOf(tagTokens),  // userTag weight=1.2
        )

        val result = scorerLp.rankAdds(listOf(c), profile, emptySet())

        assertTrue("Exceptional-synergy card must pass power floor", result.isNotEmpty())
    }

    @Test
    fun `given multiple candidates when rankAdds then results are sorted descending by score`() {
        val profile = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
        )
        val scorerHigh = DeckScorer(RoleClassifier(), mappedPower("high" to 0.9f, "low" to 0.5f))
        val high = card(id = "high", colorIdentity = listOf("U"), legalityCommander = "legal")
        val low  = card(id = "low",  colorIdentity = listOf("U"), legalityCommander = "legal")

        val result = scorerHigh.rankAdds(listOf(low, high), profile, emptySet())

        assertEquals(2, result.size)
        assertTrue("Results must be descending", result[0].score >= result[1].score)
    }

    @Test
    fun `given more candidates than limit when rankAdds then result is capped at limit`() {
        val profile    = minimalProfile(colorIdentity = setOf(ManaColor.U))
        val candidates = List(20) { i ->
            card(id = "c$i", colorIdentity = listOf("U"), legalityCommander = "legal")
        }

        val result = scorer.rankAdds(candidates, profile, emptySet(), limit = 5)

        assertEquals(5, result.size)
    }

    @Test
    fun `given owned card id when rankAdds then CardFit isOwned is true`() {
        val profile  = minimalProfile(colorIdentity = setOf(ManaColor.U))
        val owned    = card(id = "owned-x", colorIdentity = listOf("U"), legalityCommander = "legal")

        val result = scorer.rankAdds(listOf(owned), profile, ownedIds = setOf("owned-x"))

        assertEquals(1, result.size)
        assertTrue(result.first().isOwned)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 9: rankCuts()
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given land cards in mainboard when rankCuts then lands are excluded`() {
        val profile = minimalProfile()
        val mainboard = listOf(
            entry(landCard("l1"), quantity = 37),
            entry(card(id = "spell1"), quantity = 1),
        )

        val result = scorer.rankCuts(mainboard, profile)

        assertTrue("Lands must not appear in cut candidates", result.none { it.roles == setOf(DeckRole.LAND) })
        assertEquals(1, result.size)
    }

    @Test
    fun `given protected card id when rankCuts then that card is excluded`() {
        val profile = minimalProfile()
        val protected = card(id = "commander-id")
        val normal    = card(id = "normal-id")
        val mainboard = listOf(entry(protected), entry(normal))

        val result = scorer.rankCuts(mainboard, profile, protectedIds = setOf("commander-id"))

        assertTrue(result.none { it.card.scryfallId == "commander-id" })
        assertTrue(result.any  { it.card.scryfallId == "normal-id" })
    }

    @Test
    fun `given infinite_combo tagged card when rankCuts then that card is excluded as combo core`() {
        val profile = minimalProfile()
        val combo   = card(id = "combo1", tags = listOf(tagInfinite))
        val normal  = card(id = "normal2")
        val mainboard = listOf(entry(combo), entry(normal))

        val result = scorer.rankCuts(mainboard, profile)

        assertTrue("infinite_combo card must be protected", result.none { it.card.scryfallId == "combo1" })
        assertTrue(result.any { it.card.scryfallId == "normal2" })
    }

    @Test
    fun `given combo archetype tagged card when rankCuts then that card is excluded as combo core`() {
        val profile = minimalProfile()
        val combo   = card(id = "combo2", tags = listOf(tagCombo))  // combo is ARCHETYPE category
        val mainboard = listOf(entry(combo), entry(card(id = "normal3")))

        val result = scorer.rankCuts(mainboard, profile)

        assertTrue("combo-tagged card must be protected", result.none { it.card.scryfallId == "combo2" })
    }

    @Test
    fun `given multiple non-land cards when rankCuts then results are sorted ascending by score`() {
        val scorerMapped = DeckScorer(
            RoleClassifier(),
            mappedPower("low-score" to 0.1f, "high-score" to 0.9f)
        )
        val profile = minimalProfile(colorIdentity = setOf(ManaColor.U))
        val mainboard = listOf(
            entry(card(id = "high-score", colorIdentity = listOf("U"))),
            entry(card(id = "low-score",  colorIdentity = listOf("U"))),
        )

        val result = scorerMapped.rankCuts(mainboard, profile)

        assertEquals(2, result.size)
        assertTrue("rankCuts must be ascending (lowest-fit first)", result[0].score <= result[1].score)
    }

    @Test
    fun `given all non-land cards are protected or combo when rankCuts then result is empty`() {
        val profile = minimalProfile()
        val mainboard = listOf(
            entry(card(id = "p1", tags = listOf(tagInfinite))),
            entry(card(id = "p2")),
        )

        val result = scorer.rankCuts(mainboard, profile, protectedIds = setOf("p2"))

        assertTrue(result.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 10: evaluate() — role coverage
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given well-covered Commander deck when evaluate then roleCoverage contains all skeleton slots`() {
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(
                DeckRole.RAMP           to 11,
                DeckRole.CARD_ADVANTAGE to 11,
                DeckRole.SPOT_REMOVAL   to 8,
                DeckRole.BOARD_WIPE     to 4,
                DeckRole.INTERACTION    to 5,
                DeckRole.TUTOR          to 2,
                DeckRole.LAND           to 37,
            ),
        )
        val eval = scorer.evaluate(profile, nonLand = emptyList())

        val rampCoverage = eval.roleCoverage.first { it.role == DeckRole.RAMP }
        assertEquals(11, rampCoverage.current)
        assertEquals(11, rampCoverage.ideal)
        assertEquals(0, rampCoverage.gap)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 11: evaluate() — land warnings
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given too few lands when evaluate then TooFewLands warning emitted`() {
        // Commander min lands = 35; we put 30
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(DeckRole.LAND to 30),
        )

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertTrue(eval.warnings.any { it is DeckWarning.TooFewLands })
    }

    @Test
    fun `given too many lands when evaluate then TooManyLands warning emitted`() {
        // Commander max lands = 39; we put 45
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(DeckRole.LAND to 45),
        )

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertTrue(eval.warnings.any { it is DeckWarning.TooManyLands })
    }

    @Test
    fun `given land count within bounds when evaluate then no land warning`() {
        // Commander ideal = 37; we put 37
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(DeckRole.LAND to 37),
        )

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertFalse(eval.warnings.any { it is DeckWarning.TooFewLands || it is DeckWarning.TooManyLands })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 12: evaluate() — MissingRole warning
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given 0 RAMP cards in commander deck when evaluate then MissingRole for RAMP emitted`() {
        // RAMP ideal = 11 in Commander; 11 >= 2 so warning fires
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(DeckRole.RAMP to 0),
        )

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        val missing = eval.warnings.filterIsInstance<DeckWarning.MissingRole>()
        assertTrue("MissingRole RAMP expected", missing.any { it.role == DeckRole.RAMP })
    }

    @Test
    fun `given TUTOR ideal is 2 and count is 0 when evaluate then MissingRole for TUTOR emitted`() {
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(DeckRole.TUTOR to 0),
        )

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        val missing = eval.warnings.filterIsInstance<DeckWarning.MissingRole>()
        assertTrue(missing.any { it.role == DeckRole.TUTOR })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 13: evaluate() — CurveTooHigh / CurveTooLow
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given avgCmc greater than 4_0 when evaluate then CurveTooHigh warning`() {
        val profile = minimalProfile(avgCmc = 4.5, nonLandCount = 20)

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertTrue(eval.warnings.any { it is DeckWarning.CurveTooHigh })
    }

    @Test
    fun `given avgCmc exactly 4_0 when evaluate then no CurveTooHigh warning`() {
        val profile = minimalProfile(avgCmc = 4.0, nonLandCount = 20)

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertFalse(eval.warnings.any { it is DeckWarning.CurveTooHigh })
    }

    @Test
    fun `given avgCmc in 0_1 to 1_8 range and enough cards when evaluate then CurveTooLow warning`() {
        val profile = minimalProfile(avgCmc = 1.5, nonLandCount = 20)

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertTrue(eval.warnings.any { it is DeckWarning.CurveTooLow })
    }

    @Test
    fun `given avgCmc in valid range 2_0 when evaluate then no curve warning`() {
        val profile = minimalProfile(avgCmc = 2.8, nonLandCount = 20)

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertFalse(eval.warnings.any { it is DeckWarning.CurveTooHigh || it is DeckWarning.CurveTooLow })
    }

    @Test
    fun `given nonLandCount 10 or fewer when evaluate then CurveTooLow not emitted even with low avgCmc`() {
        // Edge case: small decks should not be warned about a low curve
        val profile = minimalProfile(avgCmc = 1.2, nonLandCount = 10)

        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertFalse(eval.warnings.any { it is DeckWarning.CurveTooLow })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 14: evaluate() — LowSynergyDensity
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given mostly off-strategy non-land cards when evaluate then LowSynergyDensity warning`() {
        // fingerprint has tagTokens; cards without tokens tag -> aligned=0 -> density=0
        val profile = minimalProfile(
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
            nonLandCount   = 20,
        )
        val nonLand = List(20) { i -> entry(card(id = "spell$i")) } // no matching tags

        val eval = scorer.evaluate(profile, nonLand = nonLand)

        assertTrue(eval.warnings.any { it is DeckWarning.LowSynergyDensity })
    }

    @Test
    fun `given mostly on-strategy non-land cards when evaluate then no LowSynergyDensity warning`() {
        val profile = minimalProfile(
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
            nonLandCount   = 5,
        )
        // 5 cards all with tokens tag → density = 5/5 = 1.0
        val nonLand = List(5) { i ->
            entry(card(id = "synergy$i", tags = listOf(tagTokens)))
        }

        val eval = scorer.evaluate(profile, nonLand = nonLand)

        assertFalse(eval.warnings.any { it is DeckWarning.LowSynergyDensity })
    }

    @Test
    fun `given nonLandCount equal to or less than 10 when evaluate then LowSynergyDensity not emitted`() {
        val profile = minimalProfile(
            tagFingerprint = mapOf(tagTokens.key to 1.0f),
            nonLandCount   = 5,
        )
        val nonLand = List(5) { i -> entry(card(id = "x$i")) } // off-strategy

        val eval = scorer.evaluate(profile, nonLand = nonLand)

        assertFalse(eval.warnings.any { it is DeckWarning.LowSynergyDensity })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 15: evaluate() — healthScore bounds
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given perfect coverage and correct land count when evaluate then healthScore is high`() {
        val profile = minimalProfile(
            format     = DeckFormat.COMMANDER,
            roleCounts = mapOf(
                DeckRole.RAMP           to 11,
                DeckRole.CARD_ADVANTAGE to 11,
                DeckRole.SPOT_REMOVAL   to 8,
                DeckRole.BOARD_WIPE     to 4,
                DeckRole.INTERACTION    to 5,
                DeckRole.TUTOR          to 2,
                DeckRole.LAND           to 37,
            ),
            avgCmc     = 2.8,
        )
        val nonLand = List(11) { i ->
            entry(card(id = "on$i", tags = listOf(tagTokens)), quantity = 1)
        }
        val profileWithFp = profile.copy(tagFingerprint = mapOf(tagTokens.key to 1.0f))

        val eval = scorer.evaluate(profileWithFp, nonLand = nonLand)

        assertTrue("healthScore should be >= 50 for a well-covered deck", eval.healthScore >= 50)
        assertTrue(eval.healthScore in 0..100)
    }

    @Test
    fun `given empty deck when evaluate then healthScore is within 0 to 100`() {
        val profile = minimalProfile(format = DeckFormat.COMMANDER)
        val eval = scorer.evaluate(profile, nonLand = emptyList())

        assertTrue(eval.healthScore in 0..100)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 16: evaluate() — synergyDensity calculation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty nonLand list when evaluate then synergyDensity is 0`() {
        val profile = minimalProfile()
        val eval    = scorer.evaluate(profile, nonLand = emptyList())
        assertEquals(0f, eval.synergyDensity, 0.001f)
    }

    @Test
    fun `given all cards have fingerprint tag at threshold when evaluate then synergyDensity is 1_0`() {
        // A card whose tag has fp >= 0.4 counts as aligned
        val profile = minimalProfile(tagFingerprint = mapOf(tagTokens.key to 0.5f))
        val nonLand = List(4) { i -> entry(card(id = "s$i", tags = listOf(tagTokens))) }

        val eval = scorer.evaluate(profile, nonLand = nonLand)

        assertEquals(1.0f, eval.synergyDensity, 0.001f)
    }

    @Test
    fun `given tag fingerprint below 0_4 threshold when evaluate then card does not count as aligned`() {
        // fp = 0.3 < 0.4 → card should not be aligned
        val profile = minimalProfile(tagFingerprint = mapOf(tagTokens.key to 0.3f))
        val nonLand = List(11) { i -> entry(card(id = "s$i", tags = listOf(tagTokens))) }

        val eval = scorer.evaluate(profile, nonLand = nonLand)

        assertEquals(0f, eval.synergyDensity, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 17: isAddCandidate on CardFit
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given legal in-color card when fit then isAddCandidate is true`() {
        val profile = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
        )
        val c = card(id = "add1", colorIdentity = listOf("U"), legalityCommander = "legal")
        val fit = scorer.fit(c, profile, isOwned = false)
        assertTrue(fit.isAddCandidate)
    }

    @Test
    fun `given illegal card when fit then isAddCandidate is false`() {
        val profile = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
        )
        val c = card(id = "add2", colorIdentity = listOf("U"), legalityCommander = "banned")
        val fit = scorer.fit(c, profile, isOwned = false)
        assertFalse(fit.isAddCandidate)
    }

    @Test
    fun `given out-of-color card when fit then isAddCandidate is false`() {
        val profile = minimalProfile(
            format        = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.U),
        )
        val c = card(id = "add3", colorIdentity = listOf("R"), legalityCommander = "legal")
        val fit = scorer.fit(c, profile, isOwned = false)
        assertFalse(fit.isAddCandidate)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Group 18: DeckFormat.DRAFT — isLegal always true
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given DRAFT format when fit then isLegal is always true regardless of legality string`() {
        val profile = minimalProfile(format = DeckFormat.DRAFT, colorIdentity = emptySet())
        val c = card(id = "draft1", legalityStandard = "banned", legalityCommander = "banned")

        val fit = scorer.fit(c, profile, isOwned = false)

        assertTrue("DRAFT format always makes cards legal", fit.isLegal)
    }
}
