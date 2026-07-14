package com.mmg.manahub.core.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.provideCrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CrashlyticsModule {

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        return crashlytics
    }

    /**
     * KMP migration (P1.1): platform-neutral [CrashReporter] for the remaining Hilt-injected
     * (non-Koin) sites that still need crash/log reporting. Delegates to the `core-common`
     * Android `actual` ([provideCrashReporter]), which wraps [FirebaseCrashlytics] — same
     * telemetry destination as before, just resolved through the shared interface instead of
     * a direct `FirebaseCrashlytics.getInstance()` call. This is the Hilt-side counterpart to
     * the Koin `single<CrashReporter> { provideCrashReporter() }` in `CoreBridgeKoinModule`.
     */
    @Provides
    @Singleton
    fun provideCrashReporterForHilt(): CrashReporter = provideCrashReporter()
}
