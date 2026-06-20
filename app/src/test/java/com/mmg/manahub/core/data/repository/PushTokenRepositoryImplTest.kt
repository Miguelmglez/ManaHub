package com.mmg.manahub.core.data.repository

import androidx.work.WorkManager
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserPreferences
import com.mmg.manahub.core.push.RegisterPushTokenWorker
import com.mmg.manahub.core.push.UnregisterPushTokenWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushTokenRepositoryImpl].
 *
 * FirebaseMessaging is not mocked here because [updateLocale] calls
 * `FirebaseMessaging.getInstance().token.await()`, which touches Android internals.
 * That method is tested separately in an instrumented test. For [register] and
 * [unregister] the token is already provided by the caller (FCM service), so no
 * Firebase interaction is needed.
 *
 * Covers:
 *  - GROUP 1: register — success and retry-worker enqueue on failure
 *  - GROUP 2: unregister — success and retry-worker enqueue on failure
 *  - GROUP 3: unregisterAll — swallows any exception without rethrowing
 *  - GROUP 4: locale forwarding — correct locale code passed to upsert
 */
class PushTokenRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val dataSource = mockk<PushTokenRemoteDataSource>(relaxed = true)
    private val userPreferencesDataStore = mockk<UserPreferencesDataStore>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    private lateinit var repository: PushTokenRepositoryImpl

    // ── Constants ─────────────────────────────────────────────────────────────

    private val TOKEN = "fcm-device-token-abc123"
    private val LOCALE_EN = AppLanguage.ENGLISH.code  // "en-GB"

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPreferences(appLanguage: AppLanguage = AppLanguage.ENGLISH) =
        UserPreferences(
            appLanguage = appLanguage,
            cardLanguage = CardLanguage.ENGLISH,
            newsLanguages = setOf(NewsLanguage.ENGLISH),
            preferredCurrency = PreferredCurrency.USD,
            collectionViewMode = CollectionViewMode.GRID,
        )

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        mockkObject(RegisterPushTokenWorker)
        mockkObject(UnregisterPushTokenWorker)

        // Static companion object functions must be explicitly stubbed
        every { RegisterPushTokenWorker.enqueue(any(), any(), any()) } just runs
        every { UnregisterPushTokenWorker.enqueue(any(), any()) } just runs

        // Default: preferences emit English locale
        every { userPreferencesDataStore.preferencesFlow } returns flowOf(buildPreferences())

        repository = PushTokenRepositoryImpl(
            dataSource = dataSource,
            userPreferencesDataStore = userPreferencesDataStore,
            workManager = workManager,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(RegisterPushTokenWorker)
        unmockkObject(UnregisterPushTokenWorker)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — register
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given upsert succeeds when register then dataSource upsert is called with token and locale`() = runTest {
        // Arrange
        coEvery { dataSource.upsert(TOKEN, LOCALE_EN) } returns Unit

        // Act
        repository.register(TOKEN)

        // Assert
        coVerify(exactly = 1) { dataSource.upsert(TOKEN, LOCALE_EN) }
    }

    @Test
    fun `given upsert succeeds when register then RegisterPushTokenWorker is never enqueued`() = runTest {
        // Arrange
        coEvery { dataSource.upsert(TOKEN, LOCALE_EN) } returns Unit

        // Act
        repository.register(TOKEN)

        // Assert: no worker must be scheduled on success
        verify_RegisterWorker_never_enqueued()
    }

    @Test
    fun `given upsert throws when register then RegisterPushTokenWorker is enqueued`() = runTest {
        // Arrange
        coEvery { dataSource.upsert(any(), any()) } throws RuntimeException("network error")

        // Act — must NOT rethrow
        repository.register(TOKEN)

        // Assert
        verify { RegisterPushTokenWorker.enqueue(workManager, TOKEN, LOCALE_EN) }
    }

    @Test
    fun `given upsert throws when register then exception is swallowed and not propagated`() = runTest {
        // Arrange
        coEvery { dataSource.upsert(any(), any()) } throws RuntimeException("timeout")

        // Act + Assert — runTest would fail if an exception escaped
        repository.register(TOKEN)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — unregister
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given delete succeeds when unregister then dataSource delete is called with token`() = runTest {
        // Arrange
        coEvery { dataSource.delete(TOKEN) } returns Unit

        // Act
        repository.unregister(TOKEN)

        // Assert
        coVerify(exactly = 1) { dataSource.delete(TOKEN) }
    }

    @Test
    fun `given delete succeeds when unregister then UnregisterPushTokenWorker is never enqueued`() = runTest {
        // Arrange
        coEvery { dataSource.delete(TOKEN) } returns Unit

        // Act
        repository.unregister(TOKEN)

        // Assert
        verify_UnregisterWorker_never_enqueued()
    }

    @Test
    fun `given delete throws when unregister then UnregisterPushTokenWorker is enqueued`() = runTest {
        // Arrange
        coEvery { dataSource.delete(any()) } throws RuntimeException("delete failed")

        // Act — must NOT rethrow
        repository.unregister(TOKEN)

        // Assert
        verify { UnregisterPushTokenWorker.enqueue(workManager, TOKEN) }
    }

    @Test
    fun `given delete throws when unregister then exception is swallowed`() = runTest {
        // Arrange
        coEvery { dataSource.delete(any()) } throws IllegalStateException("auth revoked")

        // Act + Assert — no exception must escape
        repository.unregister(TOKEN)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — unregisterAll
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given deleteAll succeeds when unregisterAll then dataSource deleteAll is called`() = runTest {
        // Arrange
        coEvery { dataSource.deleteAll() } returns Unit

        // Act
        repository.unregisterAll()

        // Assert
        coVerify(exactly = 1) { dataSource.deleteAll() }
    }

    @Test
    fun `given deleteAll throws when unregisterAll then exception is not propagated`() = runTest {
        // Arrange — account deletion is best-effort; failures must be silently swallowed
        coEvery { dataSource.deleteAll() } throws RuntimeException("server error")

        // Act + Assert — runTest fails if an exception escapes the suspend function
        repository.unregisterAll()
    }

    @Test
    fun `given deleteAll throws when unregisterAll then no worker is enqueued`() = runTest {
        // Arrange
        coEvery { dataSource.deleteAll() } throws RuntimeException("server error")

        // Act
        repository.unregisterAll()

        // Assert: unregisterAll is best-effort — there is no retry worker for it
        verify_RegisterWorker_never_enqueued()
        verify_UnregisterWorker_never_enqueued()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — locale forwarding
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given English language preference when register then upsert receives English locale code`() = runTest {
        // Arrange
        every { userPreferencesDataStore.preferencesFlow } returns
            flowOf(buildPreferences(appLanguage = AppLanguage.ENGLISH))
        coEvery { dataSource.upsert(any(), any()) } returns Unit

        // Act
        repository.register(TOKEN)

        // Assert: locale code comes from AppLanguage.ENGLISH.code ("en-GB")
        coVerify { dataSource.upsert(TOKEN, AppLanguage.ENGLISH.code) }
    }

    @Test
    fun `given register is called then locale is read from preferencesFlow first value`() = runTest {
        // Arrange
        val capturedLocale = mutableListOf<String>()
        coEvery { dataSource.upsert(any(), capture(capturedLocale)) } returns Unit

        // Act
        repository.register(TOKEN)

        // Assert
        assert(capturedLocale.size == 1) { "upsert must be called exactly once" }
        assert(capturedLocale[0] == AppLanguage.ENGLISH.code) {
            "Expected ${AppLanguage.ENGLISH.code}, got ${capturedLocale[0]}"
        }
    }

    // ── Private assertion helpers ─────────────────────────────────────────────

    private fun verify_RegisterWorker_never_enqueued() {
        io.mockk.verify(exactly = 0) { RegisterPushTokenWorker.enqueue(any(), any(), any()) }
    }

    private fun verify_UnregisterWorker_never_enqueued() {
        io.mockk.verify(exactly = 0) { UnregisterPushTokenWorker.enqueue(any(), any()) }
    }
}
