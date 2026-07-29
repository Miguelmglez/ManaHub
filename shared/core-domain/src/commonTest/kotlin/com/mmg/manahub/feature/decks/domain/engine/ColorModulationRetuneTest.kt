package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Wizard & Engine Rework plan, WS9.3 — sanity/splash-non-inflation checks for the retuned
 * [ArchetypeData.COLOR_MODULATION] buckets and the new [ArchetypeData.LAND_MIX] table. This is a
 * structural/monotonicity suite (not a full per-card pip simulation — [ManaBaseAnalyzer]'s Karsten
 * math already owns that, untouched by this pass): it asserts the bucket-level data itself never
 * inflates unreasonably as color count rises, which is the failure mode "splash non-inflation"
 * describes at this layer.
 */
class ColorModulationRetuneTest {

    @Test
    fun manaFixBandsAreMonotonicNonDecreasingAcrossColorCountBuckets() {
        ArchetypeFormat.entries.forEach { format ->
            val buckets = ArchetypeData.COLOR_MODULATION.getValue(format)
            (1..3).forEach { bucket ->
                val current = buckets.getValue(bucket).manaFix
                val next = buckets.getValue(bucket + 1).manaFix
                assertTrue(current.min <= next.min, "$format bucket $bucket->${bucket + 1}: min should not decrease (${current.min} > ${next.min})")
                assertTrue(current.ideal <= next.ideal, "$format bucket $bucket->${bucket + 1}: ideal should not decrease (${current.ideal} > ${next.ideal})")
                assertTrue(current.max <= next.max, "$format bucket $bucket->${bucket + 1}: max should not decrease (${current.max} > ${next.max})")
            }
        }
    }

    @Test
    fun fourColorBucketDoesNotInflateBeyondASaneCeilingRelativeToThreeColor() {
        // Splash non-inflation, bucket-level: a 4-5 color deck's dedicated-fixing demand must stay
        // in the same order of magnitude as a 3-color deck's, never balloon disproportionately --
        // this table is a COUNT-level modulation, never a per-color pip simulation (that stays
        // ManaBaseAnalyzer's job).
        ArchetypeFormat.entries.forEach { format ->
            val buckets = ArchetypeData.COLOR_MODULATION.getValue(format)
            val three = buckets.getValue(3).manaFix
            val four = buckets.getValue(4).manaFix
            assertTrue(
                four.min <= three.min * 2,
                "$format: 4-5 color min (${four.min}) more than doubles the 3-color min (${three.min})",
            )
            assertTrue(
                four.ideal <= three.ideal * 2,
                "$format: 4-5 color ideal (${four.ideal}) more than doubles the 3-color ideal (${three.ideal})",
            )
        }
    }

    @Test
    fun colorCountBucketClampsAboveFour() {
        assertTrue(ArchetypeData.colorCountBucket(5) == ArchetypeData.colorCountBucket(4))
        assertTrue(ArchetypeData.colorCountBucket(99) == 4)
    }

    // ── LAND_MIX (new WS9.3 data) ──────────────────────────────────────────────────

    @Test
    fun basicsRatioIsMonotonicNonIncreasingAsColorCountRises() {
        ArchetypeFormat.entries.forEach { format ->
            val buckets = ArchetypeData.LAND_MIX.getValue(format)
            (1..3).forEach { bucket ->
                val current = buckets.getValue(bucket).basicsRatio
                val next = buckets.getValue(bucket + 1).basicsRatio
                assertTrue(
                    current.start >= next.start,
                    "$format bucket $bucket->${bucket + 1}: basics ratio floor should not INCREASE with more colors",
                )
            }
        }
    }

    @Test
    fun basicsRatioStaysWithinAValidZeroToOneShare() {
        ArchetypeFormat.entries.forEach { format ->
            ArchetypeData.LAND_MIX.getValue(format).values.forEach { entry ->
                assertTrue(entry.basicsRatio.start in 0.0..1.0)
                assertTrue(entry.basicsRatio.endInclusive in 0.0..1.0)
                assertTrue(entry.basicsRatio.start <= entry.basicsRatio.endInclusive)
            }
        }
    }

    @Test
    fun landMixForMirrorsColorCountBucketResolution() {
        assertTrue(ArchetypeData.landMixFor(ArchetypeFormat.COMMANDER, 7) == ArchetypeData.landMixFor(ArchetypeFormat.COMMANDER, 4))
        assertTrue(ArchetypeData.landMixFor(ArchetypeFormat.COMMANDER, 2).basicsRatio == 0.22..0.32)
    }
}
