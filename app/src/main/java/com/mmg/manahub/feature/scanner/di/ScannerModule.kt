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
     * Provides the [HashDatabase] singleton.
     *
     * Prefers a previously downloaded file in [Context.getFilesDir] over the bundled asset,
     * so that OTA hash-DB updates take effect immediately without a new release.
     * Falls back to the bundled asset when no downloaded file is present.
     */
    @Provides
    @Singleton
    fun provideHashDatabase(@ApplicationContext context: Context): HashDatabase {
        val db = HashDatabase(context)
        val downloadedFile = java.io.File(context.filesDir, "card_hashes.bin")
        if (downloadedFile.exists()) db.loadFromFile(downloadedFile)
        else db.loadFromAssets()
        return db
    }

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
