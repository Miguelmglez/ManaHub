package com.mmg.manahub.app

import android.app.Application
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.domain.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.sync.CollectionSyncWorker
// import com.mmg.manahub.feature.scanner.EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ManaHubApp : Application() {

    @Inject lateinit var syncManaSymbols: SyncManaSymbolsUseCase
    @Inject lateinit var tagDictionaryRepo: TagDictionaryRepository
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var authRepository: AuthRepository
    // @Inject lateinit var embeddingDatabaseUpdater: EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        Coil.setImageLoader(ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }
            .build())

        appScope.launch {
            runCatching { syncManaSymbols() }
            runCatching { tagDictionaryRepo.loadAndApply() }
        }

        // COMMENTED OUT — Cloudflare R2 embedding DB download replaced by ML Kit OCR
        // embeddingDatabaseUpdater.scheduleUpdateCheck()

        // Schedule/cancel the periodic background sync based on auth state.
        // CollectionViewModel also does this for the collection screen, but this
        // global observer ensures sync is cancelled even when that screen is not alive.
        appScope.launch {
            authRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.Authenticated ->
                        CollectionSyncWorker.schedulePeriodicSync(workManager)
                    is SessionState.Unauthenticated -> {
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                    }
                    else -> {}
                }
            }
        }
    }
}
