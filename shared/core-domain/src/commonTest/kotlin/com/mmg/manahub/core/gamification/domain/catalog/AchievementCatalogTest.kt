package com.mmg.manahub.core.gamification.domain.catalog

import com.mmg.manahub.core.FeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural and copy invariants for [AchievementCatalog]: the persisted-id contract, the event index
 * the evaluator relies on, and descriptions that state their real thresholds (drift audit G-13).
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
    fun allAchievementIdsAreUnique() {
        val ids = AchievementCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Catalog contains duplicate ids")
    }

    @Test
    fun allFifteenStableMigratedIdsArePresent() {
        val missing = stableIds - AchievementCatalog.all.map { it.id }.toSet()
        assertTrue(missing.isEmpty(), "Missing stable ids: $missing")
    }

    @Test
    fun catalogHasAboutFortyAchievements() {
        assertTrue(AchievementCatalog.all.size >= 36, "Catalog should have ~40 defs, was ${AchievementCatalog.all.size}")
    }

    @Test
    fun everyDefHasStrictlyAscendingTiers() {
        AchievementCatalog.all.forEach { def ->
            val thresholds = def.tiers.map { it.threshold }
            assertEquals(thresholds.sorted().distinct(), thresholds, "Def ${def.id} tiers not strictly ascending")
        }
    }

    @Test
    fun everyDefReactsToAtLeastOneEvent() {
        AchievementCatalog.all.forEach { def ->
            assertTrue(def.reactsTo.isNotEmpty(), "Def ${def.id} reactsTo is empty")
        }
    }

    @Test
    fun everyDerivedDefDeclaresAResolverAndEveryCounterDefDoesNot() {
        AchievementCatalog.all.forEach { def ->
            when (def.family) {
                Family.DERIVED -> assertTrue(def.resolver != null, "DERIVED ${def.id} missing resolver")
                Family.COUNTER -> assertNull(def.resolver, "COUNTER ${def.id} should not have a resolver")
            }
        }
    }

    @Test
    fun eventIndexCoversEveryDefExactlyViaItsReactsToSet() {
        val indexed = AchievementCatalog.defsByEventType.values.flatten().toSet()
        assertEquals(AchievementCatalog.all.toSet(), indexed)
        AchievementCatalog.defsByEventType.forEach { (eventClass, defs) ->
            defs.forEach { def ->
                assertTrue(eventClass in def.reactsTo, "Def ${def.id} indexed under $eventClass it does not react to")
            }
        }
    }

    @Test
    fun unlocksListIsAlwaysEmpty() {
        AchievementCatalog.all.forEach { def ->
            assertTrue(def.unlocks.isEmpty(), "Def ${def.id} must not declare unlocks")
        }
    }

    @Test
    fun byIdResolvesKnownIdsAndReturnsNullForUnknown() {
        assertEquals("FIRST_WIN", AchievementCatalog.byId("FIRST_WIN")?.id)
        assertNull(AchievementCatalog.byId("NOPE_NOT_REAL"))
    }

    // ── Copy (G-13) ───────────────────────────────────────────────────────────

    @Test
    fun everyDescriptionStatesEachOfItsThresholdsAboveOne() {
        AchievementCatalog.all.forEach { def ->
            def.tiers.map { it.threshold }.filter { it > 1 }.forEach { threshold ->
                assertTrue(
                    mentions(def.description, threshold),
                    "Def ${def.id} description \"${def.description}\" does not state its threshold $threshold",
                )
            }
        }
    }

    @Test
    fun singleTierDescriptionsDoNotListOtherTiers() {
        AchievementCatalog.all.filter { it.tiers.size == 1 }.forEach { def ->
            assertFalse(def.description.contains(" / "), "Single-tier def ${def.id} lists several tiers")
        }
    }

    @Test
    fun noDescriptionOrTitleCarriesAFormatPlaceholder() {
        AchievementCatalog.all.forEach { def ->
            assertFalse("%" in def.description || "%" in def.title, "Def ${def.id} carries a format placeholder")
        }
    }

    @Test
    fun noTwoDerivedDefsShareAResolverAndThreshold() {
        val pairs = AchievementCatalog.all
            .filter { it.family == Family.DERIVED }
            .flatMap { def -> def.tiers.map { tier -> Triple(def.resolver, tier.threshold, def.id) } }
        val duplicates = pairs.groupBy { it.first to it.second }.filterValues { it.size > 1 }
        assertTrue(duplicates.isEmpty(), "Defs share a (resolver, threshold): ${duplicates.values.map { g -> g.map { it.third } }}")
    }

    @Test
    fun rainbowCollectorNeedsOneCardPerColorAndTheSecretNeedsTwenty() {
        assertEquals(AchievementResolver.COLORS_WITH_ANY, AchievementCatalog.byId("RAINBOW_COLLECTOR")?.resolver)
        assertEquals(AchievementResolver.COLORS_WITH_20_PLUS, AchievementCatalog.byId("SECRET_PERFECT_RAINBOW")?.resolver)
    }

    // ── Availability (D4/D5) ─────────────────────────────────────────────────

    @Test
    fun tournamentWinIsUnavailableAndPuzzleSolverFollowsItsFlag() {
        assertFalse(AchievementCatalog.byId("TOURNAMENT_WIN")!!.isAvailable)
        assertEquals(FeatureFlags.Puzzle.PUZZLE_ENABLED, AchievementCatalog.byId("PUZZLE_SOLVER")!!.isAvailable)
        val gated = AchievementCatalog.all.filter { it.availability != CatalogAvailability.ALWAYS }.map { it.id }.toSet()
        assertEquals(setOf("TOURNAMENT_WIN", "PUZZLE_SOLVER"), gated)
    }

    private fun mentions(text: String, value: Int): Boolean {
        val grouped = value.toString().reversed().chunked(3).joinToString(",").reversed()
        return listOf(value.toString(), grouped).any { token ->
            Regex("(?<![\\d,])${Regex.escape(token)}(?![\\d,])").containsMatchIn(text)
        }
    }
}
