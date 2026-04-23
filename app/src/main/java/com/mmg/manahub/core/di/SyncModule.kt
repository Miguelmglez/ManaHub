package com.mmg.manahub.core.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides WorkManager with Hilt-aware [Configuration].
 *
 * Using a custom [Configuration] is required when workers use [HiltWorkerFactory]
 * for dependency injection. The [WorkManager] instance is provided as a singleton
 * so the same instance is shared across the entire app.
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
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    /**
     * Provides a [WorkManager] configured to use Hilt's [HiltWorkerFactory].
     * This replaces WorkManager's default reflection-based worker instantiation
     * with Hilt's DI-aware factory so workers can receive injected dependencies.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
        workerFactory: HiltWorkerFactory,
    ): WorkManager {
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(context, config)
        return WorkManager.getInstance(context)
    }
}
