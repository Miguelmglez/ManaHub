package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.model.rules.*
import kotlin.test.*

class RulesSearchIndexTest {
    private fun edition(nodes: List<RulesNode>) = RulesEdition(RulesManifest("official", "2026-09-25", "a".repeat(64), 1, nodes.size, 1), nodes)
    @Test fun exactTitleGlossaryAndBodyRanking() {
        val index = RulesSearchIndex(edition(listOf(
            RulesNode("702.10", RulesNodeKind.RULE, "702.10", listOf("Enchant is a keyword. See 702.5a.")),
            RulesNode("702.9", RulesNodeKind.RULE, "702.9", listOf("Enchant.")),
            RulesNode("702.5a", RulesNodeKind.SUBRULE, "702.5a", listOf("Enchant is a static ability.")),
            RulesNode("702.5", RulesNodeKind.RULE, "Enchant", listOf("702.5. Enchant")),
            RulesNode("glossary:enchant", RulesNodeKind.GLOSSARY, "Enchant", listOf("The enchant ability.")),
        )))
        assertEquals("702.5a", index.search("702.5a").nodes.first().id)
        assertEquals("702.5", index.search("702.5").nodes.first().id)
        assertEquals(listOf("702.5", "glossary:enchant"), index.search("ENCHANT!").nodes.take(2).map { it.id })
        assertTrue(index.search("enchant").nodes.indexOfFirst { it.id == "702.9" } < index.search("enchant").nodes.indexOfFirst { it.id == "702.10" })
        assertTrue(index.search(" ").nodes.isEmpty())
        assertTrue(index.search("nevermatching").nodes.isEmpty())
    }
    @Test fun pagesAreBoundedWithoutDuplicates() {
        val index = RulesSearchIndex(edition((1..101).map { RulesNode("100.$it", RulesNodeKind.RULE, "Match", listOf("Original exact paragraph")) }))
        val pages = listOf(index.search("match"), index.search("match", 50), index.search("match", 100))
        assertEquals(listOf(50, 50, 1), pages.map { it.nodes.size })
        assertEquals(101, pages.flatMap { it.nodes }.map { it.id }.distinct().size)
        assertEquals("Original exact paragraph", pages.first().nodes.first().paragraphs.single())
    }
}
