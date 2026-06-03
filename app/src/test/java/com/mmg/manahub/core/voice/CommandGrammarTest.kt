package com.mmg.manahub.core.voice

import com.mmg.manahub.core.voice.domain.CommandGrammar
import com.mmg.manahub.core.voice.domain.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandGrammarTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — match() returns the correct command for every EN phrase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `match returns PlayLand for phrase i play a land`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("i play a land"))
    }

    @Test
    fun `match returns PlayLand for phrase play a land`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("play a land"))
    }

    @Test
    fun `match returns PlayLand for phrase land`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("land"))
    }

    @Test
    fun `match returns PlayLand for phrase play land`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("play land"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — match() returns null for unrecognised / reject tokens
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `match returns null for unk token`() {
        assertNull(CommandGrammar.match("[unk]"))
    }

    @Test
    fun `match returns null for arbitrary unrelated text`() {
        assertNull(CommandGrammar.match("draw a card"))
    }

    @Test
    fun `match returns null for empty string`() {
        assertNull(CommandGrammar.match(""))
    }

    @Test
    fun `match returns null for blank string`() {
        assertNull(CommandGrammar.match("   "))
    }

    @Test
    fun `match returns null for partial phrase`() {
        assertNull(CommandGrammar.match("a land"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — normalisation: extra whitespace and mixed case
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `match normalises upper case to PlayLand`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("I PLAY A LAND"))
    }

    @Test
    fun `match normalises mixed case to PlayLand`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("Play A Land"))
    }

    @Test
    fun `match collapses extra whitespace to PlayLand`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("  I  PLAY  A  LAND  "))
    }

    @Test
    fun `match trims leading and trailing whitespace`() {
        assertEquals(VoiceCommand.PlayLand, CommandGrammar.match("  land  "))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — grammarJson() structure
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `grammarJson contains unk token`() {
        assertTrue(
            "grammarJson must contain the [unk] rejection token",
            CommandGrammar.grammarJson().contains("\"[unk]\""),
        )
    }

    @Test
    fun `grammarJson contains all configured EN phrases`() {
        val json = CommandGrammar.grammarJson()
        val expectedPhrases = listOf("i play a land", "play a land", "land", "play land")
        expectedPhrases.forEach { phrase ->
            assertTrue(
                "grammarJson must contain phrase: $phrase",
                json.contains("\"$phrase\""),
            )
        }
    }

    @Test
    fun `grammarJson is a valid JSON array`() {
        val json = CommandGrammar.grammarJson()
        assertTrue("grammarJson must start with [", json.startsWith("["))
        assertTrue("grammarJson must end with ]", json.endsWith("]"))
    }

    @Test
    fun `grammarJson contains at least one entry beyond unk`() {
        val json = CommandGrammar.grammarJson()
        assertNotNull(json)
        // At minimum: ["i play a land","play a land","land","play land","[unk]"]
        assertTrue("grammarJson must have more than just [unk]", json.split(",").size > 1)
    }
}
