package com.mmg.manahub.feature.scanner.di

import android.content.Context
import com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer
import com.mmg.manahub.feature.scanner.presentation.SoundManager
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
     * Deliberately `@Singleton` (one instance per process) and never closed by the UI — see
     * [CardOcrAnalyzer]'s KDoc lifecycle contract (WS1, 2026-08-24). ML Kit's bundled Latin
     * recognizer is cheap to hold and expensive to re-init per screen entry, and once closed
     * a `TextRecognizer` client never recovers on its own; [CardOcrAnalyzer] self-heals
     * instead by recreating its internal client on demand, so downgrading this to a
     * non-singleton scope is unnecessary.
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
