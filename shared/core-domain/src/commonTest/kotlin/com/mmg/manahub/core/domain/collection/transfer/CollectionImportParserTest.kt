package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.feature.decks.domain.engine.DeckImportExportHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CollectionImportParserTest {

    @Test
    fun `text lines parse set, number and foil and etched markers`() {
        val parsed = CollectionImportParser.parse(
            """
            4 Lightning Bolt (2X2) 117 *F*
            1x Sol Ring
            2 Fire // Ice (MH2) 290 *E*
            3 Counterspell
            """.trimIndent()
        )

        assertEquals(CollectionFileFormat.TEXT, parsed.format)
        assertEquals(4, parsed.lines.size)
        val bolt = parsed.lines[0]
        assertEquals(4, bolt.quantity)
        assertEquals("Lightning Bolt", bolt.name)
        assertEquals("2x2", bolt.setCode)
        assertEquals("117", bolt.collectorNumber)
        assertTrue(bolt.isFoil)
        assertEquals("Sol Ring", parsed.lines[1].name)
        assertFalse(parsed.lines[1].isFoil)
        assertEquals("Fire // Ice", parsed.lines[2].name)
        assertTrue(parsed.lines[2].isFoil)
        assertEquals("NM", bolt.condition)
        assertEquals("en", bolt.language)
    }

    @Test
    fun `section headers and comments are ignored and every section merges into one list`() {
        val parsed = CollectionImportParser.parse(
            """
            // exported by somebody
            Commander
            1 Atraxa, Praetors' Voice
            Deck
            4 Lightning Bolt
            Sideboard:
            2 Lightning Bolt
            Maybeboard
            1 Opt
            """.trimIndent()
        )

        assertTrue(parsed.rejectedLines.isEmpty())
        assertEquals(listOf("Atraxa, Praetors' Voice", "Lightning Bolt", "Opt"), parsed.lines.map { it.name })
        assertEquals(6, parsed.lines.single { it.name == "Lightning Bolt" }.quantity)
    }

    @Test
    fun `garbage and non-positive lines are rejected, not dropped`() {
        val parsed = CollectionImportParser.parse("hello world\n0 Lightning Bolt\n2 Opt")

        assertEquals(listOf("Opt"), parsed.lines.map { it.name })
        assertEquals(listOf("hello world", "0 Lightning Bolt"), parsed.rejectedLines)
    }

    @Test
    fun `merge keeps foil and non-foil apart and caps the quantity`() {
        val parsed = CollectionImportParser.parse("9000 Opt\n9000 Opt\n1 Opt *F*")

        assertEquals(2, parsed.lines.size)
        assertEquals(CollectionImportParser.MAX_QUANTITY_PER_LINE, parsed.lines[0].quantity)
        assertTrue(parsed.lines[1].isFoil)
    }

    @Test
    fun `copies dropped by the quantity cap are reported, never silent`() {
        val parsed = CollectionImportParser.parse("6000 Forest (M21) 274\n6000 Forest (M21) 274")

        assertEquals(1, parsed.lines.size)
        assertEquals(CollectionImportParser.MAX_QUANTITY_PER_LINE, parsed.lines[0].quantity)
        assertEquals(2_001, parsed.clampedCopies)
    }

    @Test
    fun `a list inside the cap reports no clamped copies`() {
        val parsed = CollectionImportParser.parse("4 Opt\n4 Opt")

        assertEquals(8, parsed.lines[0].quantity)
        assertEquals(0, parsed.clampedCopies)
    }

    // ── Real-world files the detector has to survive ─────────────────────────

    @Test
    fun `an excel sep preamble does not hide the csv header`() {
        val parsed = CollectionImportParser.parse("sep=,\nCount,Name,Edition,Collector Number\n2,Opt,xln,65")

        assertEquals(CollectionFileFormat.MOXFIELD_CSV, parsed.format)
        assertEquals(listOf("Opt"), parsed.lines.map { it.name })
        assertTrue(parsed.rejectedLines.isEmpty())
    }

    @Test
    fun `a comment or title row above the header does not hide it either`() {
        val preamble = CollectionImportParser.parse("// exported 2026-09-22\nCount,Name,Edition\n1,Opt,xln")
        val titleRow = CollectionImportParser.parse("My collection\nCount,Name,Edition\n1,Opt,xln")

        assertEquals(CollectionFileFormat.MOXFIELD_CSV, preamble.format)
        assertEquals(listOf("Opt"), preamble.lines.map { it.name })
        assertEquals(CollectionFileFormat.MOXFIELD_CSV, titleRow.format)
        assertEquals(listOf("Opt"), titleRow.lines.map { it.name })
    }

    @Test
    fun `a BOM-prefixed CRLF moxfield export parses`() {
        val parsed = CollectionImportParser.parse(
            "﻿Count,Name,Edition,Collector Number\r\n3,Opt,xln,65\r\n1,Sol Ring,vma,4\r\n"
        )

        assertEquals(CollectionFileFormat.MOXFIELD_CSV, parsed.format)
        assertEquals(listOf("Opt" to 3, "Sol Ring" to 1), parsed.lines.map { it.name to it.quantity })
        assertTrue(parsed.rejectedLines.isEmpty())
    }

    @Test
    fun `a quoted field carrying a newline stays one record`() {
        val parsed = CollectionImportParser.parse("Count,Name,Edition\n1,\"Opt\nSecond line\",xln")

        assertEquals(1, parsed.lines.size)
        assertEquals("Opt\nSecond line", parsed.lines.single().name)
    }

    @Test
    fun `a name with a comma and a doubled quote survives the csv reader`() {
        val parsed = CollectionImportParser.parse(
            "Count,Name,Edition\n1,\"Jace, the \"\"Mind\"\" Sculptor\",wwk"
        )

        assertEquals("Jace, the \"Mind\" Sculptor", parsed.lines.single().name)
    }

    @Test
    fun `a row with fewer fields than the header is read, not rejected`() {
        val parsed = CollectionImportParser.parse("Count,Name,Edition,Condition,Language\n2,Opt,xln")

        assertEquals(1, parsed.lines.size)
        assertEquals(2, parsed.lines.single().quantity)
        assertEquals("xln", parsed.lines.single().setCode)
        assertEquals(CollectionCardAttributes.DEFAULT_CONDITION, parsed.lines.single().condition)
    }

    @Test
    fun `quantities that overflow an Int or go negative are rejected, not wrapped`() {
        val csv = CollectionImportParser.parse("Count,Name\n99999999999,Opt\n-4,Sol Ring\n2,Counterspell")
        val text = CollectionImportParser.parse("99999999999 Opt\n-4 Sol Ring\n2 Counterspell")

        assertEquals(listOf("Counterspell"), csv.lines.map { it.name })
        assertTrue(csv.lines.all { it.quantity > 0 })
        assertEquals(listOf("Counterspell"), text.lines.map { it.name })
        assertEquals(2, text.rejectedLines.size)
    }

    @Test
    fun `an adventure name round-trips through the text format`() {
        val exported = CollectionExportFormatter.format(
            listOf(
                CollectionExportEntry(
                    quantity = 2, name = "Bonecrusher Giant // Stomp", setCode = "eld", setName = "Throne of Eldraine",
                    collectorNumber = "115", scryfallId = "bcg", rarity = "rare", isFoil = true,
                    condition = "NM", language = "en",
                )
            ),
            CollectionFileFormat.TEXT,
        )

        val parsed = CollectionImportParser.parse(exported)

        assertTrue(parsed.rejectedLines.isEmpty())
        val line = parsed.lines.single()
        assertEquals("Bonecrusher Giant // Stomp", line.name)
        assertEquals("eld", line.setCode)
        assertEquals("115", line.collectorNumber)
        assertEquals(2, line.quantity)
        assertTrue(line.isFoil)
    }

    // ── Hostile input: what is parsed, and what is reported back ─────────────

    @Test
    fun `an over-long line never reaches the card-line regex`() {
        // Valid in every way EXCEPT its length, so a null here can only come from the length guard.
        val longName = "a".repeat(DeckImportExportHelper.MAX_CARD_LINE_LENGTH)

        assertNull(DeckImportExportHelper.parseCardLine("1 $longName"))
        assertEquals(4, DeckImportExportHelper.parseCardLine("4 Lightning Bolt")?.quantity)
    }

    @Test
    fun `a line built to trigger quadratic backtracking parses in bounded time`() {
        // Without the length guard this single line costs minutes: the cost of CARD_LINE_REGEX is
        // quadratic in the line length, and matchEntire cannot be cancelled.
        val pathological = "1 a" + " ".repeat(40_000) + "b"
        val mark = TimeSource.Monotonic.markNow()

        val parsed = CollectionImportParser.parse(pathological)

        assertTrue(mark.elapsedNow() < 5.seconds, "took ${mark.elapsedNow()}")
        assertTrue(parsed.lines.isEmpty())
        assertEquals(1, parsed.rejectedLines.size)
    }

    @Test
    fun `a reported line is truncated, whatever the source line's length`() {
        val parsed = CollectionImportParser.parse("x".repeat(100_000))

        val reported = parsed.rejectedLines.single()
        assertTrue(reported.length <= CollectionImportParser.MAX_REPORTED_LINE_LENGTH + 1, "${reported.length}")
        assertTrue(reported.endsWith("…"))
    }

    @Test
    fun `the rejected list stops growing at its cap but the count does not`() {
        val parsed = CollectionImportParser.parse((1..5_000).joinToString("\n") { "not a card line $it" })

        assertEquals(CollectionImportParser.MAX_REJECTED_LINES, parsed.rejectedLines.size)
        assertEquals(5_000, parsed.rejectedCount)
    }

    @Test
    fun `scanning stops at the line ceiling instead of materialising the document`() {
        val beyond = DeckImportExportHelper.MAX_SCANNED_LINES + 10_000
        val parsed = CollectionImportParser.parse((1..beyond).joinToString("\n") { "x" })

        // Every line is junk, so the count is exactly what was scanned before giving up.
        assertEquals(DeckImportExportHelper.MAX_SCANNED_LINES, parsed.rejectedCount)
        assertEquals(CollectionImportParser.MAX_REJECTED_LINES, parsed.rejectedLines.size)
    }

    @Test
    fun `the parse loop honours its caller's cancellation probe`() {
        var calls = 0

        assertFailsWith<IllegalStateException> {
            CollectionImportParser.parse((1..50_000).joinToString("\n") { "junk $it" }) {
                calls++
                if (calls == 2) error("cancelled")
            }
        }
    }

    @Test
    fun `a short list never pays for the cancellation probe`() {
        var calls = 0

        CollectionImportParser.parse("4 Lightning Bolt\n1 Opt") { calls++ }

        assertEquals(0, calls)
    }

    @Test
    fun `an unterminated quote cannot turn the rest of the file into one reported line`() {
        // CsvCodec folds everything after an unterminated quote into ONE record, which then reaches
        // UI state, the preference file and a clipboard Intent as rawLine.
        val csv = "Count,Name,Edition\n1,\"Opt,xln\n" + "junk,".repeat(20_000)

        val parsed = CollectionImportParser.parse(csv)

        val rawLines = parsed.lines.map { it.rawLine } + parsed.rejectedLines
        assertTrue(rawLines.isNotEmpty())
        assertTrue(
            rawLines.all { it.length <= CollectionImportParser.MAX_REPORTED_LINE_LENGTH + 1 },
            "longest reported line was ${rawLines.maxOf { it.length }}",
        )
    }

    // ── A picked file that is not text at all ────────────────────────────────

    @Test
    fun `decoded binary is recognised as not text`() {
        val jpegish = buildString {
            // Escapes, not literal control bytes: literal NUL/SOH make git treat this whole
            // file as binary, which hides every future change from the secret-scan gate.
            append("\uFFFD\uFFFD\uFFFD\u0000JFIF\u0000\u0001")
            repeat(200) { append("\uFFFD\u0001\u0002 data ") }
        }

        assertTrue(CollectionImportParser.looksBinary(jpegish))
    }

    @Test
    fun `a real list with accents and tabs is not mistaken for binary`() {
        val list = "4 Lightning Bolt (2X2) 117\n1\tSéance\n2 Jötun Grunt\n1 Æther Vial"

        assertFalse(CollectionImportParser.looksBinary(list))
        assertFalse(CollectionImportParser.looksBinary(""))
    }

    @Test
    fun `moxfield csv is detected and handles quoted commas and quotes`() {
        val csv = listOf(
            "\"Count\",\"Tradelist Count\",\"Name\",\"Edition\",\"Condition\",\"Language\",\"Foil\",\"Tags\",\"Last Modified\",\"Collector Number\",\"Alter\",\"Proxy\",\"Purchase Price\"",
            "\"2\",\"0\",\"Borrowing 100,000 Arrows\",\"sok\",\"Lightly Played\",\"Japanese\",\"foil\",\"\",\"2024-01-01\",\"31\",\"False\",\"False\",\"\"",
            "\"1\",\"0\",\"\"\"Ach! Hans, Run!\"\"\",\"unh\",\"Near Mint\",\"English\",\"\",\"\",\"\",\"116\",\"False\",\"False\",\"\"",
            "\"1\",\"0\",\"Opt\",\"xln\",\"Damaged\",\"Klingon\",\"etched\",\"\",\"\",\"65\",\"False\",\"False\",\"\"",
        ).joinToString("\r\n")

        val parsed = CollectionImportParser.parse(csv)

        assertEquals(CollectionFileFormat.MOXFIELD_CSV, parsed.format)
        assertEquals(3, parsed.lines.size)
        val arrows = parsed.lines[0]
        assertEquals("Borrowing 100,000 Arrows", arrows.name)
        assertEquals(2, arrows.quantity)
        assertEquals("sok", arrows.setCode)
        assertEquals("31", arrows.collectorNumber)
        assertEquals("LP", arrows.condition)
        assertEquals("ja", arrows.language)
        assertTrue(arrows.isFoil)
        assertEquals("\"Ach! Hans, Run!\"", parsed.lines[1].name)
        assertEquals("PO", parsed.lines[2].condition)
        assertEquals("en", parsed.lines[2].language)
        assertTrue(parsed.lines[2].isFoil)
    }

    @Test
    fun `manabox csv is detected and carries the scryfall id and snake case conditions`() {
        val csv = """
            Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,Language,Purchase price currency
            Lightning Bolt,2X2,Double Masters 2022,117,foil,uncommon,3,123,E3285E6B-3E79-4D7C-BF96-D920F973B122,0.5,false,false,light_played,de,EUR
            Opt,XLN,Ixalan,65,normal,common,1,456,not-a-uuid,,false,false,mystery,xx,USD
        """.trimIndent()

        val parsed = CollectionImportParser.parse(csv)

        assertEquals(CollectionFileFormat.MANABOX_CSV, parsed.format)
        val bolt = parsed.lines[0]
        assertEquals("e3285e6b-3e79-4d7c-bf96-d920f973b122", bolt.scryfallId)
        assertEquals(3, bolt.quantity)
        assertTrue(bolt.isFoil)
        assertEquals("LP", bolt.condition)
        assertEquals("de", bolt.language)
        val opt = parsed.lines[1]
        assertNull(opt.scryfallId)
        assertFalse(opt.isFoil)
        assertEquals("NM", opt.condition)
        assertEquals("en", opt.language)
    }

    @Test
    fun `csv rows without quantity or identifier are rejected`() {
        val csv = "Count,Name,Edition\nabc,Opt,xln\n2,,\n1,Opt,xln"

        val parsed = CollectionImportParser.parse(csv)

        assertEquals(1, parsed.lines.size)
        assertEquals(2, parsed.rejectedLines.size)
    }

    @Test
    fun `condition and language aliases map to app codes`() {
        assertEquals("M", CollectionCardAttributes.conditionCode("Mint"))
        assertEquals("NM", CollectionCardAttributes.conditionCode("near_mint"))
        assertEquals("EX", CollectionCardAttributes.conditionCode("excellent"))
        assertEquals("PL", CollectionCardAttributes.conditionCode("Moderately Played"))
        assertEquals("NM", CollectionCardAttributes.conditionCode(null))
        assertEquals("zhs", CollectionCardAttributes.languageCode("Chinese Simplified"))
        assertEquals("pt", CollectionCardAttributes.languageCode("Portuguese"))
        assertEquals("en", CollectionCardAttributes.languageCode("Elvish"))
    }
}
