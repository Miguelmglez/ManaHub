package com.mmg.manahub.core.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.mmg.manahub.core.util.recordNonFatal
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import javax.inject.Singleton

/**
 * Hilt module that provides WorkManager with a [DelegatingWorkerFactory] combining Koin- and
 * Hilt-built workers.
 *
 * IMPORTANT: When using a custom WorkManager configuration, you must disable
 * WorkManager's automatic initialization in AndroidManifest.xml by adding:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="androidx.work.WorkManagerInitializer"
 *         android:value="androidx.startup"
 *         tools:node="remove" />
 * </provider>
 * ```
 * Or alternatively add to AndroidManifest.xml application node:
 * ```xml
 * <meta-data
 *     android:name="androidx.work.impl.WorkManagerInitializer"
 *     android:value="androidx.startup"
 *     tools:node="remove"/>
 * ```
 *
 * ## KMP migration — Hilt→Koin cutover batch 6
 * All 7 non-excluded `@HiltWorker`s were converted to plain [androidx.work.CoroutineWorker]s resolved
 * by Koin's `worker { }` DSL (registered across `gamificationEngineKoinModule`, `collectionKoinModule`,
 * `core.sync.di.syncKoinModule` and `core.push.di.pushKoinModule`). The excluded scanner feature's
 * `EmbeddingDatabaseUpdateWorker` (`@HiltWorker`) stays on Hilt — it is currently fully commented out
 * (replaced by ML Kit OCR) but the coexistence path below is kept so a future re-instated `@HiltWorker`
 * keeps working without touching this file again.
 *
 * [WorkManager] now dispatches to TWO factories via [DelegatingWorkerFactory]:
 * - [SafeKoinWorkerFactory] (wraps [KoinWorkerFactory] from `koin-androidx-workmanager`) resolves the 7
 *   Koin-registered workers. Its delegate's `createWorker()` is a [org.koin.core.component.KoinComponent]
 *   that calls `getKoin()` — the DEFAULT global Koin instance — lazily, ONLY when WorkManager actually
 *   executes a job. That is normally well after `ManaHubApp.onCreate()`'s `startKoin()` call has returned,
 *   so constructing `KoinWorkerFactory()` HERE — before `startKoin()` runs, since Hilt field injection
 *   (which triggers this `@Provides`, as `ManaHubApp.workManager` is an eager `@Inject lateinit var`)
 *   happens before the `onCreate()` body — is normally safe: only the LATER `createWorker()` call
 *   touches Koin (same ordering argument documented on every other reverse/forward bridge in this
 *   migration, e.g. `KoinToHiltBridgeModule`).
 *
 *   **Real ordering gap (production crash fix, 2026-07-29):** `WorkManager.initialize()` below runs
 *   during Hilt's eager field injection on `ManaHubApp`, which happens BEFORE `onCreate()`'s body calls
 *   `startKoin()`. If a periodic worker (`PriceRefreshWorker`, `CollectionStatsSyncWorker`,
 *   `GamificationSyncWorker`, `QuestRotationWorker`, etc.) has work due at process start, WorkManager's
 *   own internal scheduler/`Processor` can dispatch it synchronously during or shortly after
 *   `initialize()` — i.e. before `startKoin()` has run — which reaches `KoinWorkerFactory.createWorker()`
 *   → `getKoin()` and throws `IllegalStateException: KoinApplication has not been started`, crashing the
 *   process. [SafeKoinWorkerFactory] catches exactly that exception and returns `null` instead, so
 *   [DelegatingWorkerFactory] falls through to the next factory ([hiltWorkerFactory]) rather than
 *   propagating the crash — that one worker run fails gracefully (periodic workers retry next period)
 *   instead of taking down the whole app. Any OTHER exception type still propagates normally so real
 *   bugs in a worker's `doWork()` aren't hidden.
 * - [hiltWorkerFactory] (unchanged) still resolves any live `@HiltWorker`.
 *
 * [DelegatingWorkerFactory.createWorker] tries each added factory in order and returns the first
 * non-null result; both factories return `null` for worker class names they don't recognise, so there
 * is no ambiguity between the two DI graphs.
 *
 * We deliberately do NOT call Koin's own `workManagerFactory()` `KoinApplication` extension (which would
 * otherwise be invoked inside `ManaHubApp.onCreate()`'s `startKoin { }`): that helper self-initializes
 * WorkManager with ONLY a bare `KoinWorkerFactory()` (no Hilt factory), guarded by
 * `WorkManager.isInitialized()`. Since THIS Hilt provider always runs first (before `startKoin()`),
 * WorkManager is already initialized with OUR combined factory by the time `startKoin()` would reach
 * that call — it would be a silent no-op. Skipping it keeps the wiring in exactly one place.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    /**
     * Provides a [WorkManager] configured with a [DelegatingWorkerFactory] that dispatches to both
     * [SafeKoinWorkerFactory] (the 7 migrated workers) and [HiltWorkerFactory] (any live `@HiltWorker`,
     * currently none — see class KDoc).
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
        hiltWorkerFactory: HiltWorkerFactory,
    ): WorkManager {
        val delegatingFactory = DelegatingWorkerFactory().apply {
            addFactory(SafeKoinWorkerFactory())
            addFactory(hiltWorkerFactory)
        }
        val config = Configuration.Builder()
            .setWorkerFactory(delegatingFactory)
            .build()
        WorkManager.initialize(context, config)
        return WorkManager.getInstance(context)
    }
}

/**
 * Wraps [KoinWorkerFactory] to fail safe instead of crashing the process when WorkManager dispatches a
 * job before `startKoin()` has run (see [SyncModule]'s KDoc for the full ordering race this guards
 * against — `WorkManager.initialize()` happens during Hilt's eager field injection, which is BEFORE
 * `ManaHubApp.onCreate()`'s body calls `startKoin()`).
 *
 * Only [IllegalStateException] — the exact type Koin's `GlobalContext.get()` throws for "not started" —
 * is swallowed (logged as a non-fatal, then treated as "this factory doesn't recognise this worker" by
 * returning `null`, letting [DelegatingWorkerFactory] fall through to the next factory). Any other
 * exception type propagates normally so a real bug in [KoinWorkerFactory]'s own resolution logic is
 * never silently hidden.
 */
private class SafeKoinWorkerFactory : WorkerFactory() {
    private val delegate = KoinWorkerFactory()

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = try {
        delegate.createWorker(appContext, workerClassName, workerParameters)
    } catch (e: IllegalStateException) {
        recordNonFatal("worker_factory_koin_not_started", e)
        null
    }
}
