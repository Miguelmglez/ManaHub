package com.mmg.manahub.feature.settings.presentation

import kotlinx.coroutines.flow.flowOf
import com.mmg.manahub.core.domain.config.DefaultsOnlyRemoteConfigRepository
import com.mmg.manahub.core.gamification.domain.DefaultGamificationAvailability
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.io.IOException

/**
 * Unit tests for [SettingsViewModel].
 *
 * GROUP 1 — notification group toggle: backend failure surfaces a toast, never a crash
 * GROUP 2 — privacy toggles: serialised per flag; failures converge on the server's value
 * GROUP 3 — privacy/notification writes need a real signed-in account
 * GROUP 4 — DataStore write failures are recorded, never thrown
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val userPrefsDataStore          = mockk<UserPreferencesDataStore>(relaxed = true)
    private val userPreferencesRepo         = mockk<UserPreferencesRepository>(relaxed = true)
    private val analyticsHelper             = mockk<AnalyticsHelper>(relaxed = true)
    private val authRepository              = mockk<AuthRepository>(relaxed = true)
    private val userProfileDataSource       = mockk<UserProfileDataSource>(relaxed = true)
    private val pushTokenRepository         = mockk<PushTokenRepository>(relaxed = true)
    private val notificationPrefsRepository = mockk<NotificationPrefsRepository>(relaxed = true)
    private val voiceModelRepository        = mockk<VoiceModelRepository>(relaxed = true)
    private val crashlytics                 = mockk<FirebaseCrashlytics>(relaxed = true)

    private val collectionPublicFlow = MutableStateFlow(true)
    private val wishlistPublicFlow   = MutableStateFlow(true)
    private val tradeListPublicFlow  = MutableStateFlow(true)
    private val sessionState         = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    private companion object {
        const val USER_ID = "user-1"
    }

    private fun user(isAnonymous: Boolean = false) = AuthUser(
        id = USER_ID, email = "x@example.com", nickname = "Mage", gameTag = "MAGE#0001",
        avatarUrl = null, provider = "email", isAnonymous = isAnonymous,
    )

    private fun profileDto(collectionPublic: Boolean? = null) = UserProfileDto(
        id = USER_ID, nickname = "Mage", collectionPublic = collectionPublic,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        every { userPrefsDataStore.themeFlow } returns MutableStateFlow(AppTheme.ArcaneCosmos)
        every { userPrefsDataStore.collectionPublicFlow } returns collectionPublicFlow
        every { userPrefsDataStore.wishlistPublicFlow } returns wishlistPublicFlow
        every { userPrefsDataStore.tradeListPublicFlow } returns tradeListPublicFlow
        every { userPrefsDataStore.pushNotificationsEnabledFlow } returns MutableStateFlow(true)
        every { userPrefsDataStore.gamificationEnabledFlow } returns MutableStateFlow(true)
        coEvery { userPrefsDataStore.saveCollectionPublic(any()) } answers { collectionPublicFlow.value = firstArg() }
        coEvery { userPrefsDataStore.saveWishlistPublic(any()) } answers { wishlistPublicFlow.value = firstArg() }
        coEvery { userPrefsDataStore.saveTradeListPublic(any()) } answers { tradeListPublicFlow.value = firstArg() }
        every { userPreferencesRepo.preferencesFlow } returns emptyFlow()
        every { notificationPrefsRepository.prefsFlow } returns MutableStateFlow(emptyMap())
        coEvery { notificationPrefsRepository.refresh() } returns Result.success(Unit)
        every { authRepository.sessionState } returns sessionState
        coEvery { authRepository.getCurrentUser() } returns user()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildViewModel() = SettingsViewModel(
        userPrefsDataStore          = userPrefsDataStore,
        userPreferencesRepo         = userPreferencesRepo,
        analyticsHelper             = analyticsHelper,
        authRepository              = authRepository,
        userProfileDataSource       = userProfileDataSource,
        pushTokenRepository         = pushTokenRepository,
        notificationPrefsRepository = notificationPrefsRepository,
        voiceModelRepository        = voiceModelRepository,
        gamificationAvailability    = DefaultGamificationAvailability(
            remoteConfigRepository = DefaultsOnlyRemoteConfigRepository(),
            userOptInFlow = flowOf(true),
            compileEnabled = true,
        ),
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — notification group toggle
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given backend offline when group toggle then toast is shown and nothing crashes`() = runTest {
        coEvery { notificationPrefsRepository.setEventsEnabled(any(), any()) } returns
            Result.failure(IOException("offline"))
        val vm = buildViewModel()

        vm.setNotificationGroupEnabled(listOf("trade_proposed", "trade_countered"), enabled = false)

        assertEquals(SettingsToast.NOTIFICATION_SAVE_FAILED, vm.uiState.value.toastMessage)
        assertTrue(vm.uiState.value.toastIsError)
        coVerify(exactly = 1) {
            notificationPrefsRepository.setEventsEnabled(listOf("trade_proposed", "trade_countered"), false)
        }
    }

    @Test
    fun `given group toggle succeeds then it is written as ONE grouped call and no toast is shown`() = runTest {
        coEvery { notificationPrefsRepository.setEventsEnabled(any(), any()) } returns Result.success(Unit)
        val vm = buildViewModel()

        vm.setNotificationGroupEnabled(listOf("friend_request", "friend_accepted"), enabled = true)

        assertNull(vm.uiState.value.toastMessage)
        coVerify(exactly = 1) { notificationPrefsRepository.setEventsEnabled(any(), true) }
        coVerify(exactly = 0) { notificationPrefsRepository.setEventEnabled(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — privacy toggles
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given opposite rapid toggles both failing then local value equals the server value`() = runTest {
        // Arrange — the server keeps collection_public = false; both PATCHes fail
        collectionPublicFlow.value = false
        coEvery { userProfileDataSource.updatePrivacySettings(any(), any(), any(), any()) } returns
            Result.failure(IOException("offline"))
        coEvery { userProfileDataSource.fetchUserProfile(USER_ID) } returns profileDto(collectionPublic = false)
        val vm = buildViewModel()

        // Act — the user flips ON then OFF before either write settles
        vm.setCollectionPublic(true)
        vm.setCollectionPublic(false)

        // Assert — local converged on the server truth, not on a pre-tap snapshot
        assertFalse(collectionPublicFlow.value)
        assertFalse(vm.uiState.value.collectionPublic)
        assertEquals(SettingsToast.PRIVACY_SAVE_FAILED, vm.uiState.value.toastMessage)
        assertTrue(vm.uiState.value.pendingPrivacyKeys.isEmpty())
    }

    @Test
    fun `given PATCH fails and the profile re-read fails too then the pre-tap value is restored`() = runTest {
        collectionPublicFlow.value = true
        coEvery { userProfileDataSource.updatePrivacySettings(any(), any(), any(), any()) } returns
            Result.failure(IOException("offline"))
        coEvery { userProfileDataSource.fetchUserProfile(USER_ID) } returns null
        val vm = buildViewModel()

        vm.setCollectionPublic(false)

        assertTrue(collectionPublicFlow.value)
        assertEquals(SettingsToast.PRIVACY_SAVE_FAILED, vm.uiState.value.toastMessage)
    }

    @Test
    fun `given PATCH succeeds then the optimistic value stays and no toast is shown`() = runTest {
        coEvery { userProfileDataSource.updatePrivacySettings(any(), any(), any(), any()) } returns
            Result.success(Unit)
        val vm = buildViewModel()

        vm.setWishlistPublic(false)

        assertFalse(wishlistPublicFlow.value)
        assertNull(vm.uiState.value.toastMessage)
        assertTrue(vm.uiState.value.pendingPrivacyKeys.isEmpty())
        coVerify(exactly = 1) {
            userProfileDataSource.updatePrivacySettings(USER_ID, null, false, null)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — authentication gate
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no signed-in user when privacy toggle then error toast and no write`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns null
        val vm = buildViewModel()

        vm.setTradeListPublic(false)

        assertEquals(SettingsToast.SIGN_IN_REQUIRED, vm.uiState.value.toastMessage)
        assertTrue(tradeListPublicFlow.value)
        coVerify(exactly = 0) { userProfileDataSource.updatePrivacySettings(any(), any(), any(), any()) }
        coVerify(exactly = 0) { userPrefsDataStore.saveTradeListPublic(any()) }
    }

    @Test
    fun `given anonymous session when privacy toggle then error toast and no write`() = runTest {
        // An anonymous user has no user_profiles row: the PATCH would match nothing and fake success
        coEvery { authRepository.getCurrentUser() } returns user(isAnonymous = true)
        val vm = buildViewModel()

        vm.setCollectionPublic(false)

        assertEquals(SettingsToast.SIGN_IN_REQUIRED, vm.uiState.value.toastMessage)
        coVerify(exactly = 0) { userProfileDataSource.updatePrivacySettings(any(), any(), any(), any()) }
    }

    @Test
    fun `given anonymous session when notification group toggle then error toast and no write`() = runTest {
        coEvery { authRepository.getCurrentUser() } returns user(isAnonymous = true)
        val vm = buildViewModel()

        vm.setNotificationGroupEnabled(listOf("trade_proposed"), enabled = false)

        assertEquals(SettingsToast.SIGN_IN_REQUIRED, vm.uiState.value.toastMessage)
        coVerify(exactly = 0) { notificationPrefsRepository.setEventsEnabled(any(), any()) }
    }

    @Test
    fun `given signed-in user changes then notification prefs are refreshed for the new account`() = runTest {
        buildViewModel()
        coVerify(exactly = 1) { notificationPrefsRepository.refresh() }   // initial (unauthenticated) state

        sessionState.value = SessionState.Authenticated(user())

        coVerify(exactly = 2) { notificationPrefsRepository.refresh() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — DataStore write failures
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given DataStore edit throws IOException when master push toggle then it is recorded and nothing crashes`() = runTest {
        coEvery { userPrefsDataStore.savePushNotificationsEnabled(any()) } throws IOException("disk full")
        val vm = buildViewModel()

        vm.setPushNotificationsEnabled(true)

        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given a theme is selected then the analytics event carries its stable persist key`() = runTest {
        // toString() on the sealed object is renamed by R8, so dashboards would break per build
        val vm = buildViewModel()

        vm.selectTheme(AppTheme.HallowedPrint)

        verify(exactly = 1) {
            analyticsHelper.logEvent("theme_selected", mapOf("theme" to "HALLOWED_PRINT"))
        }
        coVerify(exactly = 1) { userPrefsDataStore.saveTheme(AppTheme.HallowedPrint) }
    }

    @Test
    fun `given push notifications are disabled then this device's token is unregistered`() = runTest {
        // ADR-005: the master switch must stop the BACKEND sending, not only hide the notification
        val vm = buildViewModel()

        vm.setPushNotificationsEnabled(false)

        coVerify(exactly = 1) { userPrefsDataStore.savePushNotificationsEnabled(false) }
        coVerify(exactly = 1) { pushTokenRepository.unregisterCurrentDevice() }
        coVerify(exactly = 0) { pushTokenRepository.registerCurrentDevice() }
    }

    @Test
    fun `given push notifications are re-enabled then this device's token is registered again`() = runTest {
        val vm = buildViewModel()

        vm.setPushNotificationsEnabled(true)

        coVerify(exactly = 1) { pushTokenRepository.registerCurrentDevice() }
    }

    @Test
    fun `given the token call fails when toggling push then the local preference still persists`() = runTest {
        coEvery { pushTokenRepository.unregisterCurrentDevice() } throws IOException("offline")
        val vm = buildViewModel()

        vm.setPushNotificationsEnabled(false)

        coVerify(exactly = 1) { userPrefsDataStore.savePushNotificationsEnabled(false) }
        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given DataStore edit throws IOException when theme selected then it is recorded and nothing crashes`() = runTest {
        coEvery { userPrefsDataStore.saveTheme(any()) } throws IOException("disk full")
        val vm = buildViewModel()

        vm.selectTheme(AppTheme.ArcaneCosmos)

        verify(exactly = 1) { crashlytics.recordException(any()) }
    }
}
