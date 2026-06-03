package com.mmg.manahub.core.voice.domain

/**
 * Languages supported by the offline voice recognizer.
 *
 * Each entry carries display metadata for the voice-controls UI (flag emoji, short
 * ISO-style code, and a human-readable name) plus the download/storage identifiers used
 * by [VoiceModelRepository] to fetch and locate the per-language Vosk model.
 *
 * @property displayFlag Flag emoji shown next to the language in selectors and dialogs.
 * @property displayCode Short ISO-style code (e.g. "EN") for compact chips.
 * @property displayName Human-readable language name (e.g. "English").
 * @property modelFileName Zip file name downloaded from the model host (e.g. "en.zip").
 * @property modelDirName Local directory name where the extracted model lives (e.g. "en").
 */
enum class VoiceLanguage(
    val displayFlag: String,
    val displayCode: String,
    val displayName: String,
    val modelFileName: String,
    val modelDirName: String,
) {
    ENGLISH("🇬🇧", "EN", "English", "en.zip", "en"),
    SPANISH("🇪🇸", "ES", "Spanish", "es.zip", "es"),
    GERMAN("🇩🇪", "DE", "German", "de.zip", "de"),
}
