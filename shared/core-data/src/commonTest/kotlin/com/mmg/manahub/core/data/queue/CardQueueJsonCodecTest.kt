package com.mmg.manahub.core.data.queue

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.QueuedCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Adversarial and legacy payloads for the shared queue codec.
class CardQueueJsonCodecTest {

    private fun card(scryfallId: String, priceUsd: Double?) = Card(
        scryfallId = scryfallId, name = "Bolt", printedName = null, manaCost = "{R}", cmc = 1.0,
        colors = listOf("R"), colorIdentity = listOf("R"), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "lea", setName = "Alpha", collectorNumber = "161",
        rarity = "common", releasedAt = "", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = priceUsd, priceUsdFoil = null, priceEur = null, priceEurFoil = null,
        legalityStandard = "", legalityPioneer = "", legalityModern = "", legalityCommander = "",
        flavorText = null, artist = null, scryfallUri = "", oracleId = "o",
    )

    private fun entry(id: String, scryfallId: String, price: Double?) = QueuedCard(
        card = card(scryfallId, price), quantity = 1, isFoil = false, language = "en",
        condition = "NM", setCode = "lea", timestamp = 1L, id = id,
    )

    @Test
    fun nanPrice_roundTrip_keepsEveryEntry() {
        val store = InMemoryCardQueueStore()
        val repo = PersistentCardQueueRepository(store)
        repo.add(entry("a", "s1", Double.NaN))
        repo.add(entry("b", "s2", 2.0))

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(store.payload))

        assertEquals(listOf("a", "b"), restored.queue.value.map { it.id })
        assertNull(restored.queue.value.first().card.priceUsd)
    }

    @Test
    fun infinitePrice_isNotRestoredAsAPrice() {
        val store = InMemoryCardQueueStore()
        PersistentCardQueueRepository(store).add(entry("a", "s1", Double.POSITIVE_INFINITY))

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(store.payload))

        assertNull(restored.queue.value.single().card.priceUsd)
    }

    @Test
    fun duplicateIdsInPayload_areDeduplicatedOnRestore() {
        val payload = CardQueueJsonCodec.encode(listOf(entry("dup", "s1", 1.0), entry("dup", "s2", 1.0)))

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(payload)).queue.value

        // LazyColumn(key = { it.id }) in CardQueueSheet throws on duplicate keys.
        assertEquals(2, restored.map { it.id }.distinct().size)
    }

    @Test
    fun legacyOrgJsonPayload_restoresEveryEntry() {
        // Shape written by the pre-shared-queue ScannerViewModel (Android org.json): escaped
        // slashes, whole-number doubles as ints, optString "null" literals, no id / oracleId / rarity.
        val payload = """[{"scryfallId":"s1","name":"Bolt","setCode":"lea","setName":"Alpha","lang":"en",""" +
            """"priceUsd":2,"priceUsdFoil":null,"priceEur":0.5,"priceEurFoil":null,""" +
            """"imageNormal":"https:\/\/cards.scryfall.io\/normal\/a.jpg","imageArtCrop":"null",""" +
            """"collectorNumber":"161","quantity":3,"isFoil":false,"language":"en","condition":"NM",""" +
            """"timestamp":1700000000000},""" +
            """{"scryfallId":"s2","name":"Opt","setCode":"xln","setName":"Ixalan","lang":"es",""" +
            """"priceUsd":null,"priceUsdFoil":null,"priceEur":null,"priceEurFoil":null,""" +
            """"imageNormal":null,"imageArtCrop":null,"collectorNumber":"65","quantity":1,""" +
            """"isFoil":true,"language":"es","condition":"LP","timestamp":1700000000001,"id":"legacy-2"}]"""

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(payload)).queue.value

        assertEquals(listOf("s1", "s2"), restored.map { it.card.scryfallId })
        val first = restored.first()
        assertEquals(2.0, first.card.priceUsd)
        assertEquals("https://cards.scryfall.io/normal/a.jpg", first.card.imageNormal)
        assertNull(first.card.imageArtCrop)
        assertEquals(3, first.quantity)
        assertEquals("legacy-2", restored[1].id)
        assertEquals(2, restored.map { it.id }.distinct().size)
    }

    @Test
    fun nonPositiveQuantity_isNotRestoredAsACommittableEntry() {
        val payload = CardQueueJsonCodec.encode(listOf(entry("a", "s1", 1.0).copy(quantity = 0)))

        val restored = PersistentCardQueueRepository(InMemoryCardQueueStore(payload)).queue.value

        // A 0-copy entry would reach CommitScannedCardsUseCase and addOrIncrement(quantity = 0).
        assertEquals(0, restored.count { it.quantity < 1 })
    }
}
