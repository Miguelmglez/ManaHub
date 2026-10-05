package com.mmg.manahub.core.data.rules

import com.mmg.manahub.core.model.rules.*

class RulesTextParser {
    fun parse(text: String, manifest: RulesManifest): RulesEdition {
        require(manifest.schemaVersion == 1 && text.length in 100_000..4_000_000)
        require('\u0000' !in text && '\uFFFD' !in text)
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}")))
        val dates = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val date = Regex("These rules are effective as of ([A-Za-z]+) ([0-9]{1,2}), ([0-9]{4})\\.").find(text)?.groupValues ?: error("Missing rules effective date")
        val month = dates.indexOf(date[1]) + 1
        val year = date[3].toInt()
        val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = listOf(31, if (leapYear) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        require(month in 1..12 && date[2].toInt() in 1..days[month - 1])
        require(month > 0 && manifest.effectiveDate == "${date[3]}-${month.toString().padStart(2, '0')}-${date[2].padStart(2, '0')}")
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) { blocks += current.joinToString("\n"); current.clear() }
            } else current += line
        }
        if (current.isNotEmpty()) blocks += current.joinToString("\n")
        require(blocks.first().startsWith("Magic: The Gathering Comprehensive Rules"))
        val bodyStart = blocks.indexOfLast { it == "1. Game Concepts" }
        require(bodyStart >= 0 && blocks.take(bodyStart).any { it == "Contents" })
        val nodes = mutableListOf(RulesNode("introduction", RulesNodeKind.INTRODUCTION, "Introduction", blocks.take(bodyStart)))
        val chapter = Regex("([1-9])\\. (.+)")
        val section = Regex("([1-9][0-9]{2})\\. (.+)")
        val rule = Regex("([1-9][0-9]{2}\\.[0-9]+[a-z]*)\\.? (.*)", RegexOption.DOT_MATCHES_ALL)
        var inGlossary = false
        var inCredits = false
        for (block in blocks.drop(bodyStart)) {
            require(nodes.size < 10_000) { "Rules node limit exceeded" }
            if (block == "Glossary") { inGlossary = true; nodes += RulesNode("glossary", RulesNodeKind.SECTION, "Glossary", emptyList()); continue }
            if (block == "Credits") { inGlossary = false; inCredits = true; nodes += RulesNode("credits", RulesNodeKind.NOTICE, "Credits and notices", emptyList()); continue }
            if (inCredits) {
                val previous = nodes.removeAt(nodes.lastIndex)
                nodes += previous.copy(paragraphs = previous.paragraphs + block)
            } else if (inGlossary) {
                val term = block.substringBefore('\n')
                require(block.contains('\n') && term.length in 1..120) { "Invalid glossary block" }
                nodes += RulesNode("glossary:${normalizeRulesText(term)}", RulesNodeKind.GLOSSARY, term, listOf(block), "glossary")
            } else {
                val first = block.substringBefore('\n')
                val chapterMatch = chapter.matchEntire(first)
                val sectionMatch = section.matchEntire(first)
                val ruleMatch = rule.matchEntire(block)
                when {
                    chapterMatch != null -> { require(first == block); nodes += RulesNode(chapterMatch.groupValues[1], RulesNodeKind.CHAPTER, chapterMatch.groupValues[2], listOf(block)) }
                    sectionMatch != null -> { require(first == block); nodes += RulesNode(sectionMatch.groupValues[1], RulesNodeKind.SECTION, sectionMatch.groupValues[2], listOf(block), sectionMatch.groupValues[1].take(1)) }
                    ruleMatch != null -> {
                        val id = ruleMatch.groupValues[1]
                        nodes += RulesNode(id, if (id.last().isLetter()) RulesNodeKind.SUBRULE else RulesNodeKind.RULE, id, listOf(block), if (id.last().isLetter()) id.trimEnd { it.isLetter() } else id.substringBefore('.'))
                    }
                    else -> {
                        require(!Regex("^[0-9]+\\.").containsMatchIn(block)) { "Malformed rules identifier" }
                        require(nodes.last().kind in setOf(RulesNodeKind.RULE, RulesNodeKind.SUBRULE)) { "Unrecognized rules structure" }
                        val previous = nodes.removeAt(nodes.lastIndex)
                        nodes += previous.copy(paragraphs = previous.paragraphs + block)
                    }
                }
            }
        }
        require(inCredits && nodes.any { it.kind == RulesNodeKind.GLOSSARY } && nodes.last().paragraphs.any { it.contains("©2026 Wizards") || it.contains("Wizards. U.S. Pat.") })
        require(nodes.map { it.id }.distinct().size == nodes.size) { "Duplicate rules identifiers" }
        require(nodes.size <= 10_000)
        require(nodes.filter { it.kind == RulesNodeKind.CHAPTER }.map { it.id } == (1..9).map { it.toString() })
        val bodyIds = nodes.filter { it.kind in setOf(RulesNodeKind.RULE, RulesNodeKind.SUBRULE) }.map { it.id }
        require(bodyIds == bodyIds.sortedWith(naturalRuleOrder)) { "Unordered rules identifiers" }
        val tocIds = blocks.take(bodyStart).flatMap { it.split('\n') }.mapNotNull { section.matchEntire(it)?.groupValues?.get(1) }
        require(tocIds == nodes.filter { it.kind == RulesNodeKind.SECTION && it.id != "glossary" }.map { it.id }) { "Incomplete table of contents" }
        val ids = nodes.map { it.id }.toSet()
        require(nodes.all { it.parentId == null || it.parentId in ids }) { "Missing rules parent" }
        require(manifest.nodeCount == 0 || manifest.nodeCount == nodes.size) { "Rules count mismatch" }
        val glossary = nodes.filter { it.kind == RulesNodeKind.GLOSSARY }.associate { normalizeRulesText(it.title) to it.id }
        val referencePattern = Regex("\\b[1-9][0-9]{2}\\.[0-9]+[a-z]*\\b")
        val quotedTerm = Regex("[“\"]([^”\"]+)[”\"]")
        val enriched = nodes.map { node ->
            val body = node.paragraphs.joinToString("\n")
            val explicitReferences = Regex("\\b(?:rules?|sections?) ([1-9][0-9]{0,2}(?:\\.[0-9]+[a-z]*)?)\\b").findAll(body).map { it.groupValues[1] }.toList()
            require(explicitReferences.all { it in ids }) { "Missing rules reference target" }
            require(Regex("\\b[1-9][0-9]{2}\\.[0-9]+[a-z]*\\b").findAll(body).all { it.value in ids }) { "Missing grouped rules reference target" }
            val refs = referencePattern.findAll(body).map { it.value }.filter { it in ids && it != node.id }.toList() + explicitReferences.filter { it != node.id } + quotedTerm.findAll(body).mapNotNull { glossary[normalizeRulesText(it.groupValues[1])] }
            node.copy(references = refs.distinct())
        }
        return RulesEdition(manifest.copy(nodeCount = nodes.size), enriched)
    }
}

fun normalizeRulesText(value: String): String = value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")

private val ruleParts = Regex("[0-9]+|[a-z]+|[^a-z0-9]+")
val naturalRuleOrder: Comparator<String> = Comparator { a, b ->
    val parts = ruleParts
    val left = parts.findAll(a).map { it.value }.toList()
    val right = parts.findAll(b).map { it.value }.toList()
    var result = 0
    for (i in 0 until minOf(left.size, right.size)) {
        result = if (left[i].toIntOrNull() != null && right[i].toIntOrNull() != null) left[i].toInt().compareTo(right[i].toInt()) else if (left[i].all { it.isLetter() } && right[i].all { it.isLetter() }) compareValuesBy(left[i], right[i], { it.length }, { it }) else left[i].compareTo(right[i])
        if (result != 0) break
    }
    if (result == 0) left.size.compareTo(right.size) else result
}

class RulesSearchIndex(edition: RulesEdition) {
    private val ordered = edition.nodes.sortedWith { a, b -> naturalRuleOrder.compare(a.id, b.id) }
    private val normalized = ordered.map { normalizeRulesText(it.title) to normalizeRulesText(it.paragraphs.joinToString("\n")) }
    fun search(query: String, offset: Int = 0): RulesSearchPage {
        val bounded = query.take(200).trim()
        val tokens = normalizeRulesText(bounded).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return RulesSearchPage(emptyList(), 0, 0)
        val ranked = ordered.mapIndexedNotNull { i, node ->
            val (title, body) = normalized[i]
            val exact = node.id == bounded.lowercase().removeSuffix(".")
            val score = when {
                exact -> 0
                tokens.all { title.contains(it) } -> 1
                tokens.all { body.contains(it) } -> 2
                else -> return@mapIndexedNotNull null
            }
            score to node
        }
        val hits = (0..2).flatMap { score -> ranked.filter { it.first == score } }
        val start = offset.coerceIn(0, hits.size)
        return RulesSearchPage(hits.drop(start).take(50).map { it.second }, start, hits.size)
    }
}

