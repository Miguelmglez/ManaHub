package com.mmg.manahub.core.sync
// COMMENTS_REVIEWED: 2026-09-06

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Write-path hardening audit (2026-09-06), CRITICAL 1: [CollectionSyncWorker.enqueueFirstLoginSync]
 * used to share [CollectionSyncWorker.WORK_NAME_ONE_TIME] with [CollectionSyncWorker.enqueueOneTimeSync],
 * so [androidx.work.ExistingWorkPolicy.KEEP] could silently drop a first-login migration enqueue
 * whenever a plain sync was already pending -- the guest-row migration then never ran. Pins the fix:
 * a dedicated [CollectionSyncWorker.WORK_NAME_FIRST_LOGIN] name, with the plain one-time work
 * cancelled first so a not-yet-migrated guest row can never race a plain sync's push.
 */
class CollectionSyncWorkerTest {

    private val syncManager: SyncManager = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val workManager: WorkManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val user = AuthUser(
        id = "user-123",
        email = null,
        nickname = null,
        gameTag = null,
        avatarUrl = null,
        provider = "email",
    )

    @Before
    fun setUp() {
        // CollectionSyncWorker.doWork() calls WorkManager.getInstance(applicationContext) on a
        // successful cycle (to kick CardHydrationWorker) -- see PriceRefreshWorkerTest's KDoc for
        // why mockkObject(WorkManager.Companion) against the SAME context reference is required
        // here rather than mockkStatic(WorkManager::class).
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    private fun buildWorker(inputIsFirstLogin: Boolean? = null): CollectionSyncWorker {
        val builder = TestListenableWorkerBuilder<CollectionSyncWorker>(context)
        inputIsFirstLogin?.let {
            builder.setInputData(
                androidx.work.Data.Builder()
                    .putBoolean(CollectionSyncWorker.INPUT_KEY_IS_FIRST_LOGIN, it)
                    .build()
            )
        }
        return builder
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = CollectionSyncWorker(
                    appContext,
                    workerParameters,
                    syncManager,
                    authRepository,
                )
            })
            .build()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Companion enqueue functions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `enqueueFirstLoginSync cancels the plain one-time work before enqueueing under its own name`() {
        CollectionSyncWorker.enqueueFirstLoginSync(workManager)

        verify(exactly = 1) { workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                eq(CollectionSyncWorker.WORK_NAME_FIRST_LOGIN),
                eq(ExistingWorkPolicy.KEEP),
                any<OneTimeWorkRequest>(),
            )
        }
        // Never enqueued under the plain one-time name -- the whole point of the fix.
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                eq(CollectionSyncWorker.WORK_NAME_ONE_TIME),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `enqueueOneTimeSync enqueues under the plain one-time name and never cancels anything`() {
        CollectionSyncWorker.enqueueOneTimeSync(workManager)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                eq(CollectionSyncWorker.WORK_NAME_ONE_TIME),
                eq(ExistingWorkPolicy.KEEP),
                any<OneTimeWorkRequest>(),
            )
        }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  doWork dispatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `doWork with the first-login input flag calls assignUserIdAndSync, never sync`() = runBlocking {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { syncManager.assignUserIdAndSync(user.id) } returns SyncResult(state = SyncState.SUCCESS)

        val result = buildWorker(inputIsFirstLogin = true).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncManager.assignUserIdAndSync(user.id) }
        coVerify(exactly = 0) { syncManager.sync(any()) }
    }

    @Test
    fun `doWork without the first-login input flag calls sync, never assignUserIdAndSync`() = runBlocking {
        coEvery { authRepository.getCurrentUser() } returns user
        coEvery { syncManager.sync(user.id) } returns SyncResult(state = SyncState.SUCCESS)

        val result = buildWorker(inputIsFirstLogin = null).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncManager.sync(user.id) }
        coVerify(exactly = 0) { syncManager.assignUserIdAndSync(any()) }
    }
}
