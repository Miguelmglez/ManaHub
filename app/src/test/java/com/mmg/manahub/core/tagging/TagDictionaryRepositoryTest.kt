package com.mmg.manahub.core.tagging

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.TagCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TagDictionaryRepository].
 *
 * Covers:
 * - upsert: adds new override when key does not exist
 * - upsert: replaces existing override with the same key
 * - delete: removes the override with the matching key
 * - delete: with non-existent key is a no-op (no crash)
 * - resetAll: clears all overrides
 * - overridesFlow: emits decoded list when DataStore JSON changes
 * - Concurrent upsert + upsert: both changes persisted (mutex correctness test)
 * - Invalid JSON in DataStore: decode returns empty list (no crash)
 *
 * NOTE ON MUTEX TEST DESIGN:
 * The writeMutex ensures that read-modify-write operations are serialised.
 * We simulate concurrent calls by launching two coroutines and letting
 * advanceUntilIdle() interleave them through the TestCoroutineDispatcher.
 * Because upsert is guarded by Mutex.withLock, the second call must wait
 * for the first to complete before reading DataStore, guaranteeing that
 * both changes are preserved in the final JSON.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagDictionaryRepositoryTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val prefs = mockk<UserPreferencesDataStore>(relaxed = true)

    // In-memory DataStore emulator: tracks what has been "saved" so that
    // subsequent reads via tagDictionaryOverridesFlow return the latest value.
    private val savedJsonFlow = MutableStateFlow("[]")

    private lateinit var repo: TagDictionaryRepository

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Wire the mock so reads always reflect the last written value
        every { prefs.tagDictionaryOverridesFlow } returns savedJsonFlow
        coEvery { prefs.saveTagDictionaryOverrides(any()) } coAnswers {
            savedJsonFlow.value = firstArg()
        }

        repo = TagDictionaryRepository(prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Reset the global singleton to avoid test-to-test contamination
        TagDictionary.applyOverrides(emptyList())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — upsert
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty overrides when upsert then new override is added and persisted`() = runTest {
        // Arrange
        val override = TagOverride(
            key      = "custom_key",
            category = TagCategory.STRATEGY,
            labels   = mapOf("en" to "Custom Label"),
            patterns = listOf("custom pattern"),
        )

        // Act
        repo.upsert(override)

        // Assert: the override is now in the flow
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_key", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given existing override when upsert with same key then override is replaced not duplicated`() = runTest {
        // Arrange — insert first version
        val original = TagOverride(
            key      = "flying",
            category = TagCategory.KEYWORD,
            labels   = mapOf("en" to "Flying v1"),
            patterns = emptyList(),
        )
        repo.upsert(original)

        // Act — insert updated version with same key
        val updated = TagOverride(
            key      = "flying",
            category = TagCategory.KEYWORD,
            labels   = mapOf("en" to "Flying v2", "es" to "Volar"),
            patterns = emptyList(),
        )
        repo.upsert(updated)

        // Assert: only one entry exists, and it holds the latest labels
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Flying v2", list.first().labels["en"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — delete
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given existing override when delete with matching key then override is removed`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))
        repo.upsert(TagOverride(key = "trample", labels = mapOf("en" to "Trample"), patterns = emptyList()))

        // Act
        repo.delete("flying")

        // Assert: only "trample" remains
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("trample", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given non-existent key when delete then no crash and list is unchanged`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))

        // Act — delete a key that was never inserted
        repo.delete("nonexistent_key")

        // Assert: the existing override is still present
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("flying", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given empty list when delete is called then no crash`() = runTest {
        // Arrange — no overrides at all
        // Act + Assert: should not throw
        repo.delete("any_key")
        // If we reach this line, no exception was thrown
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — resetAll
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given multiple overrides when resetAll then all overrides are cleared`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "flying",  labels = mapOf("en" to "Flying"),  patterns = emptyList()))
        repo.upsert(TagOverride(key = "trample", labels = mapOf("en" to "Trample"), patterns = emptyList()))
        repo.upsert(TagOverride(key = "haste",   labels = mapOf("en" to "Haste"),   patterns = emptyList()))

        // Act
        repo.resetAll()

        // Assert: the flow now returns an empty list
        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue("Expected empty list after resetAll", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when resetAll then saveTagDictionaryOverrides is called with empty JSON array`() = runTest {
        // Act
        repo.resetAll()

        // Assert
        coVerify { prefs.saveTagDictionaryOverrides("[]") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — overridesFlow
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid JSON in DataStore when overridesFlow emits then list is decoded correctly`() = runTest {
        // Arrange — set valid JSON directly in the emulator
        savedJsonFlow.value =
            """[{"key":"flying","category":"KEYWORD","labels":{"en":"Flying"},"patterns":{}}]"""

        // Assert: flow decodes it correctly
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("flying", list.first().key)
            assertEquals(TagCategory.KEYWORD, list.first().category)
            assertEquals("Flying", list.first().labels["en"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given DataStore changes when overridesFlow then new emission reflects the change`() = runTest {
        // Arrange
        savedJsonFlow.value = "[]"

        repo.overridesFlow.test {
            // First emission: empty
            val first = awaitItem()
            assertTrue(first.isEmpty())

            // Act: simulate a DataStore update
            repo.upsert(TagOverride(key = "burn", labels = mapOf("en" to "Burn"), patterns = emptyList()))

            // Second emission: contains the new override
            val second = awaitItem()
            assertEquals(1, second.size)
            assertEquals("burn", second.first().key)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Invalid JSON resilience
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given invalid JSON in DataStore when overridesFlow emits then empty list is returned without crash`() = runTest {
        // Arrange — corrupt JSON (simulates a DataStore write error or upgrade bug)
        savedJsonFlow.value = "THIS IS NOT JSON {{{}"

        // Assert: decode is resilient and returns empty list
        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue("Expected empty list for invalid JSON", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given null-producing JSON when overridesFlow emits then empty list is returned`() = runTest {
        // Arrange — "null" is valid JSON that Gson parses as null
        savedJsonFlow.value = "null"

        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given JSON with unknown category string when overridesFlow emits then category is null without crash`() = runTest {
        // Arrange — category field has an unknown enum value
        savedJsonFlow.value =
            """[{"key":"custom","category":"DOES_NOT_EXIST","labels":{"en":"Custom"},"patterns":{}}]"""

        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            // Unknown category decodes to null (see decode() in repository)
            assertFalse("Category should be null for unknown enum value", list.first().category != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given new-shape JSON with array patterns when overridesFlow emits then rule lines are decoded`() = runTest {
        // Arrange — canonical new shape: patterns is an array of rule-line strings.
        savedJsonFlow.value =
            """[{"key":"lifegain","category":"STRATEGY","labels":{"en":"Lifegain"},"patterns":["gain + life + !gain control"]}]"""

        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("lifegain", list.first().key)
            assertEquals(listOf("gain + life + !gain control"), list.first().patterns)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given legacy JSON with map patterns and es-de labels when decoded then only en is kept and re-persisted in new shape`() = runTest {
        // Arrange — legacy shape: map-shaped patterns + es/de labels.
        val legacy =
            """[{"key":"removal","category":"ROLE","labels":{"en":"Removal","es":"Remoción","de":"Entfernung"},"patterns":{"en":["destroy target"],"es":["destruye"],"de":["zerstöre"]}}]"""
        savedJsonFlow.value = legacy

        // Act — decode keeps en-only.
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val ov = list.first()
            assertEquals("Removal", ov.labels["en"])
            assertEquals(null, ov.labels["es"])
            assertEquals(null, ov.labels["de"])
            // The legacy "en" pattern list survives as a single rule line.
            assertEquals(listOf("destroy target"), ov.patterns)
            cancelAndIgnoreRemainingEvents()
        }

        // Act — loadAndApply performs the one-time silent migration to the new shape.
        repo.loadAndApply()

        // Assert — the persisted JSON is now the new array shape with no es/de.
        val migrated = savedJsonFlow.value
        assertTrue("patterns must be an array now", migrated.contains("[\"destroy target\"]"))
        assertFalse("es label must be dropped", migrated.contains("Remoción"))
        assertFalse("de label must be dropped", migrated.contains("Entfernung"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — Concurrent upsert: mutex correctness (regression guard)
    //
    //  BEFORE FIX: plain read-modify-write without locking meant that two
    //  concurrent coroutines could both read "[]", each append their own
    //  override, and the second writer would clobber the first.
    //
    //  AFTER FIX: writeMutex.withLock serialises the operations so both
    //  overrides always end up in the final list.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two concurrent upsert calls then both overrides are persisted without data loss`() = runTest {
        // Arrange
        val overrideA = TagOverride(key = "flying",  labels = mapOf("en" to "Flying"),  patterns = emptyList())
        val overrideB = TagOverride(key = "trample", labels = mapOf("en" to "Trample"), patterns = emptyList())

        // Act — launch both upserts concurrently
        val jobA = async { repo.upsert(overrideA) }
        val jobB = async { repo.upsert(overrideB) }
        jobA.await()
        jobB.await()

        // Assert: BOTH overrides are present — the mutex prevented data loss
        repo.overridesFlow.test {
            val list = awaitItem()
            val keys = list.map { it.key }.toSet()
            assertEquals(
                "Both overrides must be persisted after concurrent upserts. " +
                "If only one is present, the mutex is not working correctly.",
                setOf("flying", "trample"),
                keys
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given concurrent upsert and delete when run simultaneously then final state is consistent`() = runTest {
        // Arrange — seed one existing override
        repo.upsert(TagOverride(key = "flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))

        // Act — concurrently add a new key and delete the existing one
        val upsertJob = async { repo.upsert(TagOverride(key = "haste", labels = mapOf("en" to "Haste"), patterns = emptyList())) }
        val deleteJob = async { repo.delete("flying") }
        upsertJob.await()
        deleteJob.await()

        // Assert: end state has "haste" and does NOT have "flying"
        repo.overridesFlow.test {
            val list = awaitItem()
            val keys = list.map { it.key }
            assertTrue("haste should be present", keys.contains("haste"))
            assertFalse("flying should have been deleted", keys.contains("flying"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
