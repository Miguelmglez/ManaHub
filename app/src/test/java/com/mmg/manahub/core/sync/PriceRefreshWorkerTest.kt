package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * WS7 Part 2 (backend-performance-optimization-plan.md, 2026-07-29): pins ADR-005 Decision 3's core
 * invariant — the 23h daily watermark is claimed ONLY on a full [RefreshCollectionPricesUseCase.Result.Success]
 * pass, NEVER on a [RefreshCollectionPricesUseCase.Result.Capped] one — and that a [PriceRefreshWorker.WORK_NAME_FOLLOW_UP]
 * one-time follow-up is enqueued ONLY on `Capped`, never on `Success`. Uses [TestListenableWorkerBuilder]
 * (plain JVM unit test, see [com.mmg.manahub.core.gamification.data.sync.QuestRotationWorkerTest]'s KDoc
 * for why no Robolectric is needed) plus `mockkStatic(WorkManager::class)` to intercept the worker's
 * static `WorkManager.getInstance(applicationContext)` call in the Capped branch, entirely sidestepping
 * the need for `WorkManagerTestInitHelper` (which DOES require a real/Robolectric Context to build its
 * in-memory Room-backed WorkDatabase — genuinely disproportionate for this one assertion).
 */
class PriceRefreshWorkerTest {

    private val refreshPricesUseCase: RefreshCollectionPricesUseCase = mockk()
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    // A single shared Context instance for both the worker builder AND the static-mock match below.
    // Matching this EXACT reference (rather than `any()`) avoids MockK's auto-hinting machinery, which
    // otherwise synthesizes its own throwaway Context proxy to resolve the matcher and ends up invoking
    // real WorkManagerImpl.getInstance() internals against it -- an incompletely-abstract proxy that
    // throws AbstractMethodError on getApplicationContext().
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        // `WorkManager.getInstance(context)` called FROM KOTLIN SOURCE (both PriceRefreshWorker's own
        // production code and this test) compiles to a direct call on the `WorkManager.Companion`
        // singleton object, NOT the `@JvmStatic` bridge method mockkStatic(WorkManager::class) would
        // intercept (that bridge only serves Java callers) -- mockkObject on the Companion is the
        // correct MockK entry point here.
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager

        // Stale watermark (epoch 0) so the 23h freshness guard never short-circuits doWork() before
        // refreshPricesUseCase is even invoked.
        every { userPreferencesDataStore.lastPriceRefreshFlow } returns flowOf(0L)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
        unmockkObject(WorkManager.Companion)
    }

    private fun buildWorker(): PriceRefreshWorker =
        TestListenableWorkerBuilder<PriceRefreshWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = PriceRefreshWorker(
                    appContext,
                    workerParameters,
                    refreshPricesUseCase,
                    userPreferencesDataStore,
                )
            })
            .build()

    @Test
    fun `Success claims the watermark and never enqueues a follow-up`() = runBlocking {
        coEvery { refreshPricesUseCase.invoke() } returns flowOf(
            RefreshCollectionPricesUseCase.Result.Success(updatedCount = 12, notFoundCount = 1, durationMs = 500L),
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { userPreferencesDataStore.saveLastPriceRefresh(any()) }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                PriceRefreshWorker.WORK_NAME_FOLLOW_UP,
                any(),
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `Capped never claims the watermark and enqueues a follow-up`() = runBlocking {
        coEvery { refreshPricesUseCase.invoke() } returns flowOf(
            RefreshCollectionPricesUseCase.Result.Capped(updatedCount = 5, notFoundCount = 0, remainingStaleCount = 40),
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { userPreferencesDataStore.saveLastPriceRefresh(any()) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                PriceRefreshWorker.WORK_NAME_FOLLOW_UP,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
