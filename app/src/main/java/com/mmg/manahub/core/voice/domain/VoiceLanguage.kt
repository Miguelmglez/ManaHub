package com.mmg.manahub.core.voice.domain

/**
 * Languages supported by the offline voice recognizer.
 *
 * Each entry carries a flag emoji and a short ISO-style code for display
 * in the voice-controls UI (language selector chips and phrase dialogs).
 */
enum class VoiceLanguage(val displayFlag: String, val displayCode: String) {
    ENGLISH("🇬🇧", "EN"),
    SPANISH("🇪🇸", "ES"),
    GERMAN("🇩🇪", "DE"),
}
