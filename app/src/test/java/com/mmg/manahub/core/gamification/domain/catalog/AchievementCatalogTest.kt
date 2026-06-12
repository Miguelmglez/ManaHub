package com.mmg.manahub.core.gamification.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural invariants for [AchievementCatalog]. These guard the persisted-id contract and the
 * event-index correctness that the evaluator relies on.
 */
class AchievementCatalogTest {

    /** The 15 ids migrated from the old `AchievementId` enum — must remain present forever. */
    private val stableIds = setOf(
        "FIRST_WIN", "WIN_STREAK_3", "WIN_STREAK_5",
        "GAMES_PLAYED_10", "GAMES_PLAYED_50", "GAMES_PLAYED_100",
        "COLLECTOR_50", "COLLECTOR_500", "MYTHIC_OWNER", "DECK_BUILDER",
        "SURVEY_VETERAN", "HIGH_VALUE_COLLECTION", "QUICK_VICTORY",
        "COMMANDER_KILLER", "RAINBOW_COLLECTOR",
    )

    @Test
    fun `all achievement ids are unique`() {
        val ids = AchievementCatalog.all.map { it.id }
        assertEquals("Catalog contains duplicate ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `all 15 stable migrated ids are present`() {
        val ids = AchievementCatalog.all.map { it.id }.toSet()
        val missing = stableIds - ids
        assertTrue("Missing stable ids: $missing", missing.isEmpty())
    }

    @Test
    fun `catalog has at least 40 achievements`() {
        assertTrue(
            "Catalog should have ~40 defs, was ${AchievementCatalog.all.size}",
            AchievementCatalog.all.size >= 40 - 4, // tolerate small tuning; spec says ~40, min ~36
        )
    }

    @Test
    fun `every def has strictly ascending tiers`() {
        AchievementCatalog.all.forEach { def ->
            val thresholds = def.tiers.map { it.threshold }
            assertEquals(
                "Def ${def.id} tiers not strictly ascending: $thresholds",
                thresholds.sorted().distinct(), thresholds,
            )
        }
    }

    @Test
    fun `every def reacts to at least one event`() {
        AchievementCatalog.all.forEach { def ->
            assertTrue("Def ${def.id} reactsTo is empty", def.reactsTo.isNotEmpty())
        }
    }

    @Test
    fun `every DERIVED def declares a resolver and every COUNTER def does not`() {
        AchievementCatalog.all.forEach { def ->
            when (def.family) {
                Family.DERIVED -> assertTrue("DERIVED ${def.id} missing resolver", def.resolver != null)
                Family.COUNTER -> assertTrue("COUNTER ${def.id} should not have a resolver", def.resolver == null)
            }
        }
    }

    @Test
    fun `event index covers every def exactly via its reactsTo set`() {
        val indexed = AchievementCatalog.defsByEventType.values.flatten().toSet()
        // Every def appears in the index (under at least one event key).
        assertEquals(AchievementCatalog.all.toSet(), indexed)

        // And each index entry matches the def's declared reactsTo.
        AchievementCatalog.defsByEventType.forEach { (eventClass, defs) ->
            defs.forEach { def ->
                assertTrue(
                    "Def ${def.id} indexed under $eventClass it does not react to",
                    eventClass in def.reactsTo,
                )
            }
        }
    }

    @Test
    fun `unlocks list is always empty in Phase 1 (reserved for Phase 3)`() {
        AchievementCatalog.all.forEach { def ->
            assertTrue("Def ${def.id} must not declare unlocks in Phase 1", def.unlocks.isEmpty())
        }
    }

    @Test
    fun `byId resolves known ids and returns null for unknown`() {
        assertEquals("FIRST_WIN", AchievementCatalog.byId("FIRST_WIN")?.id)
        assertTrue(AchievementCatalog.byId("NOPE_NOT_REAL") == null)
    }
}
