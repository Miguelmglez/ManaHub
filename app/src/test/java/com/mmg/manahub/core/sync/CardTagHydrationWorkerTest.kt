package com.mmg.manahub.core.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.card.HydrateCollectionStrategyTagsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [CardTagHydrationWorker] (bulk strategy-tag hydration, 2026-09-07): it must no-op
 * when there is nothing to hydrate, defer instead of competing with an in-flight collection sync,
 * and chain another pass via [ListenableWorker.Result.retry] only while the use case reports
 * provable progress — never by re-enqueueing its own unique name (which
 * [ExistingWorkPolicy.KEEP] would drop while that same work is still RUNNING).
 */
class CardTagHydrationWorkerTest {

    private val hydrate: HydrateCollectionStrategyTagsUseCase = mockk()
    private val syncManager: SyncManager = mockk()
    private val crashReporter: CrashReporter = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private fun buildWorker(): CardTagHydrationWorker =
        TestListenableWorkerBuilder<CardTagHydrationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = CardTagHydrationWorker(
                    appContext,
                    workerParameters,
                    hydrate,
                    syncManager,
                    crashReporter,
                )
            })
            .build()

    private fun syncState(state: SyncState) {
        every { syncManager.syncState } returns MutableStateFlow(state)
    }

    private fun hydrationResult(
        candidateCount: Int = 0,
        precomputedCount: Int = 0,
        repairedCount: Int = 0,
        onDeviceCount: Int = 0,
        hasMoreWork: Boolean = false,
    ) = HydrateCollectionStrategyTagsUseCase.Result(
        candidateCount,
        precomputedCount,
        repairedCount,
        onDeviceCount,
        hasMoreWork,
    )

    @Test
    fun `given a sync is in progress then the pass is deferred without touching the hydration use case`() = runBlocking {
        syncState(SyncState.SYNCING)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { hydrate(any(), any(), any()) }
    }

    @Test
    fun `given no candidates then the run succeeds without asking for a follow-up`() = runBlocking {
        syncState(SyncState.IDLE)
        coEvery { hydrate(any(), any(), any()) } returns hydrationResult(candidateCount = 0)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Self-enqueue would be the bug this worker deliberately avoids -- the chain is retry-based.
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `given the use case reports more work then the pass chains via retry`() = runBlocking {
        syncState(SyncState.IDLE)
        coEvery { hydrate(any(), any(), any()) } returns
            hydrationResult(candidateCount = 1000, precomputedCount = 1000, hasMoreWork = true)

        assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
    }

    @Test
    fun `given a completed pass with no more work then the run succeeds`() = runBlocking {
        syncState(SyncState.IDLE)
        coEvery { hydrate(any(), any(), any()) } returns
            hydrationResult(candidateCount = 40, precomputedCount = 40, hasMoreWork = false)

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
    }

    @Test
    fun `given the hydration pass throws then the failure is recorded and the run retries`() = runBlocking {
        syncState(SyncState.IDLE)
        coEvery { hydrate(any(), any(), any()) } throws IllegalStateException("boom")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(exactly = 1) { crashReporter.recordException(any()) }
    }

    @Test
    fun `enqueueImmediate uses its OWN unique name, never CardHydrationWorker's`() {
        CardTagHydrationWorker.enqueueImmediate(workManager)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                eq(CardTagHydrationWorker.WORK_NAME_ONE_TIME),
                eq(ExistingWorkPolicy.KEEP),
                any<OneTimeWorkRequest>(),
            )
        }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                eq(CardHydrationWorker.WORK_NAME_ONE_TIME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
