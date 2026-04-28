package com.mmg.manahub.feature.scanner.di

import android.content.Context
import com.mmg.manahub.feature.scanner.HashDatabase
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
 * Separated from the core DI modules because the hash database is specific
 * to the scanner feature and carries its own lifecycle (loaded once at startup).
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    /**
     * Provides the [HashDatabase] singleton, pre-loaded from `assets/card_hashes.bin`.
     *
     * Loading happens synchronously during Hilt component initialization.
     * If the asset is missing, [HashDatabase.cardCount] stays 0 and the scanner
     * degrades gracefully to the [com.mmg.manahub.feature.scanner.RecognitionResult.Detected]
     * result (shows the overlay but cannot identify the card).
     */
    @Provides
    @Singleton
    fun provideHashDatabase(@ApplicationContext context: Context): HashDatabase =
        HashDatabase(context).also { it.loadFromAssets() }

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
