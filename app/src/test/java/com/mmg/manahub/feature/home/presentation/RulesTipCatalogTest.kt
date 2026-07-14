package com.mmg.manahub.feature.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [MTG_TIPS_CATALOG] (Home feature overhaul Phase 2.4).
 *
 * TESTABILITY NOTE: the actual daily-selection algorithm
 * (`MTG_TIPS_CATALOG.shuffled(Random(RULES_TIP_SHUFFLE_SEED))` + an epoch-day modulus index) lives
 * inline inside the `RulesTipWidget` Composable in `HomeWidgets.kt`, and `RULES_TIP_SHUFFLE_SEED`
 * is a file-private top-level constant — neither is reachable from a plain JVM unit test (only a
 * Compose UI test could exercise the Composable directly). These tests therefore verify the
 * documented properties of the SAME algorithm (fixed-seed `shuffled(Random(seed))` + epoch-day
 * modulus) against the real [MTG_TIPS_CATALOG] data, which is what actually matters for
 * correctness (stability, in-bounds indexing, full-cycle coverage) — they do not exercise
 * `RulesTipWidget` itself. Recommend extracting a pure `selectDailyTip(catalog, seed, epochDay):
 * RuleTip` function into `RulesTipCatalog.kt` so this can be tested end-to-end without Compose.
 */
class RulesTipCatalogTest {

    // ── Catalog sanity ─────────────────────────────────────────────────────────

    @Test
    fun `catalog has no duplicate titles`() {
        val titles = MTG_TIPS_CATALOG.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `catalog has no blank titles or bodies`() {
        MTG_TIPS_CATALOG.forEach { tip ->
            assertFalse("Tip with blank title: $tip", tip.title.isBlank())
            assertFalse("Tip '${tip.title}' has a blank body", tip.body.isBlank())
        }
    }

    @Test
    fun `every tip is assigned a TipCategory`() {
        // TipCategory is a non-null constructor param, so this is a compile-time guarantee for
        // every entry — this test documents the invariant and would fail to compile (not
        // assert-fail) if RuleTip.category ever became nullable without a matching update here.
        MTG_TIPS_CATALOG.forEach { tip ->
            assertTrue(TipCategory.entries.contains(tip.category))
        }
    }

    @Test
    fun `every TipCategory has at least one tip`() {
        val used = MTG_TIPS_CATALOG.map { it.category }.toSet()
        TipCategory.entries.forEach { category ->
            assertTrue("No tips found for category $category", category in used)
        }
    }

    @Test
    fun `catalog size matches the current shipped count`() {
        // Verified against the real file (grep count), NOT the plan doc's aspirational ~160-175
        // target (see project_home_feature_overhaul_2026-07-13 memory: landed at fewer tips after
        // deduplication). Update this value deliberately when tips are added/removed — a silent
        // drift here should fail the test, not be silently tolerated.
        assertEquals(146, MTG_TIPS_CATALOG.size)
    }

    @Test
    fun `MTG_TIPS_COUNT matches the catalog's actual size`() {
        assertEquals(MTG_TIPS_CATALOG.size, MTG_TIPS_COUNT)
    }

    @Test
    fun `catalog is comfortably larger than the pre-overhaul 62-tip baseline`() {
        assertTrue(MTG_TIPS_CATALOG.size > 62)
    }

    // ── Deterministic daily-selection algorithm (mirrors RulesTipWidget) ──────

    private fun shuffledFor(seed: Long): List<RuleTip> = MTG_TIPS_CATALOG.shuffled(Random(seed))

    private fun indexForEpochDay(epochDay: Long, size: Int): Int =
        (epochDay % size).toInt()

    @Test
    fun `same seed produces the same permutation across repeated calls`() {
        val seed = 20260713L
        val first = shuffledFor(seed)
        val second = shuffledFor(seed)
        val third = shuffledFor(seed)

        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `shuffled list is a permutation of the original catalog (same elements, same size)`() {
        val shuffled = shuffledFor(42L)
        assertEquals(MTG_TIPS_CATALOG.size, shuffled.size)
        assertEquals(MTG_TIPS_CATALOG.toSet(), shuffled.toSet())
    }

    @Test
    fun `epoch-day index is always within catalog bounds across a wide range of days`() {
        val shuffled = shuffledFor(20260713L)
        val size = shuffled.size
        // Epoch day 0 (1970-01-01) through ~120 years out — covers any plausible device clock.
        val sampleDays = listOf(0L, 1L, 2L, 100L, 1000L, 19_000L, 19_000L * 3, 60_000L)
        sampleDays.forEach { epochDay ->
            val index = indexForEpochDay(epochDay, size)
            assertTrue("index $index out of bounds for day $epochDay", index in shuffled.indices)
        }
    }

    @Test
    fun `same epoch day always resolves to the same tip (deterministic, dependency-free)`() {
        val shuffled = shuffledFor(20260713L)
        val epochDay = 20_000L
        val tipA = shuffled[indexForEpochDay(epochDay, shuffled.size)]
        val tipB = shuffled[indexForEpochDay(epochDay, shuffled.size)]
        assertEquals(tipA, tipB)
    }

    @Test
    fun `cycle length equals the catalog size — one full lap of days never repeats a tip`() {
        val shuffled = shuffledFor(20260713L)
        val size = shuffled.size
        val startDay = 12_345L
        val seenIndices = (0 until size).map { offset -> indexForEpochDay(startDay + offset, size) }
        // A full lap of `size` consecutive days must touch every index exactly once.
        assertEquals((0 until size).toSet(), seenIndices.toSet())
    }

    @Test
    fun `day N and day N plus catalog size resolve to the same tip (wraps around cleanly)`() {
        val shuffled = shuffledFor(20260713L)
        val size = shuffled.size
        val day = 555L
        assertEquals(
            indexForEpochDay(day, size),
            indexForEpochDay(day + size, size),
        )
    }

    @Test
    fun `different seeds produce different permutations (seed actually affects ordering)`() {
        val a = shuffledFor(1L)
        val b = shuffledFor(2L)
        assertFalse(a == b)
    }
}
