package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.model.rules.*
import java.io.File
import kotlin.test.*

class RulesCorpusTest {
    private fun snapshot(): Pair<String, RulesManifest> {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "app/src/main/assets/rules/baseline.txt").exists() }
        val file = File(root, "app/src/main/assets/rules/baseline.txt")
        return file.readText() to RulesManifest("https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt", "2026-09-25", "8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca", 1, 4066, file.length().toInt())
    }
    @Test fun fullOfficialCorpusAndExactSearch() {
        val (text, manifest) = snapshot()
        val edition = RulesTextParser().parse(text, manifest)
        assertEquals(4066, edition.nodes.size)
        assertEquals(1, edition.nodes.count { it.id == "100" })
        assertEquals("704.5", edition.nodes.single { it.id == "704.5aa" }.parentId)
        assertTrue(edition.nodes.first().paragraphs.joinToString("\n").contains("Contents"))
        assertTrue(edition.nodes.last().paragraphs.joinToString("\n").contains("©2026 Wizards"))
        val search = RulesSearchIndex(edition)
        assertEquals("702.5a", search.search("702.5a").nodes.first().id)
        assertEquals("702.5", search.search("702.5").nodes.first().id)
        assertTrue(search.search("deathtouch").nodes.any { it.title == "Deathtouch" })
        assertTrue(search.search("zzzznonexistent").nodes.isEmpty())
        assertEquals(50, search.search("the").nodes.size)
    }
    @Test fun bomCrLfAndRejectedTruncation() {
        val (text, manifest) = snapshot()
        assertEquals(4066, RulesTextParser().parse("\uFEFF" + text.replace("\n", "\r\n"), manifest).nodes.size)
        assertFails { RulesTextParser().parse(text.substringBeforeLast("Glossary"), manifest) }
        assertFails { RulesTextParser().parse(text.replace("100.1b ", "100.1a "), manifest) }
    }
    @Test fun naturalNumericAndMultiLetterSuffix() {
        assertEquals(listOf("702.9", "702.10", "704.5z", "704.5aa"), listOf("704.5aa", "702.10", "704.5z", "702.9").sortedWith(naturalRuleOrder))
    }
    @Test fun malformedCandidatesCannotPublishPartialEditions() {
        val (text, manifest) = snapshot()
        val candidate = manifest.copy(nodeCount = 0)
        assertFails { RulesTextParser().parse(text.replaceFirst("702.5a ", "702.xa "), candidate); Unit }
        assertFails { RulesTextParser().parse(text.replaceFirst("See rule 704.5", "See rule 704.99999"), candidate); Unit }
        assertFails { RulesTextParser().parse(text.replaceFirst("See rule 704.5", "See rules 704.5 and 100.99999"), candidate); Unit }
        assertFails { RulesTextParser().parse(text.replace("September 25, 2026", "September 32, 2026"), candidate.copy(effectiveDate = "2026-09-32")) }
        val start = text.lastIndexOf("100. General")
        val end = text.lastIndexOf("101. The Magic Golden Rules")
        assertFails { RulesTextParser().parse(text.removeRange(start, end), candidate); Unit }
    }
}
