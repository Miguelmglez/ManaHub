package com.mmg.manahub.feature.scanner.di

import android.content.Context
import com.mmg.manahub.feature.scanner.CardOcrAnalyzer
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
 * The embedding-database and TFLite-model providers are commented out because the
 * pipeline now uses ML Kit Text Recognition (OCR) instead of cosine nearest-neighbour
 * search over a downloaded embedding binary.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    // COMMENTED OUT — replaced by ML Kit OCR provider below.
    /*
    @Provides
    @Singleton
    fun provideEmbeddingDatabase(@ApplicationContext context: Context): EmbeddingDatabase {
        val db = EmbeddingDatabase(context)
        val downloadedFile = java.io.File(context.filesDir, "card_embeddings.bin")
        if (downloadedFile.exists()) db.loadFromFile(downloadedFile)
        else db.loadFromAssets()
        return db
    }

    @Provides
    @Singleton
    fun provideCardEmbeddingModel(@ApplicationContext context: Context): CardEmbeddingModel =
        CardEmbeddingModel(context)
    */

    /**
     * Provides the [CardOcrAnalyzer] singleton.
     *
     * Initialises the ML Kit Latin text recognizer client eagerly.
     * No asset downloads or device-side model caching are required — ML Kit
     * bundles the base model in the app and updates it transparently via Google Play Services.
     */
    @Provides
    @Singleton
    fun provideCardOcrAnalyzer(): CardOcrAnalyzer = CardOcrAnalyzer()

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
