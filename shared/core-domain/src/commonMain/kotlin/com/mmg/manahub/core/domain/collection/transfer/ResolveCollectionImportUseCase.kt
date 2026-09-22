package com.mmg.manahub.core.domain.collection.transfer

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardLookupIdentifier
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.QueuedCard
import kotlinx.datetime.Clock

/** Outcome of [ResolveCollectionImportUseCase]. */
sealed interface CollectionImportResolution {

    /**
     * @property entries queue-ready cards, identical printings + attributes merged.
     * @property unresolvedLines source lines no card could be found for, in input order.
     * @property clampedCopies copies dropped by the per-row quantity cap, parsing and merging
     *   combined; surfaced to the user so the cap is never silent.
     */
    data class Resolved(
        val entries: List<QueuedCard>,
        val unresolvedLines: List<String>,
        val resolvedLineCount: Int,
        val clampedCopies: Int = 0,
    ) : CollectionImportResolution

    /** Scryfall's shared cooldown is active; nothing was resolved. */
    data class RateLimited(val retryAfterMs: Long) : CollectionImportResolution

    /** A batch lookup failed for a non rate-limit reason (network, parse). */
    data object Failed : CollectionImportResolution
}

/**
 * Resolves parsed import lines to cards through batched Scryfall `/cards/collection` lookups
 * ([CardRepository.MAX_IDENTIFIERS_PER_LOOKUP] identifiers per call) instead of one fuzzy search
 * per line. Identifier priority: Scryfall id > set + collector number > name + set > name.
 *
 * Lines the batch reported back as `not_found` fall back to a fuzzy name search, at most
 * [MAX_NAME_FALLBACKS] distinct names per import, before being reported unresolved. An identifier
 * that Scryfall answered but [CardIndex] could not map back also falls back, and is counted so the
 * gap between "not found" and "not matched" stays visible.
 *
 * CPU-bound for thousands of lines and deliberately dispatcher-free (commonMain must stay honest on
 * wasm): the caller runs it off its own thread and throttles [invoke]'s `onProgress` callback.
 */
class ResolveCollectionImportUseCase(
    private val cardRepository: CardRepository,
    private val crashReporter: CrashReporter? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend operator fun invoke(
        parsed: ParsedCollectionImport,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): CollectionImportResolution {
        val lines = parsed.lines
        val total = lines.size
        if (total == 0) {
            return CollectionImportResolution.Resolved(
                entries = emptyList(),
                unresolvedLines = parsed.rejectedLines,
                resolvedLineCount = 0,
                clampedCopies = parsed.clampedCopies,
            )
        }

        val identifierByLine = lines.map { identifierFor(it) }
        val uniqueIdentifiers = identifierByLine.distinctBy { it.key() }
        val cardByIdentifier = HashMap<String, Card>()
        val linesByKey = identifierByLine.groupingBy { it.key() }.eachCount()
        val notFoundKeys = HashSet<String>()
        var unmatchedResponses = 0
        var processed = 0

        for (chunk in uniqueIdentifiers.chunked(CardRepository.MAX_IDENTIFIERS_PER_LOOKUP)) {
            when (val result = cardRepository.lookupCardsByIdentifiers(chunk)) {
                is DataResult.Error -> return failure(result.message, total)
                is DataResult.Success -> {
                    result.data.notFound.forEach { notFoundKeys += it.key() }
                    val index = CardIndex(result.data.cards)
                    chunk.forEach { identifier ->
                        val card = index.match(identifier)
                        if (card != null) {
                            cardByIdentifier[identifier.key()] = card
                            processed += linesByKey[identifier.key()] ?: 0
                        } else if (identifier.key() !in notFoundKeys) {
                            // Scryfall returned something this identifier asked for but the index
                            // could not map it back; it still falls back, but it is worth knowing.
                            unmatchedResponses++
                        }
                    }
                }
            }
            onProgress(processed, total)
        }
        if (unmatchedResponses > 0) crashReporter?.log("collection_import_unmatched_response")

        val fallbackByName = HashMap<String, Card?>()
        var fallbackBudget = MAX_NAME_FALLBACKS
        var fallbackRateLimited = false
        val resolvedCards = arrayOfNulls<Card>(total)
        lines.forEachIndexed { i, line ->
            val identifier = identifierByLine[i]
            val batched = cardByIdentifier[identifier.key()]
            if (batched != null) {
                resolvedCards[i] = batched
                return@forEachIndexed
            }
            val name = line.name?.trim().orEmpty()
            val nameKey = name.lowercase()
            if (name.isNotEmpty() && !fallbackRateLimited) {
                val cached = fallbackByName[nameKey]
                resolvedCards[i] = when {
                    nameKey in fallbackByName -> cached
                    fallbackBudget > 0 -> {
                        fallbackBudget--
                        when (val search = cardRepository.searchCardByName(name)) {
                            is DataResult.Success -> search.data
                            is DataResult.Error -> {
                                if (search.message.startsWith(RATE_LIMIT_SENTINEL)) fallbackRateLimited = true
                                null
                            }
                        }.also { fallbackByName[nameKey] = it }
                    }
                    else -> null
                }
            }
            processed++
            onProgress(processed.coerceAtMost(total), total)
        }

        val unresolved = mutableListOf<String>()
        val entries = mutableListOf<QueuedCard>()
        val entryIndexByKey = HashMap<String, Int>()
        val timestamp = nowMillis()
        var clampedCopies = parsed.clampedCopies
        lines.forEachIndexed { i, line ->
            val card = resolvedCards[i]
            if (card == null) {
                unresolved += line.rawLine
            } else {
                clampedCopies += entries.mergeOrAdd(
                    entryIndexByKey,
                    QueuedCard(
                        card = card,
                        quantity = line.quantity,
                        isFoil = line.isFoil,
                        language = line.language,
                        condition = line.condition,
                        setCode = card.setCode,
                        timestamp = timestamp,
                    )
                )
            }
        }
        onProgress(total, total)

        val unresolvedCount = unresolved.size
        if (unresolvedCount > total / 2) {
            crashReporter?.log("collection_import_high_failure_rate")
        }
        if (fallbackRateLimited) crashReporter?.log("collection_import_fallback_rate_limited")

        return CollectionImportResolution.Resolved(
            entries = entries,
            unresolvedLines = parsed.rejectedLines + unresolved,
            resolvedLineCount = total - unresolvedCount,
            clampedCopies = clampedCopies,
        )
    }

    private fun failure(message: String, total: Int): CollectionImportResolution {
        if (message.startsWith(RATE_LIMIT_SENTINEL)) {
            val retryAfter = message.removePrefix(RATE_LIMIT_SENTINEL).toLongOrNull() ?: 0L
            return CollectionImportResolution.RateLimited(retryAfter.coerceAtLeast(0L))
        }
        crashReporter?.log("collection_import_lookup_failed")
        crashReporter?.setCustomKey("collection_import_lines_bucket", transferCountBucket(total))
        return CollectionImportResolution.Failed
    }

    /** Returns the copies the per-row quantity cap had to drop. */
    private fun MutableList<QueuedCard>.mergeOrAdd(indexByKey: MutableMap<String, Int>, entry: QueuedCard): Int {
        val key = "${entry.card.scryfallId}|${entry.isFoil}|${entry.language}|${entry.condition}"
        val index = indexByKey[key]
        if (index == null) {
            indexByKey[key] = size
            add(entry)
            return 0
        }
        val wanted = this[index].quantity.toLong() + entry.quantity
        val capped = wanted.coerceAtMost(CollectionImportParser.MAX_QUANTITY_PER_LINE.toLong())
        this[index] = this[index].copy(quantity = capped.toInt())
        return (wanted - capped).toInt()
    }

    /** Matches a returned card back to the identifier that asked for it. */
    private class CardIndex(cards: List<Card>) {
        private val byId = cards.associateBy { it.scryfallId.lowercase() }
        private val byPrinting = cards.associateBy { printingKey(it.setCode, it.collectorNumber) }
        private val byName: Map<String, List<Card>> = buildMap<String, MutableList<Card>> {
            cards.forEach { card ->
                nameKeys(card.name).forEach { key -> getOrPut(key) { mutableListOf() } += card }
            }
        }

        fun match(identifier: CardLookupIdentifier): Card? {
            identifier.scryfallId?.let { return byId[it.lowercase()] }
            val set = identifier.setCode
            val number = identifier.collectorNumber
            if (set != null && number != null) return byPrinting[printingKey(set, number)]
            val candidates = identifier.name?.let { byName[it.trim().lowercase()] } ?: return null
            return if (set != null) candidates.firstOrNull { it.setCode.equals(set, ignoreCase = true) }
            else candidates.firstOrNull()
        }

        private fun nameKeys(name: String): List<String> {
            val full = name.lowercase()
            val faces = full.split(" // ")
            return if (faces.size > 1) listOf(full) + faces else listOf(full)
        }
    }

    companion object {
        const val MAX_NAME_FALLBACKS = 100

        // Stable prefix of core-data's RateLimitExhaustedException message (never renamed).
        private const val RATE_LIMIT_SENTINEL = "RATE_LIMIT_EXHAUSTED:"

        private fun printingKey(setCode: String, collectorNumber: String) =
            "${setCode.lowercase()}|${collectorNumber.lowercase()}"

        /** Builds the highest-priority identifier the line supports. */
        internal fun identifierFor(line: CollectionImportLine): CardLookupIdentifier = when {
            line.scryfallId != null -> CardLookupIdentifier(scryfallId = line.scryfallId)
            line.setCode != null && line.collectorNumber != null ->
                CardLookupIdentifier(setCode = line.setCode, collectorNumber = line.collectorNumber)
            line.setCode != null -> CardLookupIdentifier(name = line.name, setCode = line.setCode)
            else -> CardLookupIdentifier(name = line.name)
        }

        private fun CardLookupIdentifier.key(): String =
            "${scryfallId.orEmpty()}|${name?.lowercase().orEmpty()}|${setCode?.lowercase().orEmpty()}|" +
                collectorNumber?.lowercase().orEmpty()
    }
}
