package com.mmg.manahub.core.gamification.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural invariants for [UnlockableCatalog]: the persisted-id contract, real achievement
 * references, grandfathering (no theme unlockables) and availability consistency (G-15).
 */
class UnlockableCatalogTest {

    @Test
    fun allUnlockableIdsAreUnique() {
        val ids = UnlockableCatalog.all.map { it.id.value }
        assertEquals(ids.size, ids.toSet().size, "Catalog contains duplicate ids")
    }

    @Test
    fun catalogHasRoughlyTwentyItems() {
        assertTrue(UnlockableCatalog.all.size in 18..24, "Catalog should have ~20 items, was ${UnlockableCatalog.all.size}")
    }

    @Test
    fun everyAchievementUnlockedRuleReferencesARealAchievementId() {
        val realIds = AchievementCatalog.all.map { it.id }.toSet()
        UnlockableCatalog.all.forEach { unlockable ->
            val rule = unlockable.unlockRule
            if (rule is UnlockRule.AchievementUnlocked) {
                assertTrue(
                    rule.achievementId in realIds,
                    "Unlockable '${unlockable.id.value}' references unknown achievement '${rule.achievementId}'",
                )
            }
        }
    }

    @Test
    fun anItemTiedToAnUnavailableAchievementIsItselfUnavailable() {
        UnlockableCatalog.all.forEach { unlockable ->
            val rule = unlockable.unlockRule as? UnlockRule.AchievementUnlocked ?: return@forEach
            val achievement = AchievementCatalog.byId(rule.achievementId)!!
            if (!achievement.isAvailable) {
                assertFalse(unlockable.isAvailable, "${unlockable.id.value} advertises unreachable ${achievement.id}")
            }
        }
        assertFalse(UnlockableCatalog.byId("title_tournament_champion")!!.isAvailable)
    }

    @Test
    fun byKindCoversAllKindsPresentInTheCatalogAndPartitionsEveryItem() {
        assertEquals(UnlockableCatalog.all.map { it.kind }.toSet(), UnlockableCatalog.byKind.keys)
        val flattened = UnlockableCatalog.byKind.values.flatten()
        assertEquals(UnlockableCatalog.all.toSet(), flattened.toSet())
        assertEquals(UnlockableCatalog.all.size, flattened.size)
    }

    @Test
    fun allFourCosmeticKindsAreRepresented() {
        assertEquals(UnlockableKind.entries.toSet(), UnlockableCatalog.all.map { it.kind }.toSet())
    }

    @Test
    fun byKindGroupsAreSortedBySortOrderThenId() {
        UnlockableCatalog.byKind.forEach { (kind, items) ->
            val expected = items.sortedWith(compareBy({ it.sortOrder }, { it.id.value }))
            assertEquals(expected, items, "Kind $kind not sorted")
        }
    }

    @Test
    fun byIdResolvesKnownIdsAndReturnsNullForUnknown() {
        assertEquals("frame_gold", UnlockableCatalog.byId("frame_gold")?.id?.value)
        assertEquals("frame_gold", UnlockableCatalog.byId(UnlockableId("frame_gold"))?.id?.value)
        assertNull(UnlockableCatalog.byId("not_a_real_cosmetic"))
    }

    @Test
    fun noUnlockableIsATheme() {
        assertTrue(
            UnlockableCatalog.all.none { it.id.value.contains("theme", ignoreCase = true) },
            "No unlockable id should reference a theme",
        )
    }

    @Test
    fun levelRulesUseSaneLevelsAndAchievementRulesAreNonBlank() {
        UnlockableCatalog.all.forEach { unlockable ->
            when (val rule = unlockable.unlockRule) {
                is UnlockRule.LevelAtLeast ->
                    assertTrue(rule.level >= 1, "Level rule must require level >= 1 (${unlockable.id.value})")
                is UnlockRule.AchievementUnlocked ->
                    assertTrue(rule.achievementId.isNotBlank(), "Achievement id must be non-blank (${unlockable.id.value})")
            }
        }
    }

    @Test
    fun everyBadgeCarriesAGlyphAndAFrameShape() {
        UnlockableCatalog.byKind[UnlockableKind.BADGE].orEmpty().forEach { badge ->
            assertFalse(badge.renderSpec.glyph.isNullOrBlank(), "Badge ${badge.id.value} must have a glyph")
            assertTrue(badge.renderSpec.badgeShape != null, "Badge ${badge.id.value} must have a frame shape")
        }
    }

    @Test
    fun theFourPlayStyleTitlesAreLevelGatedAndPresent() {
        val playStyleIds = setOf("title_aggressor", "title_strategist", "title_midrange", "title_balanced")
        val present = UnlockableCatalog.all.filter { it.id.value in playStyleIds }
        assertEquals(playStyleIds.size, present.size, "All 4 PlayStyle titles must be present")
        present.forEach {
            assertTrue(it.unlockRule is UnlockRule.LevelAtLeast, "PlayStyle title ${it.id.value} must be level-gated")
        }
    }
}
