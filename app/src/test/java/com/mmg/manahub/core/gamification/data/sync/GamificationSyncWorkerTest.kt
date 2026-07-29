package com.mmg.manahub.core.gamification.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * WS7 Part 2 (backend-performance-optimization-plan.md, 2026-07-29): pins the gamification gate's
 * defense-in-depth worker guard (ADR-005 Decision 1), mirroring [QuestRotationWorkerTest]'s pattern
 * for the sibling worker — see that class's KDoc for why [TestListenableWorkerBuilder] is a plain JVM
 * unit test here (no Robolectric).
 */
class GamificationSyncWorkerTest {

    private val gamificationSyncManager: GamificationSyncManager = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk()

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildWorker(): GamificationSyncWorker =
        TestListenableWorkerBuilder<GamificationSyncWorker>(mockk<Context>(relaxed = true))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = GamificationSyncWorker(
                    appContext,
                    workerParameters,
                    gamificationSyncManager,
                    authRepository,
                    userPreferencesDataStore,
                )
            })
            .build()

    @Test
    fun `doWork returns success and never calls sync when gamification flag is off`() = runBlocking {
        every { userPreferencesDataStore.gamificationEnabledFlow } returns flowOf(false)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { authRepository.getCurrentUser() }
        coVerify(exactly = 0) { gamificationSyncManager.sync(any()) }
    }

    @Test
    fun `doWork returns success without syncing when there is no current user`() = runBlocking {
        every { userPreferencesDataStore.gamificationEnabledFlow } returns flowOf(true)
        coEvery { authRepository.getCurrentUser() } returns null

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { gamificationSyncManager.sync(any()) }
    }

    @Test
    fun `doWork syncs the current user when gamification flag is on`() = runBlocking {
        every { userPreferencesDataStore.gamificationEnabledFlow } returns flowOf(true)
        val user = AuthUser(
            id = "user-123",
            email = null,
            nickname = null,
            gameTag = null,
            avatarUrl = null,
            provider = "email",
        )
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { gamificationSyncManager.sync("user-123") } returns Result.success(Unit)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { gamificationSyncManager.sync("user-123") }
    }
}
