package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionExportFormatterTest {

    private val entries = listOf(
        entry(quantity = 4, name = "Lightning Bolt", setCode = "2x2", number = "117", isFoil = true, condition = "LP", language = "ja"),
        entry(quantity = 1, name = "Borrowing 100,000 Arrows", setCode = "sok", number = "31", condition = "NM", language = "en"),
        entry(quantity = 2, name = "\"Ach! Hans, Run!\"", setCode = "unh", number = "116", condition = "PO", language = "de"),
        entry(quantity = 1, name = "Fire // Ice", setCode = "mh2", number = "290", condition = "M", language = "zhs"),
    )

    @Test
    fun `text export writes moxfield lines with a skippable header`() {
        val text = CollectionExportFormatter.format(entries, CollectionFileFormat.TEXT, headerComment = "ManaHub export")

        val lines = text.lines().filter { it.isNotBlank() }
        assertEquals("// ManaHub export", lines.first())
        assertEquals("4 Lightning Bolt (2X2) 117 *F*", lines[1])
        assertEquals("1 Borrowing 100,000 Arrows (SOK) 31", lines[2])
    }

    @Test
    fun `text round-trips name, printing, quantity and foil`() {
        val parsed = CollectionImportParser.parse(
            CollectionExportFormatter.format(entries, CollectionFileFormat.TEXT, headerComment = "x")
        )

        assertTrue(parsed.rejectedLines.isEmpty())
        assertEquals(
            entries.map { listOf(it.quantity, it.name, it.setCode, it.collectorNumber, it.isFoil) },
            parsed.lines.map { listOf(it.quantity, it.name, it.setCode, it.collectorNumber, it.isFoil) },
        )
    }

    @Test
    fun `moxfield csv round-trips every attribute its scale can hold`() {
        val csv = CollectionExportFormatter.format(entries, CollectionFileFormat.MOXFIELD_CSV)
        val parsed = CollectionImportParser.parse(csv)

        assertEquals(CollectionFileFormat.MOXFIELD_CSV, parsed.format)
        assertEquals(CollectionExportFormatter.MOXFIELD_HEADER.joinToString(","), csv.lines().first())
        assertEquals(entries.map { it.roundTripKey() }, parsed.lines.map { it.roundTripKey() })
    }

    @Test
    fun `manabox csv round-trips including the scryfall id`() {
        val csv = CollectionExportFormatter.format(entries, CollectionFileFormat.MANABOX_CSV)
        val parsed = CollectionImportParser.parse(csv)

        assertEquals(CollectionFileFormat.MANABOX_CSV, parsed.format)
        assertEquals(entries.map { it.roundTripKey() }, parsed.lines.map { it.roundTripKey() })
        assertEquals(entries.map { it.scryfallId }, parsed.lines.map { it.scryfallId })
    }

    @Test
    fun `moxfield condition scale collapses EX and GD to lightly played`() {
        val csv = CollectionExportFormatter.format(
            listOf(entries[0].copy(condition = "EX"), entries[1].copy(condition = "GD")),
            CollectionFileFormat.MOXFIELD_CSV,
        )

        assertEquals(listOf("LP", "LP"), CollectionImportParser.parse(csv).lines.map { it.condition })
    }

    @Test
    fun `file name uses source, date and extension`() {
        val name = CollectionExportFormatter.fileName("wishlist", CollectionFileFormat.MANABOX_CSV, 1_758_499_200_000L, TimeZone.UTC)

        assertEquals("manahub-wishlist-2025-09-22.csv", name)
    }

    @Test
    fun `sorted orders by name then set then number`() {
        val sorted = CollectionExportFormatter.sorted(entries)

        assertEquals(listOf("\"Ach! Hans, Run!\"", "Borrowing 100,000 Arrows", "Fire // Ice", "Lightning Bolt"), sorted.map { it.name })
    }

    @Test fun streamingRecordsMatchLegacyOutputWithoutFormulaMutation() {
        val rows=entries+entries[0].copy(name="+2 Mace",scryfallId="mace-printing")+entries[1].copy(name="Unicode Ω, \"quoted\"\nsecond line",scryfallId="unicode-printing")
        for(format in CollectionFileFormat.entries) {
            val streamed=CollectionExportFormatter.header(format,"fixture")+rows.joinToString("") { CollectionExportFormatter.record(it,format) }
            assertEquals(CollectionExportFormatter.format(rows,format,"fixture"),streamed)
            assertTrue(streamed.contains("+2 Mace"))
        }
        val csv=CollectionExportFormatter.header(CollectionFileFormat.MANABOX_CSV)+rows.joinToString("") { CollectionExportFormatter.record(it,CollectionFileFormat.MANABOX_CSV) }
        val parsed=CollectionImportParser.parse(csv)
        assertEquals(rows.map { it.roundTripKey() },parsed.lines.map { it.roundTripKey() })
    }
    @Test fun durableQuerySerializationRetainsNestedCriteriaAndMembership() {
        val advanced=com.mmg.manahub.core.model.AdvancedSearchQuery(listOf(
            com.mmg.manahub.core.model.SearchCriterion.AnyOf(listOf(listOf(com.mmg.manahub.core.model.SearchCriterion.Name("Ω",true)),listOf(com.mmg.manahub.core.model.SearchCriterion.HasTag(listOf("tagA"),mapOf("tagA" to "o:draw"))))),
            com.mmg.manahub.core.model.SearchCriterion.CollectionStatus(com.mmg.manahub.core.model.CollectionSource.WISHLIST)))
        val json=kotlinx.serialization.json.Json
        val stored=json.encodeToString(com.mmg.manahub.core.model.AdvancedSearchQuery.serializer(),advanced)
        assertEquals(advanced,json.decodeFromString(com.mmg.manahub.core.model.AdvancedSearchQuery.serializer(),stored))
    }
    private fun CollectionExportEntry.roundTripKey() =
        listOf(quantity, name, setCode, collectorNumber, isFoil, condition, language)

    private fun CollectionImportLine.roundTripKey() =
        listOf(quantity, name, setCode, collectorNumber, isFoil, condition, language)

    private fun entry(
        quantity: Int,
        name: String,
        setCode: String,
        number: String,
        isFoil: Boolean = false,
        condition: String,
        language: String,
    ) = CollectionExportEntry(
        quantity = quantity,
        name = name,
        setCode = setCode,
        setName = "Set $setCode",
        collectorNumber = number,
        scryfallId = "00000000-0000-0000-0000-00000000${number.padStart(4, '0')}",
        rarity = "rare",
        isFoil = isFoil,
        condition = condition,
        language = language,
    )
}


