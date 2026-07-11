package com.mmg.manahub.core.tagging

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.TagCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
 * - upsert: adds new `custom_`-keyed override when key does not exist
 * - upsert: replaces existing `custom_`-keyed override with the same key
 * - D12: upsert REJECTS any key that does not start with `custom_` (system tags are read-only)
 * - delete: removes the override with the matching key
 * - delete: with non-existent key is a no-op (no crash)
 * - resetAll: clears all overrides ("delete all custom tags")
 * - overridesFlow: emits decoded list when DataStore JSON changes (decode is read-only, not D12-filtered)
 * - Concurrent upsert + upsert: both changes persisted (mutex correctness test)
 * - F1: corrupt JSON never wipes prior state — decode falls back to the last-known-good value
 * - D12 migration: `loadAndApply` retires (drops) any override targeting a system key
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
        // decode()/upsert()/loadAndApply() call recordNonFatal on corrupt/rejected paths,
        // which reaches FirebaseCrashlytics.getInstance() — must be mocked in unit tests.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

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
        unmockkStatic(FirebaseCrashlytics::class)
        // Reset the global singleton to avoid test-to-test contamination
        TagDictionary.applyOverrides(emptyList())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — upsert (custom_-keyed, per D12)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty overrides when upsert then new custom override is added and persisted`() = runTest {
        // Arrange
        val override = TagOverride(
            key      = "custom_key",
            category = TagCategory.CUSTOM,
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
    fun `given existing custom override when upsert with same key then override is replaced not duplicated`() = runTest {
        // Arrange — insert first version
        val original = TagOverride(
            key      = "custom_flying",
            category = TagCategory.CUSTOM,
            labels   = mapOf("en" to "Flying v1"),
            patterns = emptyList(),
        )
        repo.upsert(original)

        // Act — insert updated version with same key
        val updated = TagOverride(
            key      = "custom_flying",
            category = TagCategory.CUSTOM,
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
    //  GROUP 1b — D12: system dictionary tags are read-only
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given key without custom_ prefix when upsert then override is rejected and not persisted`() = runTest {
        // Act — attempt to upsert a SYSTEM key (no custom_ prefix)
        repo.upsert(TagOverride(key = "removal", labels = mapOf("en" to "Removal Override"), patterns = emptyList()))

        // Assert: nothing was persisted
        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue("A non-custom_ key must never be persisted", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { prefs.saveTagDictionaryOverrides(any()) }
    }

    @Test
    fun `given mix of custom and system keys when upserted then only the custom one is persisted`() = runTest {
        // Act
        repo.upsert(TagOverride(key = "custom_my_tag", labels = mapOf("en" to "My Tag"), patterns = emptyList()))
        repo.upsert(TagOverride(key = "board_wipe", labels = mapOf("en" to "Hijacked"), patterns = emptyList()))

        // Assert
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_my_tag", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — delete
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given existing override when delete with matching key then override is removed`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "custom_flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))
        repo.upsert(TagOverride(key = "custom_trample", labels = mapOf("en" to "Trample"), patterns = emptyList()))

        // Act
        repo.delete("custom_flying")

        // Assert: only "custom_trample" remains
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_trample", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given non-existent key when delete then no crash and list is unchanged`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "custom_flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))

        // Act — delete a key that was never inserted
        repo.delete("nonexistent_key")

        // Assert: the existing override is still present
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_flying", list.first().key)
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
    //  GROUP 3 — resetAll (F2: "delete all custom tags")
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given multiple custom overrides when resetAll then all overrides are cleared`() = runTest {
        // Arrange
        repo.upsert(TagOverride(key = "custom_flying",  labels = mapOf("en" to "Flying"),  patterns = emptyList()))
        repo.upsert(TagOverride(key = "custom_trample", labels = mapOf("en" to "Trample"), patterns = emptyList()))
        repo.upsert(TagOverride(key = "custom_haste",   labels = mapOf("en" to "Haste"),   patterns = emptyList()))

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
    //  GROUP 4 — overridesFlow (pure decode, not D12-gated — D12 only guards writes)
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
            repo.upsert(TagOverride(key = "custom_burn", labels = mapOf("en" to "Burn"), patterns = emptyList()))

            // Second emission: contains the new override
            val second = awaitItem()
            assertEquals(1, second.size)
            assertEquals("custom_burn", second.first().key)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Invalid JSON resilience (F1)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given invalid JSON with no prior good state when overridesFlow emits then empty list is returned without crash`() = runTest {
        // Arrange — corrupt JSON (simulates a DataStore write error or upgrade bug), no prior
        // successful decode has happened yet, so last-known-good is still the initial empty list.
        savedJsonFlow.value = "THIS IS NOT JSON {{{}"

        // Assert: decode is resilient and returns empty list (== last-known-good == initial state)
        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue("Expected empty list for invalid JSON with no prior state", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given corrupt JSON after a previously-valid state when decoded then last-known-good is returned not empty`() = runTest {
        // Arrange — prime a valid, non-empty state first.
        repo.upsert(TagOverride(key = "custom_flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))

        // Act — the DataStore blob becomes corrupt out from under the repo (e.g. a partial
        // write, external corruption). decode() must NOT treat this as "no overrides".
        savedJsonFlow.value = "THIS IS NOT JSON {{{}"

        // Assert: the previously-good override is still returned, not an empty list.
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_flying", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given corrupt stored blob when upsert runs then it never overwrites the blob using an empty base`() = runTest {
        // Arrange — prime a valid state, then corrupt the stored blob directly (bypassing upsert,
        // simulating external corruption) so the repo's next read of the raw JSON fails to parse.
        repo.upsert(TagOverride(key = "custom_flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))
        savedJsonFlow.value = "THIS IS NOT JSON {{{}"

        // Act — upsert a second custom override while the stored blob is corrupt.
        repo.upsert(TagOverride(key = "custom_trample", labels = mapOf("en" to "Trample"), patterns = emptyList()))

        // Assert: the write is based on last-known-good, so BOTH overrides now exist —
        // the corrupt read never silently downgraded the base state to an empty list.
        repo.overridesFlow.test {
            val list = awaitItem()
            val keys = list.map { it.key }.toSet()
            assertEquals(setOf("custom_flying", "custom_trample"), keys)
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
            """[{"key":"custom_x","category":"DOES_NOT_EXIST","labels":{"en":"Custom"},"patterns":{}}]"""

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
            """[{"key":"custom_lifegain","category":"CUSTOM","labels":{"en":"Lifegain"},"patterns":["gain + life + !gain control"]}]"""

        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_lifegain", list.first().key)
            assertEquals(listOf("gain + life + !gain control"), list.first().patterns)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given legacy JSON with map patterns and es-de labels for a custom key when decoded then only en is kept and re-persisted in new shape`() = runTest {
        // Arrange — legacy shape: map-shaped patterns + es/de labels, on a `custom_` key so the
        // shape migration is exercised independently of the D12 system-key retirement migration.
        val legacy =
            """[{"key":"custom_removal","category":"ROLE","labels":{"en":"Removal","es":"Remoción","de":"Entfernung"},"patterns":{"en":["destroy target"],"es":["destruye"],"de":["zerstöre"]}}]"""
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

        // Assert — the persisted JSON is now the new array shape with no es/de, and the
        // custom_-keyed override is preserved (not retired — it is not a system key).
        val migrated = savedJsonFlow.value
        assertTrue("patterns must be an array now", migrated.contains("[\"destroy target\"]"))
        assertTrue("custom key must survive migration", migrated.contains("custom_removal"))
        assertFalse("es label must be dropped", migrated.contains("Remoción"))
        assertFalse("de label must be dropped", migrated.contains("Entfernung"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5b — D12 migration: retiring overrides that target system keys
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a legacy override on a system key when loadAndApply runs then it is retired not just re-shaped`() = runTest {
        // Arrange — a pre-D12 override targeting the system key "removal" (no custom_ prefix).
        savedJsonFlow.value =
            """[{"key":"removal","category":"ROLE","labels":{"en":"My Removal"},"patterns":["destroy target"]}]"""

        // Act
        repo.loadAndApply()

        // Assert — the override is dropped entirely, not merely re-encoded.
        repo.overridesFlow.test {
            val list = awaitItem()
            assertTrue("System-key override must be retired by the D12 migration", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a mix of system-key and custom-key overrides when loadAndApply runs then only the custom one survives`() = runTest {
        // Arrange
        savedJsonFlow.value = """[
            {"key":"removal","category":"ROLE","labels":{"en":"Hijacked"},"patterns":[]},
            {"key":"custom_my_tag","category":"CUSTOM","labels":{"en":"My Tag"},"patterns":["foo"]}
        ]""".trimIndent()

        // Act
        repo.loadAndApply()

        // Assert
        repo.overridesFlow.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("custom_my_tag", list.first().key)
            cancelAndIgnoreRemainingEvents()
        }
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
        val overrideA = TagOverride(key = "custom_flying",  labels = mapOf("en" to "Flying"),  patterns = emptyList())
        val overrideB = TagOverride(key = "custom_trample", labels = mapOf("en" to "Trample"), patterns = emptyList())

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
                setOf("custom_flying", "custom_trample"),
                keys
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given concurrent upsert and delete when run simultaneously then final state is consistent`() = runTest {
        // Arrange — seed one existing override
        repo.upsert(TagOverride(key = "custom_flying", labels = mapOf("en" to "Flying"), patterns = emptyList()))

        // Act — concurrently add a new key and delete the existing one
        val upsertJob = async { repo.upsert(TagOverride(key = "custom_haste", labels = mapOf("en" to "Haste"), patterns = emptyList())) }
        val deleteJob = async { repo.delete("custom_flying") }
        upsertJob.await()
        deleteJob.await()

        // Assert: end state has "custom_haste" and does NOT have "custom_flying"
        repo.overridesFlow.test {
            val list = awaitItem()
            val keys = list.map { it.key }
            assertTrue("custom_haste should be present", keys.contains("custom_haste"))
            assertFalse("custom_flying should have been deleted", keys.contains("custom_flying"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
