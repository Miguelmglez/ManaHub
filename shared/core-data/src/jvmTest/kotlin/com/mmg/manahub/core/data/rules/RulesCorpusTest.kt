package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.model.rules.*
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class RulesCorpusTest {
    private fun assets(): File {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "app/src/main/assets/rules/baseline.txt").exists() }
        return File(root, "app/src/main/assets/rules")
    }

    private fun manifest(directory: File): RulesManifest {
        val json = Json.parseToJsonElement(File(directory, "baseline-manifest.json").readText()).jsonObject
        return RulesManifest(
            sourceUrl = json.getValue("sourceUrl").jsonPrimitive.content,
            effectiveDate = json.getValue("effectiveDate").jsonPrimitive.content,
            sha256 = json.getValue("sha256").jsonPrimitive.content,
            schemaVersion = json.getValue("schemaVersion").jsonPrimitive.int,
            nodeCount = json.getValue("nodeCount").jsonPrimitive.int,
            byteCount = json.getValue("byteCount").jsonPrimitive.int,
        )
    }

    private fun snapshot(): Pair<String, RulesManifest> {
        val directory = assets()
        return File(directory, "baseline.txt").readText() to manifest(directory)
    }

    @Test fun packagedBaselineBytesMatchManifest() {
        val directory = assets()
        val manifest = manifest(directory)
        val bytes = File(directory, "baseline.txt").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(manifest.byteCount, bytes.size, "Packaged baseline byte count must match its manifest")
        assertEquals(manifest.sha256, hash, "Packaged baseline SHA-256 must match its manifest")
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
