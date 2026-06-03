package com.mmg.manahub.core.voice.domain

/**
 * Central catalogue of recognized voice phrases.
 *
 * Each [GrammarEntry] maps a single normalized phrase to a [VoiceCommand] in a
 * specific [VoiceLanguage]. The recognizer builds its restricted Vosk grammar from
 * the subset of entries enabled for the current session, and resolves recognized
 * text back to commands via [match].
 */
object CommandGrammar {

    /** A single recognizable phrase, its command, and the language it belongs to. */
    data class GrammarEntry(val phrase: String, val command: VoiceCommand, val language: VoiceLanguage)

    /** All known phrases across every command and language. */
    val allEntries: List<GrammarEntry> = listOf(
        // PlayLand — English
        GrammarEntry("i play a land",         VoiceCommand.PlayLand, VoiceLanguage.ENGLISH),
        GrammarEntry("play a land",            VoiceCommand.PlayLand, VoiceLanguage.ENGLISH),
        GrammarEntry("play land",              VoiceCommand.PlayLand, VoiceLanguage.ENGLISH),
        // PlayLand — Spanish
        GrammarEntry("juego una tierra",       VoiceCommand.PlayLand, VoiceLanguage.SPANISH),
        GrammarEntry("juego tierra",           VoiceCommand.PlayLand, VoiceLanguage.SPANISH),
        GrammarEntry("bajo tierra",           VoiceCommand.PlayLand, VoiceLanguage.SPANISH),
        GrammarEntry("bajo una tierra",           VoiceCommand.PlayLand, VoiceLanguage.SPANISH),
        // PlayLand — German
        GrammarEntry("ich spiele ein land",    VoiceCommand.PlayLand, VoiceLanguage.GERMAN),
        GrammarEntry("land spielen",           VoiceCommand.PlayLand, VoiceLanguage.GERMAN),
        // EndTurn — English
        GrammarEntry("end turn",               VoiceCommand.EndTurn, VoiceLanguage.ENGLISH),
        GrammarEntry("end my turn",            VoiceCommand.EndTurn, VoiceLanguage.ENGLISH),
        GrammarEntry("you go",            VoiceCommand.EndTurn, VoiceLanguage.ENGLISH),
        GrammarEntry("your turn",            VoiceCommand.EndTurn, VoiceLanguage.ENGLISH),

        // EndTurn — Spanish
        GrammarEntry("fin de turno",           VoiceCommand.EndTurn, VoiceLanguage.SPANISH),
        GrammarEntry("paso turno",         VoiceCommand.EndTurn, VoiceLanguage.SPANISH),
        GrammarEntry("tu turno",         VoiceCommand.EndTurn, VoiceLanguage.SPANISH),
        GrammarEntry("te toca",         VoiceCommand.EndTurn, VoiceLanguage.SPANISH),
        // EndTurn — German
        GrammarEntry("zug beenden",            VoiceCommand.EndTurn, VoiceLanguage.GERMAN),
        GrammarEntry("naechster zug",          VoiceCommand.EndTurn, VoiceLanguage.GERMAN),
        GrammarEntry("dein zug",          VoiceCommand.EndTurn, VoiceLanguage.GERMAN),

        )

    /**
     * Builds the Vosk grammar JSON array for the given enabled commands and languages.
     * Always appends "[unk]" so out-of-grammar speech is rejected rather than mis-matched.
     */
    fun grammarJson(
        enabledCommands: Set<VoiceCommand>,
        enabledLanguages: Set<VoiceLanguage>,
    ): String {
        val phrases = allEntries
            .filter { it.command in enabledCommands && it.language in enabledLanguages }
            .map { it.phrase } + "[unk]"
        return "[${phrases.joinToString(",") { "\"$it\"" }}]"
    }

    /** Backward-compatible no-arg variant returning all phrases in all languages. */
    fun grammarJson(): String = grammarJson(
        setOf(VoiceCommand.PlayLand, VoiceCommand.EndTurn),
        VoiceLanguage.entries.toSet(),
    )

    /**
     * Resolves recognized text to a [VoiceCommand], restricted to the given languages.
     * Returns null when the normalized text does not exactly match any enabled phrase.
     */
    fun match(text: String, enabledLanguages: Set<VoiceLanguage> = VoiceLanguage.entries.toSet()): VoiceCommand? {
        val normalized = text.lowercase().trim().replace(Regex("\\s+"), " ")
        return allEntries
            .filter { it.language in enabledLanguages }
            .firstOrNull { it.phrase == normalized }
            ?.command
    }
}
