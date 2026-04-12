package com.mmg.manahub.feature.settings

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * Covers:
 * - onAutoRefreshChanged: saves to DataStore and updates state optimistically
 * - setAppLanguage: calls repo and emits on appLanguageChanged SharedFlow
 * - setNewsLanguages with empty set: DataStore NOT called (guard check)
 * - setPreferredCurrency: delegates to repo
 * - selectTheme: delegates to DataStore
 * - preferencesFlow update: _prefsState is updated
 * - themeFlow update: _uiState.currentTheme is updated
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userPrefsDataStore  = mockk<UserPreferencesDataStore>(relaxed = true)
    private val userPreferencesRepo = mockk<UserPreferencesRepository>(relaxed = true)

    // In-memory flow emulators for DataStore-backed properties
    private val autoRefreshFlow  = MutableStateFlow(false)
    private val themeFlow        = MutableStateFlow<AppTheme>(AppTheme.NeonVoid)
    private val preferencesFlow  = MutableStateFlow(defaultPreferences())

    private lateinit var viewModel: SettingsViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultPreferences() = UserPreferences(
        appLanguage       = AppLanguage.ENGLISH,
        cardLanguage      = CardLanguage.ENGLISH,
        newsLanguages     = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = PreferredCurrency.USD,
    )

    private fun buildViewModel(): SettingsViewModel {
        every { userPrefsDataStore.autoRefreshPricesFlow } returns autoRefreshFlow
        every { userPrefsDataStore.themeFlow }             returns themeFlow
        every { userPreferencesRepo.preferencesFlow }      returns preferencesFlow

        coEvery { userPrefsDataStore.saveAutoRefreshPrices(any()) } coAnswers {
            autoRefreshFlow.value = firstArg()
        }
        coEvery { userPrefsDataStore.saveTheme(any()) } coAnswers {
            themeFlow.value = firstArg()
        }

        return SettingsViewModel(
            userPrefsDataStore  = userPrefsDataStore,
            userPreferencesRepo = userPreferencesRepo,
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
    //  GROUP 1 — onAutoRefreshChanged
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given autoRefresh is false when onAutoRefreshChanged true then DataStore is saved and state updates optimistically`() = runTest {
        // Arrange
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.autoRefreshPrices)

        // Act
        viewModel.onAutoRefreshChanged(true)
        advanceUntilIdle()

        // Assert: DataStore write was made
        coVerify { userPrefsDataStore.saveAutoRefreshPrices(true) }
        // Assert: state reflects the new value
        assertTrue(viewModel.uiState.value.autoRefreshPrices)
    }

    @Test
    fun `given autoRefresh is true when onAutoRefreshChanged false then state is updated to false`() = runTest {
        // Arrange
        autoRefreshFlow.value = true
        advanceUntilIdle()

        // Act
        viewModel.onAutoRefreshChanged(false)
        advanceUntilIdle()

        // Assert
        coVerify { userPrefsDataStore.saveAutoRefreshPrices(false) }
        assertFalse(viewModel.uiState.value.autoRefreshPrices)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — setAppLanguage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given English language when setAppLanguage then repo_setAppLanguage is called`() = runTest {
        // Act
        viewModel.setAppLanguage(AppLanguage.ENGLISH)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPreferencesRepo.setAppLanguage(AppLanguage.ENGLISH) }
    }

    @Test
    fun `when setAppLanguage then appLanguageChanged SharedFlow emits`() = runTest {
        // Assert via Turbine
        viewModel.appLanguageChanged.test {
            // Act
            viewModel.setAppLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            // We expect exactly one emission
            awaitItem() // Unit emission
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — setNewsLanguages
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given empty set when setNewsLanguages then repo_setNewsLanguages is NOT called`() = runTest {
        // Act
        viewModel.setNewsLanguages(emptySet())
        advanceUntilIdle()

        // Assert: the guard (if languages.isEmpty() return) prevents the repo call
        coVerify(exactly = 0) { userPreferencesRepo.setNewsLanguages(any()) }
    }

    @Test
    fun `given non-empty set when setNewsLanguages then repo_setNewsLanguages is called`() = runTest {
        // Arrange
        val langs = setOf(NewsLanguage.ENGLISH, NewsLanguage.SPANISH)

        // Act
        viewModel.setNewsLanguages(langs)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPreferencesRepo.setNewsLanguages(langs) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — setPreferredCurrency
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when setPreferredCurrency USD then repo_setPreferredCurrency is called with USD`() = runTest {
        // Act
        viewModel.setPreferredCurrency(PreferredCurrency.USD)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPreferencesRepo.setPreferredCurrency(PreferredCurrency.USD) }
    }

    @Test
    fun `when setPreferredCurrency EUR then repo_setPreferredCurrency is called with EUR`() = runTest {
        // Act
        viewModel.setPreferredCurrency(PreferredCurrency.EUR)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPreferencesRepo.setPreferredCurrency(PreferredCurrency.EUR) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — selectTheme
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when selectTheme MedievalGrimoire then userPrefsDataStore_saveTheme is called`() = runTest {
        // Act
        viewModel.selectTheme(AppTheme.MedievalGrimoire)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPrefsDataStore.saveTheme(AppTheme.MedievalGrimoire) }
    }

    @Test
    fun `when selectTheme ArcaneCosmos then userPrefsDataStore_saveTheme is called`() = runTest {
        // Act
        viewModel.selectTheme(AppTheme.ArcaneCosmos)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPrefsDataStore.saveTheme(AppTheme.ArcaneCosmos) }
    }

    @Test
    fun `when selectTheme NeonVoid then userPrefsDataStore_saveTheme is called`() = runTest {
        // Act
        viewModel.selectTheme(AppTheme.NeonVoid)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userPrefsDataStore.saveTheme(AppTheme.NeonVoid) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — preferencesFlow → _prefsState
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given preferencesFlow emits new prefs when collected then _prefsState is updated`() = runTest {
        // Arrange: initial state uses defaultPreferences (USD)
        advanceUntilIdle()
        assertEquals(PreferredCurrency.USD, viewModel.prefsState.value.userPreferences.preferredCurrency)

        // Act: emit updated preferences
        val updatedPrefs = defaultPreferences().copy(preferredCurrency = PreferredCurrency.EUR)
        preferencesFlow.value = updatedPrefs
        advanceUntilIdle()

        // Assert: _prefsState reflects the update
        assertEquals(PreferredCurrency.EUR, viewModel.prefsState.value.userPreferences.preferredCurrency)
    }

    @Test
    fun `given preferencesFlow emits Spanish card language when collected then _prefsState reflects it`() = runTest {
        // Act
        val updatedPrefs = defaultPreferences().copy(cardLanguage = CardLanguage.SPANISH)
        preferencesFlow.value = updatedPrefs
        advanceUntilIdle()

        // Assert
        assertEquals(CardLanguage.SPANISH, viewModel.prefsState.value.userPreferences.cardLanguage)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — themeFlow → _uiState.currentTheme
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given initial NeonVoid theme when ViewModel initializes then currentTheme is NeonVoid`() = runTest {
        // Arrange: themeFlow starts as NeonVoid
        advanceUntilIdle()

        // Assert
        assertEquals(AppTheme.NeonVoid, viewModel.uiState.value.currentTheme)
    }

    @Test
    fun `given themeFlow emits MedievalGrimoire when collected then currentTheme is updated`() = runTest {
        // Arrange
        advanceUntilIdle()

        // Act: simulate DataStore emitting a new theme
        themeFlow.value = AppTheme.MedievalGrimoire
        advanceUntilIdle()

        // Assert
        assertEquals(AppTheme.MedievalGrimoire, viewModel.uiState.value.currentTheme)
    }

    @Test
    fun `given themeFlow emits ArcaneCosmos when collected then currentTheme is updated`() = runTest {
        // Act
        themeFlow.value = AppTheme.ArcaneCosmos
        advanceUntilIdle()

        // Assert
        assertEquals(AppTheme.ArcaneCosmos, viewModel.uiState.value.currentTheme)
    }
}
