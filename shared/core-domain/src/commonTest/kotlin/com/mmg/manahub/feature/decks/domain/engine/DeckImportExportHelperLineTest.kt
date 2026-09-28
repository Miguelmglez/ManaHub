package com.mmg.manahub.feature.decks.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-line text format shared by deck import/export and the collection transfer. */
class DeckImportExportHelperLineTest {

    @Test
    fun `the set group is written only when a collector number can follow it`() {
        assertEquals(
            "4 Lightning Bolt (M11) 163",
            DeckImportExportHelper.formatLine(4, "Lightning Bolt", "m11", "163", includeSet = true, isFoil = false),
        )
        // The importer's set group REQUIRES a number: "(M11)" alone would be swallowed by the name.
        assertEquals(
            "4 Lightning Bolt",
            DeckImportExportHelper.formatLine(4, "Lightning Bolt", "m11", "", includeSet = true, isFoil = false),
        )
        assertEquals(
            "4 Lightning Bolt",
            DeckImportExportHelper.formatLine(4, "Lightning Bolt", "", "163", includeSet = true, isFoil = false),
        )
    }

    @Test
    fun `a printing-less line still parses back to the same name and quantity`() {
        val line = DeckImportExportHelper.formatLine(2, "Sol Ring", "vma", "", includeSet = true, isFoil = true)

        val parsed = DeckImportExportHelper.parseCardLine(line)!!

        assertEquals(2, parsed.quantity)
        assertEquals("Sol Ring", parsed.name)
        assertNull(parsed.setCode)
        assertNull(parsed.collectorNumber)
        assertTrue(parsed.isFoil)
    }

    @Test
    fun `quantity multipliers and both finish markers are accepted`() {
        assertEquals(4, DeckImportExportHelper.parseCardLine("4x Opt")?.quantity)
        assertEquals(4, DeckImportExportHelper.parseCardLine("4X Opt")?.quantity)
        assertEquals(true, DeckImportExportHelper.parseCardLine("1 Opt *F*")?.isFoil)
        assertEquals(true, DeckImportExportHelper.parseCardLine("1 Opt *e*")?.isFoil)
        assertEquals(false, DeckImportExportHelper.parseCardLine("1 Opt")?.isFoil)
    }

    @Test
    fun `a line with no name is not a card line`() {
        assertNull(DeckImportExportHelper.parseCardLine("4   "))
        assertNull(DeckImportExportHelper.parseCardLine("4"))
        assertNull(DeckImportExportHelper.parseCardLine("Lightning Bolt"))
    }
}
