package com.mmg.manahub.core.domain.collection.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
