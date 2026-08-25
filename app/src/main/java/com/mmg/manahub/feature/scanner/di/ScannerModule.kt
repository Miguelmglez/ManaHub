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
 * The pipeline uses ML Kit Text Recognition (OCR) — see [CardOcrAnalyzer] and
 * `CardRecognizer`. The earlier embedding-database / TFLite cosine nearest-neighbour pipeline
 * was removed (WS5, `scanner-reliability-plan.md`, 2026-08-25); it is preserved in git history
 * if it is ever needed again.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

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
