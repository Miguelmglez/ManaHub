package com.mmg.manahub.core.gamification.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.engine.QuestReconciler
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
 * defense-in-depth worker guard (ADR-005 Decision 1) — this is the empirical proof that a stray
 * already-enqueued [QuestRotationWorker] run, firing before [com.mmg.manahub.app.ManaHubApp]'s
 * reactive `cancelUniqueWork` lands, reaches zero Room writes when the flag is off.
 *
 * Uses [TestListenableWorkerBuilder] (`androidx.work:work-testing`, added this task) with a custom
 * [WorkerFactory] to build a real worker instance with mocked collaborators — this is a plain JVM unit
 * test (no Robolectric, no device/emulator): the worker's `doWork()` never touches [Context] beyond
 * what [ListenableWorker] already stores, so a relaxed MockK [Context] is a sufficient stand-in.
 */
class QuestRotationWorkerTest {

    private val questReconciler: QuestReconciler = mockk(relaxed = true)
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk()

    @Before
    fun setUp() {
        // The flag-off self-abort path calls the top-level `recordNonFatal` (core/util/CrashlyticsHelper.kt),
        // which internally reaches FirebaseCrashlytics.getInstance() -- unavailable in a plain JVM unit
        // test without a real/initialized FirebaseApp (feedback_crashlytics_helper_top_level_functions).
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildWorker(): QuestRotationWorker =
        TestListenableWorkerBuilder<QuestRotationWorker>(mockk<Context>(relaxed = true))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = QuestRotationWorker(
                    appContext,
                    workerParameters,
                    questReconciler,
                    userPreferencesDataStore,
                )
            })
            .build()

    @Test
    fun `doWork returns success and never reconciles when gamification flag is off`() = runBlocking {
        every { userPreferencesDataStore.gamificationEnabledFlow } returns flowOf(false)

        val result = buildWorker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { questReconciler.reconcile() }
    }

    @Test
    fun `doWork reconciles when gamification flag is on`() = runBlocking {
        every { userPreferencesDataStore.gamificationEnabledFlow } returns flowOf(true)
        coEvery { questReconciler.reconcile() } returns Unit

        val result = buildWorker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { questReconciler.reconcile() }
    }
}
