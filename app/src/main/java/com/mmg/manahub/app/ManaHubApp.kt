package com.mmg.manahub.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.domain.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.sync.SyncWorker
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ManaHubApp : Application() {

    @Inject lateinit var syncManaSymbols: SyncManaSymbolsUseCase
    @Inject lateinit var tagDictionaryRepo: TagDictionaryRepository
    @Inject lateinit var workManager: WorkManager

    override fun onCreate() {
        super.onCreate()

        // Disable Crashlytics in debug builds to avoid noise in the Firebase
        // dashboard and prevent development data from being transmitted.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // Register the SVG decoder so Coil can render Scryfall SVG symbol images.
        Coil.setImageLoader(ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }
            .build())

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { syncManaSymbols() }
            // Apply any user overrides before card analyzers run for the first time.
            runCatching { tagDictionaryRepo.loadAndApply() }
        }

        // Schedule the hourly background sync. KEEP keeps any existing schedule
        // so reinstalls / process restarts don't reset the next-run timer.
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.buildRequest(),
        )
    }
}
