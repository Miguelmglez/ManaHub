package com.mmg.manahub.feature.tagdictionary

import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.tagging.TagDictionary
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.tagging.TagOverride
import com.mmg.manahub.feature.tagdictionary.presentation.TagDictionaryEvent
import com.mmg.manahub.feature.tagdictionary.presentation.TagDictionaryRow
import com.mmg.manahub.feature.tagdictionary.presentation.TagDictionaryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TagDictionaryViewModel].
 *
 * Covers:
 * - setAutoThreshold / setSuggestThreshold: cross-adjustment + coercion, now under a single
 *   serialized write path (F5)
 * - saveOverride / resetEntry / resetAll: forward to the repository with the correct args
 * - F4: `rows` updates reactively from [TagDictionaryRepository.overridesFlow] — no manual
 *   "refresh after every mutation" call is needed anymore
 * - D12: [TagDictionaryViewModel.createCustomTag] always mints a `custom_`-prefixed, collision-avoided key
 * - F2: `resetAll` emits [TagDictionaryEvent.CustomTagsCleared] for the Screen's success toast
 * - onQueryChange / onStartEdit / onDismissEdit / create+reset dialog toggles
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagDictionaryViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dictionaryRepo = mockk<TagDictionaryRepository>(relaxed = true)
    private val prefs          = mockk<UserPreferencesDataStore>(relaxed = true)

    // Expose mutable flows so individual tests can control emitted values
    private val autoThresholdFlow    = MutableStateFlow(0.90f)
    private val suggestThresholdFlow = MutableStateFlow(0.60f)
    private val overridesFlow        = MutableStateFlow<List<TagOverride>>(emptyList())

    private lateinit var viewModel: TagDictionaryViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRow(
        key:      String       = "custom_flying",
        category: TagCategory  = TagCategory.CUSTOM,
        labelEn:  String       = "Flying",
        rules:    List<String> = emptyList(),
    ) = TagDictionaryRow(
        key      = key,
        category = category,
        labelEn  = labelEn,
        rules    = rules,
    )

    private fun buildViewModel(): TagDictionaryViewModel {
        every { prefs.tagAutoThresholdFlow }    returns autoThresholdFlow
        every { prefs.tagSuggestThresholdFlow } returns suggestThresholdFlow
        every { dictionaryRepo.overridesFlow }  returns overridesFlow
        coEvery { dictionaryRepo.loadAndApply() } returns Unit

        return TagDictionaryViewModel(
            dictionaryRepo = dictionaryRepo,
            prefs          = prefs,
        )
    }

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // logBreadcrumb()/the suggest-threshold .catch{} both reach FirebaseCrashlytics directly.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
        // The VM's overridesFlow collector mutates the real TagDictionary singleton — reset it
        // so test-to-test contamination never leaks a custom_ key into an unrelated test.
        TagDictionary.applyOverrides(emptyList())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — setAutoThreshold
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given suggestThreshold 0_77 when setAutoThreshold 0_80 then saveTagSuggestThreshold is called with adjusted value`() = runTest {
        // Arrange: auto=0.80 would mean suggest must be <= 0.75
        // Current suggest (from flow) = 0.77 which is > 0.80 - 0.05 = 0.75 → needs adjustment
        suggestThresholdFlow.value = 0.77f
        advanceUntilIdle()

        // Act
        viewModel.setAutoThreshold(0.80f)
        advanceUntilIdle()

        // Assert: suggest threshold was adjusted down to 0.80 - 0.05 = 0.75
        coVerify { prefs.saveTagAutoThreshold(0.80f) }
        coVerify { prefs.saveTagSuggestThreshold(0.75f) }
    }

    @Test
    fun `given suggestThreshold 0_70 when setAutoThreshold 0_80 then saveTagSuggestThreshold is NOT called`() = runTest {
        // Arrange: suggest=0.70 is already <= 0.80 - 0.05 = 0.75 → no adjustment needed
        suggestThresholdFlow.value = 0.70f
        advanceUntilIdle()

        // Act
        viewModel.setAutoThreshold(0.80f)
        advanceUntilIdle()

        // Assert: auto threshold saved, but suggest not touched
        coVerify { prefs.saveTagAutoThreshold(0.80f) }
        coVerify(exactly = 0) { prefs.saveTagSuggestThreshold(any()) }
    }

    @Test
    fun `given value below minimum when setAutoThreshold then value is coerced to 0_05`() = runTest {
        // Act
        viewModel.setAutoThreshold(0.0f)
        advanceUntilIdle()

        // Assert: coerced to the minimum allowed value
        coVerify { prefs.saveTagAutoThreshold(0.05f) }
    }

    @Test
    fun `given value above maximum when setAutoThreshold then value is coerced to 1_0`() = runTest {
        // Act
        viewModel.setAutoThreshold(1.5f)
        advanceUntilIdle()

        // Assert: coerced to 1.0
        coVerify { prefs.saveTagAutoThreshold(1.0f) }
    }

    @Test
    fun `given two rapid threshold calls when run concurrently then both writes complete without interleaving`() = runTest {
        // F5 regression guard: setAutoThreshold and setSuggestThreshold share a single
        // serialized write path (thresholdMutex) so a rapid drag on both sliders can't
        // interleave their read-modify-write sequences.
        viewModel.setAutoThreshold(0.95f)
        viewModel.setSuggestThreshold(0.50f)
        advanceUntilIdle()

        coVerify { prefs.saveTagAutoThreshold(0.95f) }
        coVerify { prefs.saveTagSuggestThreshold(0.50f) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — setSuggestThreshold
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given autoThreshold 0_90 when setSuggestThreshold with value above auto-0_05 then value is coerced down`() = runTest {
        // Arrange: auto=0.90 → max allowed suggest = 0.90 - 0.05 = 0.85
        autoThresholdFlow.value = 0.90f
        advanceUntilIdle()

        // Act: try to set suggest = 0.88 (above max 0.85)
        viewModel.setSuggestThreshold(0.88f)
        advanceUntilIdle()

        // Assert: coerced to 0.85
        coVerify { prefs.saveTagSuggestThreshold(0.85f) }
    }

    @Test
    fun `given autoThreshold 0_90 when setSuggestThreshold with valid value then value is saved as-is`() = runTest {
        // Arrange
        autoThresholdFlow.value = 0.90f
        advanceUntilIdle()

        // Act: 0.70 is well within the allowed range
        viewModel.setSuggestThreshold(0.70f)
        advanceUntilIdle()

        // Assert: saved unchanged
        coVerify { prefs.saveTagSuggestThreshold(0.70f) }
    }

    @Test
    fun `given negative value when setSuggestThreshold then value is coerced to 0`() = runTest {
        // Act
        viewModel.setSuggestThreshold(-0.10f)
        advanceUntilIdle()

        // Assert: lower bound is 0
        coVerify { prefs.saveTagSuggestThreshold(0f) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — saveOverride
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid row when saveOverride then dictionaryRepo_upsert is called with correct TagOverride`() = runTest {
        // Arrange
        val row = buildRow(
            key     = "custom_flying",
            labelEn = "Flying",
            rules   = listOf("gain + life + !gain control"),
        )
        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert: English-only label, rule lines forwarded as-is.
        coVerify {
            dictionaryRepo.upsert(
                match { override ->
                    override.key == "custom_flying" &&
                    override.labels["en"] == "Flying" &&
                    override.labels["es"] == null &&
                    override.patterns == listOf("gain + life + !gain control")
                }
            )
        }
    }

    @Test
    fun `given valid row when saveOverride then editingKey is cleared after save`() = runTest {
        // Arrange
        val row = buildRow(key = "custom_flying")
        viewModel.onStartEdit("custom_flying")
        advanceUntilIdle()
        assertEquals("custom_flying", viewModel.state.value.editingKey)

        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert: editingKey reset after save
        assertNull(viewModel.state.value.editingKey)
    }

    @Test
    fun `given a repository emission after saveOverride then rows update reactively without a manual refresh call`() = runTest {
        // Arrange
        val row = buildRow(key = "custom_flying", labelEn = "Flying")
        coEvery { dictionaryRepo.upsert(any()) } coAnswers {
            // Simulate the repository's real behavior: a successful upsert re-emits overridesFlow.
            overridesFlow.value = listOf(firstArg())
        }

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert: the VM's reactive overridesFlow collector rebuilt rows from the new emission —
        // no explicit "refreshRows"/loadAndApply call was needed after the mutation.
        assertTrue(viewModel.state.value.rows.any { it.key == "custom_flying" && it.labelEn == "Flying" })
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — resetEntry
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given key when resetEntry then dictionaryRepo_delete is called with that key`() = runTest {
        // Arrange
        coEvery { dictionaryRepo.delete(any()) } returns Unit

        // Act
        viewModel.resetEntry("custom_flying")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { dictionaryRepo.delete("custom_flying") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — resetAll (F2)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when resetAll then dictionaryRepo_resetAll is called`() = runTest {
        // Arrange
        coEvery { dictionaryRepo.resetAll() } returns Unit

        // Act
        viewModel.resetAll()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { dictionaryRepo.resetAll() }
    }

    @Test
    fun `when resetAll completes then CustomTagsCleared event is emitted for the toast`() = runTest {
        coEvery { dictionaryRepo.resetAll() } returns Unit

        viewModel.events.test {
            viewModel.resetAll()
            assertEquals(TagDictionaryEvent.CustomTagsCleared, awaitItem())
        }
    }

    @Test
    fun `when resetAll completes then isConfirmingResetAll is cleared`() = runTest {
        coEvery { dictionaryRepo.resetAll() } returns Unit
        viewModel.onRequestResetAll()
        assertTrue(viewModel.state.value.isConfirmingResetAll)

        viewModel.resetAll()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isConfirmingResetAll)
    }

    @Test
    fun `given onRequestResetAll then isConfirmingResetAll becomes true and onDismissResetAll clears it`() = runTest {
        viewModel.onRequestResetAll()
        assertTrue(viewModel.state.value.isConfirmingResetAll)

        viewModel.onDismissResetAll()
        assertFalse(viewModel.state.value.isConfirmingResetAll)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5b — D12: createCustomTag mints a collision-avoided custom_ key
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a blank label when createCustomTag then upsert is never called`() = runTest {
        viewModel.createCustomTag(label = "   ", rules = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { dictionaryRepo.upsert(any()) }
    }

    @Test
    fun `given a label when createCustomTag then upsert is called with a slugified custom_ key`() = runTest {
        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        viewModel.createCustomTag(label = "My Cool Tag!", rules = listOf("some rule"))
        advanceUntilIdle()

        coVerify {
            dictionaryRepo.upsert(
                match { override ->
                    override.key == "custom_my_cool_tag" &&
                    override.labels["en"] == "My Cool Tag!" &&
                    override.category == TagCategory.CUSTOM &&
                    override.patterns == listOf("some rule")
                }
            )
        }
    }

    @Test
    fun `given a colliding slug when createCustomTag then a numeric suffix is appended`() = runTest {
        coEvery { dictionaryRepo.upsert(any()) } coAnswers {
            overridesFlow.value = overridesFlow.value + firstArg<TagOverride>()
        }

        // First tag claims "custom_my_tag".
        viewModel.createCustomTag(label = "My Tag", rules = emptyList())
        advanceUntilIdle()

        // Second tag with the same slug must not collide.
        viewModel.createCustomTag(label = "My Tag", rules = emptyList())
        advanceUntilIdle()

        coVerify { dictionaryRepo.upsert(match { it.key == "custom_my_tag" }) }
        coVerify { dictionaryRepo.upsert(match { it.key == "custom_my_tag_2" }) }
    }

    @Test
    fun `given a successful createCustomTag when it completes then isCreatingCustomTag is cleared`() = runTest {
        coEvery { dictionaryRepo.upsert(any()) } returns Unit
        viewModel.onStartCreateCustomTag()
        assertTrue(viewModel.state.value.isCreatingCustomTag)

        viewModel.createCustomTag(label = "New Tag", rules = emptyList())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCreatingCustomTag)
    }

    @Test
    fun `given onStartCreateCustomTag then isCreatingCustomTag becomes true and onDismiss clears it`() = runTest {
        viewModel.onStartCreateCustomTag()
        assertTrue(viewModel.state.value.isCreatingCustomTag)

        viewModel.onDismissCreateCustomTag()
        assertFalse(viewModel.state.value.isCreatingCustomTag)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — onQueryChange
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given initial empty query when onQueryChange called then state query is updated`() = runTest {
        // Arrange
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.query)

        // Act
        viewModel.onQueryChange("flying")

        // Assert
        assertEquals("flying", viewModel.state.value.query)
    }

    @Test
    fun `given non-empty query when onQueryChange called with empty string then query is cleared`() = runTest {
        // Arrange
        viewModel.onQueryChange("removal")
        assertEquals("removal", viewModel.state.value.query)

        // Act
        viewModel.onQueryChange("")

        // Assert
        assertEquals("", viewModel.state.value.query)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — onStartEdit / onDismissEdit
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no editing key when onStartEdit then editingKey is set`() = runTest {
        // Arrange
        advanceUntilIdle()
        assertNull(viewModel.state.value.editingKey)

        // Act
        viewModel.onStartEdit("custom_flying")

        // Assert
        assertEquals("custom_flying", viewModel.state.value.editingKey)
    }

    @Test
    fun `given editingKey is set when onDismissEdit then editingKey is null`() = runTest {
        // Arrange
        viewModel.onStartEdit("custom_removal")
        assertEquals("custom_removal", viewModel.state.value.editingKey)

        // Act
        viewModel.onDismissEdit()

        // Assert
        assertNull(viewModel.state.value.editingKey)
    }

    @Test
    fun `given one key being edited when onStartEdit with different key then new key replaces old`() = runTest {
        // Arrange
        viewModel.onStartEdit("custom_flying")
        assertEquals("custom_flying", viewModel.state.value.editingKey)

        // Act
        viewModel.onStartEdit("custom_trample")

        // Assert: key is replaced, not stacked
        assertEquals("custom_trample", viewModel.state.value.editingKey)
    }
}
