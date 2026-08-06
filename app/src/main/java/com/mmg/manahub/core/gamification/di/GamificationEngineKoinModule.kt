package com.mmg.manahub.core.gamification.di

import com.mmg.manahub.core.data.local.SyncPreferencesStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.SupabaseGamificationDataSource
import com.mmg.manahub.core.gamification.data.repository.GamificationRepositoryImpl
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncWorker
import com.mmg.manahub.core.gamification.data.sync.QuestRotationWorker
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.QuestStableIdProvider
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.core.gamification.engine.AchievementBackfill
import com.mmg.manahub.core.gamification.engine.AchievementEvaluator
import com.mmg.manahub.core.gamification.engine.EntitlementGranter
import com.mmg.manahub.core.gamification.engine.GamificationEngineImpl
import com.mmg.manahub.core.gamification.engine.QuestEvaluator
import com.mmg.manahub.core.gamification.engine.QuestReconciler
import com.mmg.manahub.core.gamification.engine.StreakTracker
import com.mmg.manahub.core.gamification.engine.XpGranter
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 4. The gamification engine graph (ADR-002) is now natively
 * Koin-built here, replacing the deleted Hilt `com.mmg.manahub.core.gamification.di.GamificationModule`
 * plus five previously Hilt-`@Inject`-constructed classes ([AchievementBackfill], [EntitlementGranter],
 * [QuestReconciler], [GamificationSyncManager] — all four lost `@Inject`/`@Singleton` this batch).
 *
 * This is the single cross-cutting home for the whole engine graph (mirrors `sharedDomainKoinModule`'s
 * role for the shared use cases): it is consumed by MULTIPLE Koin islands (Profile, Home, Game,
 * GamificationCelebration) plus `ManaHubApp` itself, so registering it once here — rather than
 * per-island — avoids `DefinitionOverrideException`. Every consumer resolves via `get()`, cross-module,
 * exactly like the pre-existing `TournamentDao`/`TournamentRepository` split.
 *
 * ## What moved here (previously Hilt `@Binds`/`@Provides`/`@Inject constructor`)
 * - [GamificationEngine] ← [GamificationEngineImpl], [GamificationRepository] ← [GamificationRepositoryImpl],
 *   [GamificationRemoteDataSource] ← [SupabaseGamificationDataSource] — the three `@Binds` interfaces.
 * - The system [Clock]/[TimeZone] — previously trivial `@Provides` singletons; constructed directly.
 * - [ClaimQuestRewardUseCase] — previously a `@Provides` factory; now a plain native single.
 * - [XpGranter], [AchievementEvaluator], [QuestEvaluator], [StreakTracker], [EntitlementGranter],
 *   [QuestStableIdProvider] — previously bare `@Inject constructor` classes satisfied implicitly by the
 *   Hilt graph; now explicit Koin singles.
 * - [AchievementBackfill], [QuestReconciler], [GamificationSyncManager] — same as above. `AchievementBackfill`
 *   has a surviving Hilt-only consumer (`ManaHubApp`, still bridged via `by inject()`); `QuestReconciler`/
 *   `GamificationSyncManager`'s former `@HiltWorker` consumers were converted to Koin `worker { }`
 *   registrations in KMP migration batch 6 (see below) — no reverse bridge remains for either.
 *
 * ## [com.mmg.manahub.core.gamification.domain.ProgressionEventBus] is NOT registered here
 * It is registered as a native `single { ProgressionEventBus() }` in `coreBridgeKoinModule` (that is
 * where it already lived as a Hilt-instance bridge since batch 1) — resolved below via `get()`.
 * `GamificationEngineImpl`'s bus, every repository/use-case that emits onto it, AND `ManaHubApp`'s own
 * direct `AppOpenedToday` emission all now share that ONE Koin-native instance. `ManaHubApp` switched its
 * `progressionEventBus` field from a Hilt `@Inject lateinit var` to a Koin `by inject()` delegate (same
 * lazy-resolution pattern already used for `syncManaSymbols`): it is only read from `appScope.launch { }`
 * blocks that run strictly after `startKoin()` returns, so the lazy resolution is safe.
 *
 * ## [GamificationDao] / [GamificationStatsDao] — Room stays androidMain / Hilt-`DatabaseModule`-owned
 * Forward-bridged from `ManaHubApp` (new `@Inject lateinit var` fields), exactly like every other
 * Room DAO in this migration (`TournamentDao`, `PlaytestDao`, ...).
 *
 * ## [SyncPreferencesStore] — constructed natively, NOT forward-bridged
 * Its only dependency is the application [android.content.Context] (`@ApplicationContext` in Hilt); it
 * holds no in-memory state — every method reads/writes the SAME shared `user_prefs` DataStore file via
 * `Context.userPrefsDataStore`. A fresh Koin-built instance is therefore behaviourally IDENTICAL to the
 * Hilt-built singleton `core.sync.CollectionStatsSyncWorker`/the legacy `SyncManager` use — there is no
 * risk of divergent state from having two instances live in the two DI graphs.
 *
 * ## KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem)
 * [GamificationSyncWorker] and [QuestRotationWorker] were converted from `@HiltWorker`/`@AssistedInject`
 * to plain `CoroutineWorker`s registered here via Koin's `worker { }` DSL — [GamificationSyncManager]
 * and [QuestReconciler] were ALREADY native Koin singles in this module, so no new dependency wiring was
 * needed. The reverse-bridge `@Provides` for both in `KoinToHiltBridgeModule` were deleted (no other
 * Hilt-only consumer remained after the conversion — audited against the excluded online/voice/scanner/
 * nearby trees). See `core.di.SyncModule.provideWorkManager` for the [androidx.work.DelegatingWorkerFactory]
 * wiring that lets these Koin-resolved workers coexist with the excluded scanner's Hilt worker.
 *
 * [GamificationEngine], [GamificationRepository], [ClaimQuestRewardUseCase] and [AchievementBackfill]
 * were audited for OTHER Hilt-only consumers (the excluded online/voice/scanner/nearby trees + all
 * `@HiltWorker`s) — none found, so none of them need a reverse bridge.
 *
 * @param gamificationDao the Hilt/Room-owned [GamificationDao] singleton.
 * @param gamificationStatsDao the Hilt/Room-owned [GamificationStatsDao] singleton.
 * @return a Koin [Module] providing the entire native gamification engine graph.
 */
fun gamificationEngineKoinModule(
    gamificationDao: GamificationDao,
    gamificationStatsDao: GamificationStatsDao,
): Module = module {
    // ── Room DAOs (Hilt/DatabaseModule-owned, Room stays androidMain) — forward-bridged. ──
    single { gamificationDao }
    single { gamificationStatsDao }

    // ── System clock/timezone — trivial, constructed directly (previously Hilt @Provides). ──
    single<Clock> { Clock.System }
    single { TimeZone.currentSystemDefault() }

    // ── Sync infra: SyncPreferencesStore is stateless, constructed natively (see KDoc above). ──
    single { SyncPreferencesStore(androidContext()) }

    // ── Data layer. ──
    single<GamificationRemoteDataSource> {
        SupabaseGamificationDataSource(
            supabaseClient = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    single<GamificationRepository> {
        GamificationRepositoryImpl(
            dao = get(),
            clock = get(),
            timeZone = get(),
            userPreferencesDataStore = get(),
        )
    }

    // ── Engine collaborators. ──
    // crashReporter comes from coreBridgeKoinModule (single<CrashReporter>) — NOT re-declared here.
    single { XpGranter(dao = get(), clock = get(), timeZone = get(), userPreferencesDataStore = get()) }
    single { AchievementEvaluator(dao = get(), statsDao = get(), clock = get(), crashReporter = get()) }
    single { QuestEvaluator(dao = get(), clock = get(), timeZone = get()) }
    single { StreakTracker(dao = get(), clock = get(), timeZone = get()) }
    single { EntitlementGranter(dao = get(), clock = get()) }
    single { QuestStableIdProvider(authRepository = get(), dataStore = get()) }

    single<GamificationEngine> {
        GamificationEngineImpl(
            bus = get(), // native single in coreBridgeKoinModule
            xpGranter = get(),
            achievementEvaluator = get(),
            questEvaluator = get(),
            streakTracker = get(),
            entitlementGranter = get(),
            defaultDispatcher = Dispatchers.Default,
            crashReporter = get(),
        )
    }

    single { ClaimQuestRewardUseCase(gamificationRepository = get(), clock = get()) }

    // ── One-shot / periodic orchestrators (ManaHubApp + the two reverse-bridged @HiltWorkers). ──
    single {
        AchievementBackfill(
            dao = get(),
            statsDao = get(),
            clock = get(),
            defaultDispatcher = Dispatchers.Default,
        )
    }
    single {
        QuestReconciler(
            dao = get(),
            stableIdProvider = get(),
            claimQuestRewardUseCase = get(),
            clock = get(),
            timeZone = get(),
        )
    }
    single {
        GamificationSyncManager(
            gamificationDao = get(),
            remote = get(),
            syncPrefs = get(),
            ioDispatcher = Dispatchers.IO,
            crashReporter = get(),
        )
    }

    // ── KMP migration — Hilt→Koin cutover batch 6: WorkManager subsystem. ──
    // Both workers gained a `userPreferencesDataStore` param (WS1+WS3 Part A item 3, backend-
    // performance-optimization-plan.md §1) — defense-in-depth so an already-enqueued periodic run
    // no-ops when the gamification master flag is off. `UserPreferencesDataStore` is bridged in
    // `coreBridgeKoinModule` — resolved via `get()`, not re-registered here.
    worker {
        GamificationSyncWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            gamificationSyncManager = get(),
            authRepository = get(),
            userPreferencesDataStore = get(),
        )
    }
    worker {
        QuestRotationWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            questReconciler = get(),
            userPreferencesDataStore = get(),
        )
    }
}
