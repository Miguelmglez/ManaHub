package com.mmg.manahub.core.data.queue

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.QueuedCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A queue built with a `persistenceScope` must never encode or write on the caller's thread — the
 * Collection import review holds thousands of rows, and re-encoding them per tap ANRs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersistentCardQueueAsyncPersistenceTest {

    private class CountingStore : CardQueueStore {
        var payload: String? = null
        var writes = 0

        override fun read(): String? = payload

        override fun write(payload: String) {
            writes++
            this.payload = payload
        }
    }

    private fun card(id: String) = Card(
        scryfallId = id, name = "Card $id", printedName = null, manaCost = "{R}", cmc = 1.0,
        colors = listOf("R"), colorIdentity = listOf("R"), typeLine = "Instant", printedTypeLine = null,
        oracleText = null, printedText = null, keywords = emptyList(), power = null, toughness = null,
        loyalty = null, setCode = "lea", setName = "Limited Edition Alpha", collectorNumber = "161",
        rarity = "common", releasedAt = "1993-08-05", frameEffects = emptyList(), promoTypes = emptyList(),
        lang = "en", imageNormal = null, imageArtCrop = null, imageBackNormal = null,
        priceUsd = 1.5, priceUsdFoil = null, priceEur = 1.2, priceEurFoil = 3.0,
        legalityStandard = "", legalityPioneer = "", legalityModern = "", legalityCommander = "",
        flavorText = null, artist = null, scryfallUri = "", oracleId = "oracle-$id",
    )

    private fun entry(id: String) = QueuedCard(
        card = card(id),
        quantity = 1,
        isFoil = false,
        language = "en",
        condition = "NM",
        setCode = "lea",
        timestamp = 0L,
    )

    @Test
    fun `a bulk add updates the queue synchronously and persists off the caller's thread`() = runTest {
        val store = CountingStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = PersistentCardQueueRepository(store, persistenceScope = CoroutineScope(dispatcher))
        val entries = List(10_000) { entry("id-$it") }

        repository.addAll(entries)

        assertEquals(10_000, repository.queue.value.size, "the in-memory queue must be readable immediately")
        assertEquals(0, store.writes, "nothing may be encoded or written on the caller's thread")

        advanceUntilIdle()
        assertEquals(1, store.writes)
        assertEquals(10_000, PersistentCardQueueRepository(InMemoryCardQueueStore(store.payload)).queue.value.size)
    }

    @Test
    fun `a burst of quantity taps collapses into a single write of the latest queue`() = runTest {
        val store = CountingStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = PersistentCardQueueRepository(store, persistenceScope = CoroutineScope(dispatcher))
        repository.addAll(listOf(entry("a")))
        advanceUntilIdle()
        val writesAfterAdd = store.writes

        val id = repository.queue.value.single().id
        repeat(20) { repository.incrementQuantity(id) }

        assertEquals(writesAfterAdd, store.writes, "taps must not write on the caller's thread")
        assertEquals(21, repository.queue.value.single().quantity)

        advanceUntilIdle()
        // Conflated: the burst costs a handoff to the waiting consumer plus one buffered signal,
        // never one encode per tap.
        assertTrue(
            store.writes - writesAfterAdd <= 2,
            "20 taps cost ${store.writes - writesAfterAdd} writes",
        )
        assertEquals(
            21,
            PersistentCardQueueRepository(InMemoryCardQueueStore(store.payload)).queue.value.single().quantity,
        )
    }

    @Test
    fun `without a persistence scope every mutation still writes before returning`() = runTest {
        val store = CountingStore()
        val repository = PersistentCardQueueRepository(store)

        repository.add(entry("a"))

        assertEquals(1, store.writes)
        assertEquals(1, PersistentCardQueueRepository(InMemoryCardQueueStore(store.payload)).queue.value.size)
    }
}
