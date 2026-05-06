package com.mmg.manahub.feature.scanner.di

import android.content.Context
import com.mmg.manahub.feature.scanner.CardEmbeddingModel
import com.mmg.manahub.feature.scanner.EmbeddingDatabase
import com.mmg.manahub.feature.scanner.SoundManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides scanner-related singleton dependencies.
 *
 * Separated from the core DI modules because the embedding database and TFLite model
 * are specific to the scanner feature and carry their own lifecycle
 * (loaded once at startup, potentially hot-reloaded via WorkManager).
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    /**
     * Provides the [EmbeddingDatabase] singleton.
     *
     * Prefers a previously downloaded file in [Context.getFilesDir] over the bundled asset,
     * so that OTA embedding-DB updates take effect immediately without a new release.
     * Falls back to the bundled asset when no downloaded file is present (returns an empty
     * DB on first launch when the model has never been downloaded).
     */
    @Provides
    @Singleton
    fun provideEmbeddingDatabase(@ApplicationContext context: Context): EmbeddingDatabase {
        val db = EmbeddingDatabase(context)
        val downloadedFile = java.io.File(context.filesDir, "card_embeddings.bin")
        if (downloadedFile.exists()) db.loadFromFile(downloadedFile)
        else db.loadFromAssets()
        return db
    }

    /**
     * Provides the [CardEmbeddingModel] singleton.
     *
     * The TFLite interpreter is initialised eagerly in the constructor.
     * If `mobilenet_v3_small.tflite` is absent from assets, the constructor
     * throws [IllegalStateException] — this is intentional so the failure is
     * surfaced immediately at startup rather than silently at scan time.
     */
    @Provides
    @Singleton
    fun provideCardEmbeddingModel(@ApplicationContext context: Context): CardEmbeddingModel =
        CardEmbeddingModel(context)

    /**
     * Provides the [SoundManager] singleton.
     *
     * All PCM buffers are generated once in the constructor and reused for every
     * subsequent playback call. No files in `res/raw/` are required.
     */
    @Provides
    @Singleton
    fun provideSoundManager(@ApplicationContext context: Context): SoundManager =
        SoundManager(context)
}
