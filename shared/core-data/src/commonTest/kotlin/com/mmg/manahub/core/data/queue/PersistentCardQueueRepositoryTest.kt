package com.mmg.manahub.core.data.queue

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.QueuedCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentCardQueueRepositoryTest {

    private class RecordingCrashReporter : CrashReporter {
        val exceptions = mutableListOf<Throwable>()
        val logs = mutableListOf<String>()
        override fun recordException(throwable: Throwable) { exceptions += throwable }
        override fun log(message: String) { logs += message }
        override fun setCustomKey(key: String, value: String) = Unit
    }

    private fun card(
        scryfallId: String = "sid-1",
        name: String = "Lightning Bolt",
        priceUsd: Double? = 1.5,
        imageNormal: String? = "https://img/normal.jpg",
        oracleId: String = "oracle-1",
    ) = Card(
        scryfallId = scryfallId, name = name, printedName = null, manaCost = "{R}", cmc = 1.0,
        colors = listOf("R"), colorIdentity = listOf("R"), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "lea", setName = "Limited Edition Alpha", collectorNumber = "161",
        rarity = "common", releasedAt = "1993-08-05", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = imageNormal, imageArtCrop = null, imageBackNormal = null,
        priceUsd = priceUsd, priceUsdFoil = null, priceEur = 1.2, priceEurFoil = 3.0,
        legalityStandard = "", legalityPioneer = "", legalityModern = "", legalityCommander = "",
        flavorText = null, artist = null, scryfallUri = "", oracleId = oracleId,
    )

    private fun entry(
        id: String = "id-1",
        scryfallId: String = "sid-1",
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: String = "NM",
    ) = QueuedCard(
        card = card(scryfallId = scryfallId),
        quantity = quantity,
        isFoil = isFoil,
        language = "en",
        condition = condition,
        setCode = "lea",
        timestamp = 1_000L,
        id = id,
    )

    @Test
    fun encodeDecode_roundTrip_preservesPersistedFields() {
        val original = entry(quantity = 3, isFoil = true)

        val decoded = CardQueueJsonCodec.decode(CardQueueJsonCodec.encode(listOf(original)))

        assertEquals(0, decoded.skippedEntries)
        val restored = decoded.entries.single()
        assertEquals(original.id, restored.id)
        assertEquals(original.quantity, restored.quantity)
        assertEquals(original.isFoil, restored.isFoil)
        assertEquals(original.language, restored.language)
        assertEquals(original.condition, restored.condition)
        assertEquals(original.setCode, restored.setCode)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.card.scryfallId, restored.card.scryfallId)
        assertEquals(original.card.name, restored.card.name)
        assertEquals(original.card.setName, restored.card.setName)
        assertEquals(original.card.collectorNumber, restored.card.collectorNumber)
        assertEquals(original.card.priceUsd, restored.card.priceUsd)
        assertEquals(original.card.priceEurFoil, restored.card.priceEurFoil)
        assertNull(restored.card.priceUsdFoil)
        assertEquals(original.card.imageNormal, restored.card.imageNormal)
        assertNull(restored.card.imageArtCrop)
        assertEquals(original.card.oracleId, restored.card.oracleId)
        assertEquals(original.card.rarity, restored.card.rarity)
    }

    @Test
    fun decode_legacyScannerPayload_restoresEntries() {
        // Shape written by the pre-shared-queue ScannerViewModel via org.json: explicit nulls,
        // integral doubles without a fraction, and no oracleId/rarity keys.
        val legacy = """
            [{"scryfallId":"sid-1","name":"Lightning Bolt","setCode":"lea","setName":"Alpha",
              "lang":"en","priceUsd":2,"priceUsdFoil":null,"priceEur":1.25,"priceEurFoil":null,
              "imageNormal":null,"imageArtCrop":"https://img/art.jpg","collectorNumber":"161",
              "quantity":2,"isFoil":false,"language":"en","condition":"NM","timestamp":1700000000000,
              "id":"legacy-id"}]
        """.trimIndent()

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(legacy)).queue.value

        val only = restored.single()
        assertEquals("legacy-id", only.id)
        assertEquals(2, only.quantity)
        assertEquals(2.0, only.card.priceUsd)
        assertNull(only.card.priceUsdFoil)
        assertNull(only.card.imageNormal)
        assertEquals("https://img/art.jpg", only.card.imageArtCrop)
        assertEquals(1_700_000_000_000L, only.timestamp)
        assertEquals("", only.card.oracleId)
    }

    @Test
    fun decode_legacyEntriesWithoutId_getDistinctFreshIds() {
        val entryJson = """{"scryfallId":"sid-1","name":"Bolt","setCode":"lea","setName":"Alpha","lang":"en",
            "collectorNumber":"161","quantity":1,"isFoil":false,"language":"en","condition":"NM","timestamp":5}"""
        val restored = CardQueueJsonCodec.decode("[$entryJson,$entryJson]").entries

        assertEquals(2, restored.size)
        assertTrue(restored.all { it.id.isNotBlank() })
        assertNotEquals(restored[0].id, restored[1].id)
    }

    @Test
    fun decode_literalNullStringImage_isTreatedAsMissing() {
        // Older org.json optString() turned JSON null into "null", which was then re-persisted.
        val json = """[{"scryfallId":"s","name":"n","setCode":"x","setName":"X","lang":"en",
            "imageNormal":"null","collectorNumber":"1","quantity":1,"isFoil":false,"language":"en",
            "condition":"NM","timestamp":1,"id":"a"}]"""

        assertNull(CardQueueJsonCodec.decode(json).entries.single().card.imageNormal)
    }

    @Test
    fun restore_malformedEntry_isSkippedAndReported_othersKept() {
        val good = CardQueueJsonCodec.encode(listOf(entry(id = "good")))
        val payload = good.removeSuffix("]") + """,{"name":"missing scryfallId"}]"""
        val crashReporter = RecordingCrashReporter()

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(payload), crashReporter).queue.value

        assertEquals(listOf("good"), restored.map { it.id })
        assertEquals(1, crashReporter.exceptions.size)
    }

    @Test
    fun restore_corruptPayload_startsEmptyAndReports() {
        val crashReporter = RecordingCrashReporter()

        val repository = PersistentCardQueueRepository(InMemoryCardQueueStore("{not json"), crashReporter)

        assertTrue(repository.queue.value.isEmpty())
        assertEquals(1, crashReporter.exceptions.size)
        assertTrue(crashReporter.logs.single().startsWith("scanner_queue_restore_failed"))
    }

    @Test
    fun mutations_arePersisted_andRestoredByANewInstance() {
        val store = InMemoryCardQueueStore()
        val repository = PersistentCardQueueRepository(store)

        repository.add(entry(id = "a", scryfallId = "sid-a"))
        repository.add(entry(id = "b", scryfallId = "sid-b"))
        repository.incrementQuantity("a")
        repository.remove("b")

        val restored = PersistentCardQueueRepository(store).queue.value
        assertEquals(listOf("a"), restored.map { it.id })
        assertEquals(2, restored.single().quantity)
    }

    @Test
    fun addOrMerge_sameAttributes_mergesQuantity_differentAttributes_appends() {
        val repository = PersistentCardQueueRepository(InMemoryCardQueueStore())
        repository.addOrMerge(entry(id = "a", quantity = 1))

        repository.addOrMerge(entry(id = "b", quantity = 2))
        repository.addOrMerge(entry(id = "c", quantity = 1, isFoil = true))

        val queue = repository.queue.value
        assertEquals(listOf("a", "c"), queue.map { it.id })
        assertEquals(3, queue.first().quantity)
    }

    @Test
    fun duplicate_insertsFreshCopyRightAfterOriginal() {
        var now = 10L
        val repository = PersistentCardQueueRepository(InMemoryCardQueueStore(), nowMillis = { now })
        val a = entry(id = "a", scryfallId = "sid-a")
        repository.add(a)
        repository.add(entry(id = "b", scryfallId = "sid-b"))
        now = 99L

        val copy = repository.duplicate(a)

        assertEquals(listOf("a", copy.id, "b"), repository.queue.value.map { it.id })
        assertNotEquals("a", copy.id)
        assertEquals(99L, copy.timestamp)
    }

    @Test
    fun decrementQuantity_atOne_removesEntry() {
        val repository = PersistentCardQueueRepository(InMemoryCardQueueStore())
        repository.add(entry(id = "a", quantity = 2))

        repository.decrementQuantity("a")
        assertEquals(1, repository.queue.value.single().quantity)

        repository.decrementQuantity("a")
        assertTrue(repository.queue.value.isEmpty())
    }

    @Test
    fun removeByScryfallId_removesEveryEntryOfThatPrinting() {
        val repository = PersistentCardQueueRepository(InMemoryCardQueueStore())
        repository.add(entry(id = "a", scryfallId = "sid-1"))
        repository.add(entry(id = "b", scryfallId = "sid-1", isFoil = true))
        repository.add(entry(id = "c", scryfallId = "sid-2"))

        repository.removeByScryfallId("sid-1")

        assertEquals(listOf("c"), repository.queue.value.map { it.id })
    }

    @Test
    fun update_replacesById_andClearEmptiesPersistedPayload() {
        val store = InMemoryCardQueueStore()
        val repository = PersistentCardQueueRepository(store)
        repository.add(entry(id = "a"))

        repository.update(entry(id = "a", condition = "LP"))
        assertEquals("LP", repository.queue.value.single().condition)

        repository.clear()
        assertTrue(PersistentCardQueueRepository(store).queue.value.isEmpty())
    }
}
