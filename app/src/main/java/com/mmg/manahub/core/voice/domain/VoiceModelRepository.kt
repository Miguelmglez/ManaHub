package com.mmg.manahub.core.voice.domain

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Manages the lifecycle of the offline voice-recognition models, one per [VoiceLanguage].
 *
 * Each language has an independent download/extraction/validation state and an independent
 * on-disk model directory, so users can install only the languages they need.
 */
interface VoiceModelRepository {

    /**
     * Per-language model state. A language missing from the map is treated as
     * [VoiceModelState.NotDownloaded] by callers.
     */
    val modelStates: StateFlow<Map<VoiceLanguage, VoiceModelState>>

    /**
     * Downloads, extracts, and validates the model for [language].
     *
     * No-ops when the model is already [VoiceModelState.Ready] or currently
     * [VoiceModelState.Downloading]. Updates only the entry for [language] in [modelStates].
     */
    suspend fun download(language: VoiceLanguage)

    /**
     * Deletes the on-disk model for [language] and resets its state to
     * [VoiceModelState.NotDownloaded].
     */
    suspend fun delete(language: VoiceLanguage)

    /**
     * Returns the validated model directory for [language], or `null` when the model is not
     * present or fails validation.
     */
    fun modelDir(language: VoiceLanguage): File?
}
