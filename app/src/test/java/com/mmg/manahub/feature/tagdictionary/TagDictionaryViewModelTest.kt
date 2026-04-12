package com.mmg.manahub.feature.tagdictionary

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.tagging.TagDictionary
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.tagging.TagOverride
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TagDictionaryViewModel].
 *
 * Covers:
 * - setAutoThreshold: adjusts suggestThreshold down when too close, coerces min
 * - setSuggestThreshold: coerces to autoThreshold - 0.05f max
 * - saveOverride: calls dictionaryRepo.upsert() then refreshRows()
 * - resetEntry: calls dictionaryRepo.delete(key) then refreshRows()
 * - resetAll: calls dictionaryRepo.resetAll() then refreshRows()
 * - onQueryChange: updates query in state
 * - onStartEdit / onDismissEdit: toggle editingKey
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

    private lateinit var viewModel: TagDictionaryViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRow(
        key:        String     = "flying",
        category:   TagCategory = TagCategory.KEYWORD,
        labelEn:    String     = "Flying",
        labelEs:    String     = "Volar",
        labelDe:    String     = "Fliegend",
        patternsEn: List<String> = emptyList(),
        patternsEs: List<String> = emptyList(),
        patternsDe: List<String> = emptyList(),
    ) = TagDictionaryRow(
        key        = key,
        category   = category,
        labelEn    = labelEn,
        labelEs    = labelEs,
        labelDe    = labelDe,
        patternsEn = patternsEn,
        patternsEs = patternsEs,
        patternsDe = patternsDe,
    )

    private fun buildViewModel(): TagDictionaryViewModel {
        every { prefs.tagAutoThresholdFlow }    returns autoThresholdFlow
        every { prefs.tagSuggestThresholdFlow } returns suggestThresholdFlow
        every { dictionaryRepo.overridesFlow }  returns flowOf(emptyList())
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
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            key     = "flying",
            labelEn = "Flying",
            labelEs = "Volar",
            labelDe = "Fliegend",
        )
        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert
        coVerify {
            dictionaryRepo.upsert(
                match { override ->
                    override.key == "flying" &&
                    override.labels["en"] == "Flying" &&
                    override.labels["es"] == "Volar" &&
                    override.labels["de"] == "Fliegend"
                }
            )
        }
    }

    @Test
    fun `given valid row when saveOverride then editingKey is cleared after save`() = runTest {
        // Arrange
        val row = buildRow(key = "flying")
        viewModel.onStartEdit("flying")
        advanceUntilIdle()
        assertEquals("flying", viewModel.state.value.editingKey)

        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert: editingKey reset after save
        assertNull(viewModel.state.value.editingKey)
    }

    @Test
    fun `given valid row when saveOverride then refreshRows is called (loadAndApply invoked)`() = runTest {
        // Arrange
        val row = buildRow()
        coEvery { dictionaryRepo.upsert(any()) } returns Unit

        // Act
        viewModel.saveOverride(row)
        advanceUntilIdle()

        // Assert: loadAndApply called at init + after saveOverride = at least 2 times
        coVerify(atLeast = 2) { dictionaryRepo.loadAndApply() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — resetEntry
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given key when resetEntry then dictionaryRepo_delete is called with that key`() = runTest {
        // Arrange
        coEvery { dictionaryRepo.delete(any()) } returns Unit

        // Act
        viewModel.resetEntry("flying")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { dictionaryRepo.delete("flying") }
    }

    @Test
    fun `given key when resetEntry then refreshRows is called after delete`() = runTest {
        // Arrange
        coEvery { dictionaryRepo.delete(any()) } returns Unit

        // Act
        viewModel.resetEntry("flying")
        advanceUntilIdle()

        // Assert: loadAndApply called at init + after resetEntry
        coVerify(atLeast = 2) { dictionaryRepo.loadAndApply() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — resetAll
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
    fun `when resetAll then refreshRows is called after reset`() = runTest {
        // Arrange
        coEvery { dictionaryRepo.resetAll() } returns Unit

        // Act
        viewModel.resetAll()
        advanceUntilIdle()

        // Assert: loadAndApply called at init + after resetAll
        coVerify(atLeast = 2) { dictionaryRepo.loadAndApply() }
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
        viewModel.onStartEdit("flying")

        // Assert
        assertEquals("flying", viewModel.state.value.editingKey)
    }

    @Test
    fun `given editingKey is set when onDismissEdit then editingKey is null`() = runTest {
        // Arrange
        viewModel.onStartEdit("removal")
        assertEquals("removal", viewModel.state.value.editingKey)

        // Act
        viewModel.onDismissEdit()

        // Assert
        assertNull(viewModel.state.value.editingKey)
    }

    @Test
    fun `given one key being edited when onStartEdit with different key then new key replaces old`() = runTest {
        // Arrange
        viewModel.onStartEdit("flying")
        assertEquals("flying", viewModel.state.value.editingKey)

        // Act
        viewModel.onStartEdit("trample")

        // Assert: key is replaced, not stacked
        assertEquals("trample", viewModel.state.value.editingKey)
    }
}
