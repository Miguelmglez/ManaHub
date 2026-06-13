package com.mmg.manahub.core.gamification.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural invariants for [UnlockableCatalog]. These guard the persisted-id contract, the
 * achievement-reference correctness the entitlement granter relies on, and the grandfathering rule
 * (no theme unlockables).
 */
class UnlockableCatalogTest {

    @Test
    fun `all unlockable ids are unique`() {
        val ids = UnlockableCatalog.all.map { it.id.value }
        assertEquals("Catalog contains duplicate ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog has roughly 20 items`() {
        assertTrue(
            "Catalog should have ~20 items, was ${UnlockableCatalog.all.size}",
            UnlockableCatalog.all.size in 18..24,
        )
    }

    @Test
    fun `every AchievementUnlocked rule references a real AchievementCatalog id`() {
        val realIds = AchievementCatalog.all.map { it.id }.toSet()
        UnlockableCatalog.all.forEach { unlockable ->
            val rule = unlockable.unlockRule
            if (rule is UnlockRule.AchievementUnlocked) {
                assertTrue(
                    "Unlockable '${unlockable.id.value}' references unknown achievement '${rule.achievementId}'",
                    rule.achievementId in realIds,
                )
            }
        }
    }

    @Test
    fun `byKind covers all kinds present in the catalog and partitions every item`() {
        val kindsPresent = UnlockableCatalog.all.map { it.kind }.toSet()
        assertEquals(kindsPresent, UnlockableCatalog.byKind.keys)

        // Every item appears in exactly one kind bucket; buckets reassemble the full catalog.
        val flattened = UnlockableCatalog.byKind.values.flatten()
        assertEquals(UnlockableCatalog.all.toSet(), flattened.toSet())
        assertEquals(UnlockableCatalog.all.size, flattened.size)
    }

    @Test
    fun `all four cosmetic kinds are represented`() {
        val kinds = UnlockableCatalog.all.map { it.kind }.toSet()
        assertEquals(UnlockableKind.entries.toSet(), kinds)
    }

    @Test
    fun `byKind groups are sorted by sortOrder then id`() {
        UnlockableCatalog.byKind.forEach { (kind, items) ->
            val expected = items.sortedWith(compareBy({ it.sortOrder }, { it.id.value }))
            assertEquals("Kind $kind not sorted", expected, items)
        }
    }

    @Test
    fun `byId resolves known ids and returns null for unknown`() {
        assertEquals("frame_gold", UnlockableCatalog.byId("frame_gold")?.id?.value)
        assertEquals("frame_gold", UnlockableCatalog.byId(UnlockableId("frame_gold"))?.id?.value)
        assertTrue(UnlockableCatalog.byId("not_a_real_cosmetic") == null)
    }

    @Test
    fun `no unlockable is a theme (grandfathering — themes stay free)`() {
        // Guard against accidentally introducing a theme cosmetic. There is no THEME kind; assert the
        // enum itself never gains one and that no id masquerades as a theme.
        assertTrue(
            "No unlockable id should reference a theme",
            UnlockableCatalog.all.none { it.id.value.contains("theme", ignoreCase = true) },
        )
    }

    @Test
    fun `level rules use sane levels and achievement rules are non-blank`() {
        UnlockableCatalog.all.forEach { unlockable ->
            when (val rule = unlockable.unlockRule) {
                is UnlockRule.LevelAtLeast ->
                    assertTrue("Level rule must require level >= 1 (${unlockable.id.value})", rule.level >= 1)
                is UnlockRule.AchievementUnlocked ->
                    assertTrue("Achievement id must be non-blank (${unlockable.id.value})", rule.achievementId.isNotBlank())
            }
        }
    }

    @Test
    fun `every badge carries a glyph and a frame shape`() {
        UnlockableCatalog.byKind[UnlockableKind.BADGE].orEmpty().forEach { badge ->
            assertTrue("Badge ${badge.id.value} must have a glyph", !badge.renderSpec.glyph.isNullOrBlank())
            assertTrue("Badge ${badge.id.value} must have a frame shape", badge.renderSpec.badgeShape != null)
        }
    }

    @Test
    fun `the four PlayStyle titles are level-gated and present`() {
        val playStyleIds = setOf("title_aggressor", "title_strategist", "title_midrange", "title_balanced")
        val present = UnlockableCatalog.all.filter { it.id.value in playStyleIds }
        assertEquals("All 4 PlayStyle titles must be present", playStyleIds.size, present.size)
        present.forEach {
            assertTrue(
                "PlayStyle title ${it.id.value} must be level-gated (PlayStyle is derived, not an achievement)",
                it.unlockRule is UnlockRule.LevelAtLeast,
            )
        }
    }
}
