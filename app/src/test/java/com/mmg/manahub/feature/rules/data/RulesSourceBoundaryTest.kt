package com.mmg.manahub.feature.rules.data

import org.junit.Assert.*
import org.junit.Test

class RulesSourceBoundaryTest {
    @Test fun officialUrlsAreHttpsWithoutCredentialsOrAlternatePorts() {
        assertEquals("https://magic.wizards.com/en/rules", validatedRulesUrl("https://magic.wizards.com/en/rules"))
        assertTrue(validatedRulesUrl("https://media.wizards.com/2026/MagicCompRules%2020260925.txt").contains("%20"))
        for (url in listOf("http://media.wizards.com/rules.txt", "https://media.wizards.com.attacker.invalid/rules.txt", "https://user:secret@media.wizards.com/rules.txt", "https://media.wizards.com:8443/rules.txt")) {
            assertTrue(runCatching { validatedRulesUrl(url) }.isFailure)
        }
    }
    @Test fun malformedUtf8NeverGetsReplacementText() {
        assertTrue(runCatching { decodeRules(byteArrayOf(0xc3.toByte(), 0x28)) }.isFailure)
        assertEquals("Magic\u00A0rules", decodeRules("Magic\u00A0rules".toByteArray()))
    }
}
