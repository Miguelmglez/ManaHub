package com.mmg.manahub.app

// import com.mmg.manahub.feature.scanner.EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncWorker
import com.mmg.manahub.core.gamification.data.sync.QuestRotationWorker
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.engine.AchievementBackfill
import com.mmg.manahub.core.gamification.engine.EntitlementGranter
import com.mmg.manahub.core.gamification.engine.QuestReconciler
import com.mmg.manahub.core.sync.CollectionStatsSyncWorker
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.PriceRefreshWorker
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class ManaHubApp : Application() {

    @Inject lateinit var syncManaSymbols: SyncManaSymbolsUseCase
    @Inject lateinit var tagDictionaryRepo: TagDictionaryRepository
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var pushTokenRepository: PushTokenRepository
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var gamificationEngine: GamificationEngine
    @Inject lateinit var progressionEventBus: ProgressionEventBus
    @Inject lateinit var achievementBackfill: AchievementBackfill
    @Inject lateinit var questReconciler: QuestReconciler
    @Inject lateinit var entitlementGranter: EntitlementGranter
    @Inject lateinit var gamificationSyncManager: GamificationSyncManager
    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore
    // @Inject lateinit var embeddingDatabaseUpdater: EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()

        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
        }

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .components { add(SvgDecoder.Factory()) }
                .build()
        )

        appScope.launch {
            runCatching { syncManaSymbols() }
            runCatching { tagDictionaryRepo.loadAndApply() }
        }

        // Start the gamification engine collecting the progression bus (idempotent), then
        // emit the daily-open event. The engine's ledger (key app_open:{localDate}) dedupes
        // multiple cold starts the same day, so a plain emit on every launch is correct.
        gamificationEngine.start(appScope)

        // One-shot Family-A achievement backfill (ADR-002 §4): retroactively unlock achievements the
        // user already qualifies for, suppressing celebrations. Guarded by a DataStore flag so it runs
        // exactly once after the v39 migration. Failures are swallowed — never block app start.
        appScope.launch {
            runCatching {
                if (!userPreferencesDataStore.isGamificationBackfillDone()) {
                    achievementBackfill.run()
                    userPreferencesDataStore.setGamificationBackfillDone()
                }
            }
            // Retroactive cosmetic catch-up (ADR-002 §10): grant entitlements the player already
            // qualifies for (current level + all unlocked achievements, incl. any just backfilled).
            // Idempotent — only inserts missing rows — so it is safe on every launch. Runs AFTER the
            // backfill block above so backfilled achievement unlocks are visible to it. Failures
            // swallowed; never block app start.
            runCatching { entitlementGranter.reconcileAll() }
        }

        appScope.launch {
            runCatching {
                progressionEventBus.emit(
                    ProgressionEvent.AppOpenedToday(
                        localDate = LocalDate.now().toString(),
                        occurredAt = Instant.now(),
                    )
                )
            }
        }

        // Roll quests over on app start (local-first: runs regardless of auth). Idempotent — settles
        // any stale instances and generates the current period if missing. Failures swallowed.
        appScope.launch {
            runCatching { questReconciler.reconcile() }
        }

        PriceRefreshWorker.scheduleDailyRefresh(workManager)
        CollectionStatsSyncWorker.scheduleDailySync(workManager)
        QuestRotationWorker.scheduleDaily(workManager)

        // COMMENTED OUT — Cloudflare R2 embedding DB download replaced by ML Kit OCR
        // embeddingDatabaseUpdater.scheduleUpdateCheck()

        // Schedule/cancel the periodic background sync based on auth state.
        // CollectionViewModel also does this for the collection screen, but this
        // global observer ensures sync is cancelled even when that screen is not alive.
        appScope.launch {
            authRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        CollectionSyncWorker.schedulePeriodicSync(workManager)
                        // Gamification Phase 4 sync (ADR-002 §11): schedule the periodic worker AND run a
                        // one-time guest→account reconcile so an anonymous/guest's local progress merges
                        // into the account exactly once on sign-in. Monotonic merges make the reconcile
                        // idempotent, so a harmless re-run on a later session re-emission is safe.
                        GamificationSyncWorker.schedulePeriodicSync(workManager)
                        appScope.launch {
                            runCatching { gamificationSyncManager.reconcileOnSignIn(state.user.id) }
                        }
                        appScope.launch {
                            runCatching {
                                val token = FirebaseMessaging.getInstance().token.await()
                                pushTokenRepository.register(token)
                            }
                        }
                    }
                    is SessionState.Unauthenticated -> {
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_ONE_TIME)
                        appScope.launch {
                            runCatching {
                                val token = FirebaseMessaging.getInstance().token.await()
                                pushTokenRepository.unregister(token)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel("trades_high", "Trade Proposals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New trade proposals and counter-proposals"
            },
            NotificationChannel("trades_updates", "Trade Updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Trade accepted, declined, cancelled, completed"
            },
            NotificationChannel("friends", "Friends", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Friend requests and acceptances"
            }
        ).forEach { nm.createNotificationChannel(it) }
    }
}
