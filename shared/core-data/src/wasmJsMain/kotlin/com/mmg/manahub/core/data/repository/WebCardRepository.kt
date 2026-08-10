package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardPriceUpdate
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.PaginatedCards
import com.mmg.manahub.core.model.SuggestedTag
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Web [CardRepository] implementation (web roadmap W3b) — the first web data-layer slice that
 * backs a REAL product screen (Card Search), not a showcase.
 *
 * ## Scoping (deliberate — see `docs/plans/kmp-migration-plan.md` §5 W3 / the W3b task brief)
 * The web target is explicitly ONLINE-FIRST (master plan §2.1): it does NOT replicate Android's
 * offline-first Room semantics, has no local card database, and Collection/Deck Studio/tagging have
 * not been ported to web yet. Faithfully implementing all 27 [CardRepository] methods would mean
 * building fake Room-equivalent tag/price/backfill machinery nobody asked for. This class instead
 * splits the interface in two:
 *
 *  - **Implemented for real** (13 methods) — the actual card search/lookup surface: search by
 *    name/query/raw-query, get by id/set+number, prints/art-variants/language-prints, playable
 *    sets, batch id resolution, cache warming, and a session-scoped [observeCard] flow.
 *  - **Stubbed with a loud, documented exception** (14 methods) — Room-cache-only or
 *    collection/tag-write concerns with zero web consumer today (price/tag mutation, backfills,
 *    stale-cache eviction, English-sibling Room lookups). Each throws
 *    [UnsupportedOperationException] with a message pointing back here, so an accidental future
 *    caller gets an obvious, loud signal instead of silent wrong behavior — never a quiet no-op and
 *    never a faked Room read.
 *
 * ## Networking + caching
 * Every real method delegates to [remote] (`ScryfallRemoteDataSource`, already `commonMain` — a
 * Ktor-based Scryfall client wrapped in the SAME app-wide `ScryfallRequestQueue` rate limiter and
 * `ScryfallCache` TTL/dedup cache Android uses). This repository reuses it directly rather than
 * re-implementing rate-limiting/caching against the raw `ScryfallClient`.
 *
 * On top of that, this class keeps its OWN plain in-memory, session-scoped cache
 * ([sessionCardCache], a `MutableStateFlow<Map<String, Card>>`) so [observeCard] can offer a
 * reactive [Flow] and [getCardsByIds] can serve a "local, no network fetch" batch lookup — the web
 * equivalent of Android's Room read, minus persistence across reload. This is intentionally NOT
 * backed by `KeyValueStore`/`localStorage` — a full card cache doesn't belong in a ~5-10MB
 * string-only store (see `project_w3_repo_slice_pattern` memory).
 */
class WebCardRepository(
    private val remote: ScryfallRemoteDataSource,
) : CardRepository {

    /** Session-scoped, in-memory only — cleared on page reload. Never persisted. */
    private val sessionCardCache = MutableStateFlow<Map<String, Card>>(emptyMap())

    private fun cacheCard(card: Card) {
        sessionCardCache.update { it + (card.scryfallId to card) }
    }

    private fun cacheCards(cards: List<Card>) {
        if (cards.isEmpty()) return
        sessionCardCache.update { it + cards.associateBy(Card::scryfallId) }
    }

    // ── Implemented for real ─────────────────────────────────────────────────────────────────

    override suspend fun searchCardByName(query: String): DataResult<Card> =
        remote.searchCardByName(query).toDataResult(onSuccess = ::cacheCard)

    override suspend fun searchCards(query: String, page: Int, bypassCache: Boolean): DataResult<List<Card>> =
        remote.searchCards(query, page, bypassCache).toDataResult(onSuccess = ::cacheCards)

    override suspend fun searchCardsPaginated(
        query: String,
        page: Int,
        bypassCache: Boolean,
    ): DataResult<PaginatedCards> =
        remote.searchCardsPaginated(query, page, bypassCache)
            .toDataResult(onSuccess = { cacheCards(it.cards) })

    override suspend fun getCardById(scryfallId: String): DataResult<Card> =
        remote.getCardById(scryfallId).toDataResult(onSuccess = ::cacheCard)

    override suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card> =
        remote.getCardBySetAndNumber(set, number).toDataResult(onSuccess = ::cacheCard)

    override suspend fun getCardPrints(name: String): DataResult<List<Card>> {
        val safeName = name.replace("\"", "").replace("\\", "").trim()
        val query = "!\"$safeName\" unique:prints"
        return remote.searchCards(query, page = 1).toDataResult(onSuccess = ::cacheCards)
    }

    override suspend fun getCardArtVariants(name: String): DataResult<List<Card>> =
        remote.getCardArtVariants(name).toDataResult(onSuccess = ::cacheCards)

    override suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>> =
        remote.getLanguagePrints(setCode, collectorNumber).toDataResult(onSuccess = ::cacheCards)

    override suspend fun getCardByExactName(name: String): Result<Card> =
        remote.getCardByExactName(name).onSuccess(::cacheCard)

    override suspend fun searchWithRawQuery(query: String, order: String?, page: Int): List<Card> =
        remote.searchWithRawQuery(query, order, page).also(::cacheCards)

    override suspend fun getPlayableSets(): DataResult<List<MagicSet>> =
        runCatching { remote.getAllSets() }.toDataResult()

    /**
     * Web has no Room, so "local cache" here is [sessionCardCache] — the same session-scoped,
     * in-memory-only map every other real method in this class populates. IDs never seen this
     * session (never searched/opened/warmed) are silently skipped, matching the interface's
     * documented "no network fetch" contract exactly, just against a different backing store.
     */
    override suspend fun getCardsByIds(scryfallIds: List<String>): List<Card> {
        if (scryfallIds.isEmpty()) return emptyList()
        val cache = sessionCardCache.value
        return scryfallIds.mapNotNull { cache[it] }
    }

    /**
     * Web equivalent of Android's Room-observation-backed [CardRepository.observeCard]: since
     * there is no local database to observe, this emits from [sessionCardCache] instead — the
     * cached card if present, `null` otherwise, and a fresh emission whenever a subsequent fetch
     * (through any method on this instance) populates or updates the entry.
     */
    override fun observeCard(scryfallId: String): Flow<Card?> =
        sessionCardCache.map { it[scryfallId] }

    override suspend fun warmCacheForIds(scryfallIds: List<String>) {
        if (scryfallIds.isEmpty()) return
        val cache = sessionCardCache.value
        val missing = scryfallIds.distinct().filterNot { cache.containsKey(it) }
        if (missing.isEmpty()) return
        // Best-effort, same as Android: a batch-fetch failure never blocks callers — they fall
        // back to individual getCardById.
        remote.getCardsBatch(missing).onSuccess(::cacheCards)
    }

    // ── Stubbed: Room-cache-only / collection-tag concerns, no web consumer today ────────────
    // See the class KDoc "Scoping" section above. Each throws loudly instead of silently no-op'ing
    // or faking Room semantics — a future accidental caller gets an obvious signal, not silent
    // wrong behavior.

    override suspend fun refreshCardById(scryfallId: String): DataResult<Card> = roomOnly("refreshCardById")

    override suspend fun backfillMissingOracleIds(limit: Int): Unit = roomOnly("backfillMissingOracleIds")

    override suspend fun backfillMissingStrategyTags(limit: Int): Unit = roomOnly("backfillMissingStrategyTags")

    override suspend fun getCachedEnglishSiblings(
        pairs: Set<Pair<String, String>>,
    ): Map<Pair<String, String>, Card> = roomOnly("getCachedEnglishSiblings")

    override suspend fun updatePrices(
        scryfallId: String,
        priceUsd: Double?,
        priceUsdFoil: Double?,
        priceEur: Double?,
        priceEurFoil: Double?,
        updatedAt: Long,
    ): Unit = roomOnly("updatePrices")

    override suspend fun updatePricesBatch(updates: List<CardPriceUpdate>): Unit = roomOnly("updatePricesBatch")

    override suspend fun evictStaleCache(): Unit = roomOnly("evictStaleCache")

    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>): Unit = roomOnly("updateCardTags")

    override suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>): Unit = roomOnly("unionCardTags")

    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>): Unit =
        roomOnly("updateUserTags")

    override suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>): Unit =
        roomOnly("updateSuggestedTags")

    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag): Unit =
        roomOnly("confirmSuggestedTag")

    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag): Unit =
        roomOnly("dismissSuggestedTag")

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun <T> Result<T>.toDataResult(onSuccess: (T) -> Unit = {}): DataResult<T> =
        fold(
            onSuccess = { value -> onSuccess(value); DataResult.Success(value) },
            onFailure = { exception ->
                if (exception is ClientRequestException && exception.response.status == HttpStatusCode.NotFound) {
                    DataResult.Error("SCRYFALL_404")
                } else {
                    DataResult.Error(exception.message ?: "Unknown error")
                }
            },
        )

    /**
     * Throws a loud, self-explanatory [UnsupportedOperationException] for a stubbed method. Returns
     * [Nothing], so it type-checks as the body of any override regardless of declared return type
     * (including `Unit`-returning ones) — see the class KDoc "Scoping" section for why these methods
     * are stubs rather than silent no-ops.
     */
    private fun roomOnly(methodName: String): Nothing = throw UnsupportedOperationException(
        "$methodName is Room-cache-only; no local card database on web — see CardRepository.kt " +
            "scoping note in kmp-migration-plan.md §5 W3",
    )
}
