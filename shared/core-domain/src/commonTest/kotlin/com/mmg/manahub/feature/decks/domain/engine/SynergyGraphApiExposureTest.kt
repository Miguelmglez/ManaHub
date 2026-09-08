package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.feature.decks.domain.engine.analysisv3.roleTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan (Phase 0 / E5): read-only additions to [SynergyGraph]
 * ([SynergyGraph.cardAxisProfile], [SynergyGraph.axisIdeals]) and the new [CurveTargets] object.
 * These are additive API-exposure only -- nothing in [AnalysisEngine.evaluate] calls any of them,
 * so this suite exists to prove the NEW public surface matches [SynergyGraph.build]'s own internal
 * numbers, not to guard a scoring path (the golden/corpus/calibration suites already do that,
 * confirmed byte-identical by this same phase).
 */
class SynergyGraphApiExposureTest {

    // ── cardAxisProfile ──────────────────────────────────────────────────────────────────────

    @Test
    fun `cardAxisProfile matches the per-card profile build() computes internally`() {
        val counterspell = card(id = "cs-1", name = "Counterspell", tags = listOf(roleTag("counterspell")))
        val profile = SynergyGraph.cardAxisProfile(counterspell, ArchetypeFormat.COMMANDER)

        assertEquals(counterspell.scryfallId, profile.cardId)
        assertTrue((profile.produces["ENGINE"] ?: 0f) > 0f, "counterspell should produce ENGINE")

        // Cross-check against build() over a single-card mainboard: the SAME card, alone, should
        // register the SAME ENGINE producer credit in the full graph.
        val graph = SynergyGraph.build(listOf(entry(counterspell)), ArchetypeFormat.COMMANDER, includeEngineAxis = true)
        val engineAxis = graph.axes.first { it.axis == "ENGINE" }
        assertEquals(1, engineAxis.producerCopies)
    }

    @Test
    fun `cardAxisProfile with no tribal context omits TRIBE credit`() {
        val vampireLord = card(
            id = "vl-1",
            name = "Vampire Lord",
            typeLine = "Creature — Vampire",
            oracleText = "Other Vampires you control get +1/+1.",
        )
        val profile = SynergyGraph.cardAxisProfile(vampireLord, ArchetypeFormat.COMMANDER)
        assertTrue(profile.produces.keys.none { it.startsWith("TRIBE:") })

        val withTribe = SynergyGraph.cardAxisProfile(
            vampireLord, ArchetypeFormat.COMMANDER,
            dominantTribeAxis = "TRIBE:vampire", dominantTribeKey = "${TribeDeriver.TRIBE_PREFIX}vampire",
        )
        assertTrue((withTribe.produces["TRIBE:vampire"] ?: 0f) > 0f)
    }

    // ── axisIdeals ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `axisIdeals with no nonLandCount returns the raw Commander-scale table unscaled`() {
        val ideals = SynergyGraph.axisIdeals(ArchetypeFormat.COMMANDER)
        val life = ideals.getValue("LIFE")
        assertEquals(8, life.producerIdeal)
        assertEquals(12, life.payoffIdeal)
    }

    @Test
    fun `axisIdeals scaled to a nonLandCount matches build()'s own scaled ideal for that axis`() {
        // A 37-nonland deck (~60-card scale) with zero LIFE signal still lets us compare the
        // SCALED ideal build() attaches to AxisState.producerIdeal against axisIdeals' own output
        // for the identical (format, nonLandCount) pair.
        val filler = (1..37).map { i -> entry(card(id = "filler-$i", name = "Filler $i")) }
        val graph = SynergyGraph.build(filler, ArchetypeFormat.SIXTY)
        val lifeAxisState = graph.axes.first { it.axis == "LIFE" }

        val scaledIdeals = SynergyGraph.axisIdeals(ArchetypeFormat.SIXTY, nonLandCount = 37)
        assertEquals(scaledIdeals.getValue("LIFE").producerIdeal, lifeAxisState.producerIdeal)
    }

    @Test
    fun `axisIdeals covers every STATIC_AXES-adjacent named axis including ENGINE`() {
        val ideals = SynergyGraph.axisIdeals(ArchetypeFormat.COMMANDER)
        assertTrue("ENGINE" in ideals)
        assertTrue("TOKENS" in ideals)
        assertTrue(ideals.keys.none { it.startsWith("TRIBE:") })
    }

    // ── CurveTargets ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `CurveTargets forSkeleton fractions sum to 1`() {
        listOf(CurveShape.FRONT, CurveShape.BELL, CurveShape.BACK).forEach { shape ->
            val skeleton = testSkeleton(shape)
            val total = CurveTargets.forSkeleton(skeleton).sumOf { it.fraction }
            assertTrue(total in 0.999..1.001, "shape=$shape summed to $total")
        }
    }

    @Test
    fun `CurveTargets bucket ids match AnalysisEngine's own curve section ids`() {
        val targets = CurveTargets.forSkeleton(testSkeleton(CurveShape.BELL))
        val ids = targets.map { it.bucketId }
        assertEquals((0..6).map { "mv:$it" } + "mv:7plus", ids)
    }

    @Test
    fun `CurveTargets FRONT weights low buckets more than high buckets`() {
        val targets = CurveTargets.forSkeleton(testSkeleton(CurveShape.FRONT)).associateBy { it.bucketId }
        assertTrue(targets.getValue("mv:0").fraction > targets.getValue("mv:6").fraction)
    }

    @Test
    fun `CurveTargets BACK weights high buckets more than low buckets`() {
        val targets = CurveTargets.forSkeleton(testSkeleton(CurveShape.BACK)).associateBy { it.bucketId }
        assertTrue(targets.getValue("mv:6").fraction > targets.getValue("mv:0").fraction)
    }

    @Test
    fun `CurveTargets targetCount is populated only when nonLandCount is supplied`() {
        val skeleton = testSkeleton(CurveShape.BELL)
        assertTrue(CurveTargets.forSkeleton(skeleton).all { it.targetCount == null })
        val withCount = CurveTargets.forSkeleton(skeleton, nonLandCount = 63)
        assertTrue(withCount.all { it.targetCount != null })
        // Rounding per bucket can drift the sum by a few copies either way -- not required to be
        // exactly 63, only in the same ballpark (never negative, never wildly over/under).
        val summed = withCount.sumOf { it.targetCount ?: 0 }
        assertTrue(summed in 55..71, "summed target counts $summed should be close to nonLandCount 63")
    }

    private fun testSkeleton(shape: CurveShape): ResolvedArchetypeSkeleton = ArchetypeSkeletonResolver.resolveWithColor(
        format = ArchetypeFormat.COMMANDER,
        archetype = null,
        themes = emptyList(),
        identity = emptySet(),
    ).copy(shape = shape)
}
