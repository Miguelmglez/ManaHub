package com.mmg.manahub.core.data.queue

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.model.hasSameAttributesAs
import com.mmg.manahub.core.model.newQueuedCardId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock

/**
 * [CardQueueRepository] that keeps the queue in memory and writes the whole list to [store] after
 * every mutation. The persisted payload is restored once, on construction.
 *
 * Must be a single app-wide instance: two instances over the same [store] would overwrite each
 * other's writes.
 */
class PersistentCardQueueRepository(
    private val store: CardQueueStore,
    private val crashReporter: CrashReporter? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CardQueueRepository {

    private val _queue = MutableStateFlow(restore())

    override val queue: StateFlow<List<QueuedCard>> = _queue.asStateFlow()

    private fun restore(): List<QueuedCard> {
        val payload = store.read() ?: return emptyList()
        return try {
            val result = CardQueueJsonCodec.decode(payload)
            if (result.skippedEntries > 0) {
                reportRestoreFailure(IllegalStateException("Skipped ${result.skippedEntries} malformed queue entries"))
            }
            result.entries
        } catch (e: Exception) {
            reportRestoreFailure(e)
            emptyList()
        }
    }

    private fun reportRestoreFailure(e: Exception) {
        crashReporter?.log("scanner_queue_restore_failed: ${e::class.simpleName}")
        crashReporter?.recordException(RuntimeException("[CardQueueRepository] Queue deserialization failed", e))
    }

    private inline fun mutate(transform: (List<QueuedCard>) -> List<QueuedCard>) {
        _queue.update(transform)
        store.write(CardQueueJsonCodec.encode(_queue.value))
    }

    override fun add(entry: QueuedCard) = mutate { it + entry }

    override fun addAll(entries: List<QueuedCard>) {
        if (entries.isEmpty()) return
        mutate { it + entries }
    }

    override fun addOrMerge(entry: QueuedCard) = mutate { cards ->
        val index = cards.indexOfFirst { it.hasSameAttributesAs(entry) }
        if (index < 0) {
            cards + entry
        } else {
            cards.toMutableList().also { it[index] = it[index].copy(quantity = it[index].quantity + entry.quantity) }
        }
    }

    override fun remove(id: String) = mutate { cards -> cards.filterNot { it.id == id } }

    override fun removeAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        mutate { cards -> cards.filterNot { it.id in idSet } }
    }

    override fun removeCommitted(committed: Collection<QueuedCard>) {
        if (committed.isEmpty()) return
        val byId = committed.associateBy { it.id }
        mutate { cards ->
            cards.mapNotNull { current ->
                val snapshot = byId[current.id] ?: return@mapNotNull current
                when {
                    current == snapshot -> null
                    current.copy(quantity = snapshot.quantity) != snapshot -> current
                    current.quantity > snapshot.quantity -> current.copy(quantity = current.quantity - snapshot.quantity)
                    else -> null
                }
            }
        }
    }

    override fun removeByScryfallId(scryfallId: String) =
        mutate { cards -> cards.filterNot { it.card.scryfallId == scryfallId } }

    override fun removeByScryfallIds(scryfallIds: Collection<String>) {
        if (scryfallIds.isEmpty()) return
        val idSet = scryfallIds.toSet()
        mutate { cards -> cards.filterNot { it.card.scryfallId in idSet } }
    }

    override fun update(entry: QueuedCard) = mutate { cards ->
        cards.map { if (it.id == entry.id) entry else it }
    }

    override fun incrementQuantity(id: String) = mutate { cards ->
        cards.map { if (it.id == id) it.copy(quantity = it.quantity + 1) else it }
    }

    override fun decrementQuantity(id: String) = mutate { cards ->
        val target = cards.firstOrNull { it.id == id }
        when {
            target == null -> cards
            target.quantity <= 1 -> cards.filterNot { it.id == id }
            else -> cards.map { if (it.id == id) it.copy(quantity = it.quantity - 1) else it }
        }
    }

    override fun duplicate(entry: QueuedCard): QueuedCard {
        val copy = entry.copy(id = newQueuedCardId(), timestamp = nowMillis())
        mutate { cards ->
            val index = cards.indexOfFirst { it.id == entry.id }
            if (index < 0) cards + copy else cards.toMutableList().apply { add(index + 1, copy) }
        }
        return copy
    }

    override fun clear() = mutate { emptyList() }

    override fun replaceAll(entries: List<QueuedCard>) = mutate { entries }
}
