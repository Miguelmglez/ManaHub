package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.SuggestedTag
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    suspend fun searchCardByName(query: String): DataResult<Card>

    /**
     * Searches Scryfall for cards matching [query].
     *
     * @param bypassCache when true, skips the in-memory search cache and always re-fetches.
     *   Required for `order:random` queries where a stable cache key would otherwise return the
     *   same page on every refresh. Defaults to false (cached) for all existing callers.
     */
    suspend fun searchCards(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): DataResult<List<Card>>

    /**
     * Searches Scryfall for cards matching [query]. Returns paginated results with a [hasMore] flag.
     *
     * @param bypassCache when true, skips the in-memory search cache and always re-fetches.
     */
    suspend fun searchCardsPaginated(
        query: String,
        page: Int = 1,
        bypassCache: Boolean = false,
    ): DataResult<com.mmg.manahub.core.model.PaginatedCards>
    suspend fun getCardById(scryfallId: String): DataResult<Card>

    /**
     * Edge-case audit A3 (2026-07-15). Force-refreshes [scryfallId] from Scryfall, bypassing the
     * freshness cache check in [getCardById] — a cached row that predates the `oracleId` column
     * (blank `oracle_id`) can otherwise look "fresh" by cache age and never get a chance to pick
     * up its real oracle id, silently excluding it from the oracle-wide Collection/Wishlist/Trade
     * observers (Card Versions & Languages, Phase 1B). Used both for a targeted one-shot refresh
     * (CardDetailViewModel, when the loaded card's `oracleId` is blank) and by
     * [backfillMissingOracleIds]'s opportunistic startup pass.
     */
    suspend fun refreshCardById(scryfallId: String): DataResult<Card>

    /**
     * Edge-case audit A3 (2026-07-15). Opportunistic startup maintenance: finds up to [limit]
     * cards referenced by a LIVE collection or wishlist row whose cached row predates the
     * `oracleId` column (blank `oracle_id`), and force-refreshes each sequentially via
     * [refreshCardById] (itself funnelled through the Scryfall rate-limit queue). Best-effort and
     * failure-silent — one bad fetch must never abort the batch or block app start.
     */
    suspend fun backfillMissingOracleIds(limit: Int = 20)

    /**
     * One-time strategy-tags backfill (2026-07-22). Search-result pages no longer trigger Supabase
     * `card_strategy_tags` resolution (only opening Card Detail does), so a card cached before that
     * change -- or never opened in Card Detail -- can sit in Room with no tags forever even though
     * Collection/Deck Studio read tags purely from Room. Finds up to [limit] owned cards
     * (collection/wishlist/deck) with a populated `oracleId` that have never been resolved against
     * the precomputed table, and resolves each sequentially through the SAME path every other
     * resolution site uses. Self-terminating: a resolved card (even to zero tags) populates the
     * `card_strategy_tags_cache` table and drops out of future candidate lists -- no "done" flag
     * needed, same property as [backfillMissingOracleIds]. Must run AFTER
     * [backfillMissingOracleIds] at startup: a blank `oracleId` card can never have a precomputed
     * row, so oracle-id backfill is a prerequisite for this to find real candidates. Best-effort and
     * failure-silent per card -- one bad resolution must never abort the batch or block app start.
     */
    suspend fun backfillMissingStrategyTags(limit: Int = 40)

    /** Fetches a card by set code and collector number (returns English version by default). */
    suspend fun getCardBySetAndNumber(set: String, number: String): DataResult<Card>

    /**
     * Broken-image fix (2026-07-17). Batch-reads the ENGLISH printing already cached in Room for
     * each `(setCode, collectorNumber)` pair in [pairs] — Room ONLY, never a network fetch (must
     * stay instant for offline-safe collection-list rendering). Used to override a non-English
     * representative card's image fields with its English sibling's: many non-English Scryfall
     * printings have no native image, while the English printing of the same set + collector
     * number is guaranteed to (same illustration). A pair with no cached English row is simply
     * absent from the returned map — not an error; callers keep the representative's own image.
     */
    suspend fun getCachedEnglishSiblings(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Card>

    /** Fetches all prints (versions) of a card by its exact English name. */
    suspend fun getCardPrints(name: String): DataResult<List<Card>>

    /** Fetches all paper-printed versions (prints) of a card by its exact English name. */
    suspend fun getCardArtVariants(name: String): DataResult<List<Card>>

    /**
     * Card Versions & Languages, Phase 1A. Fetches every language printed for the exact
     * printing identified by [setCode] + [collectorNumber] (Scryfall `set:<code>
     * cn:"<number>" lang:any unique:prints`). Feeds CardDetail's language selector (Phase 1B).
     * All returned cards are upserted into the local Room cache.
     */
    suspend fun getLanguagePrints(setCode: String, collectorNumber: String): DataResult<List<Card>>

    /** Fetches a card by its exact English name (e.g. "Lightning Bolt"). */
    suspend fun getCardByExactName(name: String): Result<Card>

    /**
     * Scanner reliability plan, W2.8 (2026-08-24). Resolves the LOCALIZED printing matching
     * [name] in [lang] via Scryfall's `include_multilingual` search -- the only way to match a
     * non-English PRINTED name; [getCardByExactName]/[searchCardByName] hit `/cards/named`, which
     * is English-only. Used by the card scanner's resolution ladder so a non-English OCR name
     * resolves straight to its correct localized [Card] (with the right `scryfallId` and
     * `printedName`) instead of resolving the English print and flagging every non-English scan
     * as a mismatch. `DataResult.Error` (never a thrown exception) when nothing matches --
     * callers distinguish "not found" from a real failure the same way every other method on this
     * interface does (the `"SCRYFALL_404"` sentinel convention).
     */
    suspend fun searchCardPrintedName(name: String, lang: String): DataResult<Card>

    /**
     * Executes a raw Scryfall query string and returns matching cards.
     *
     * @param order optional Scryfall `order` param (e.g. `"edhrec"`). Null preserves the historic
     *   default (name-ASC) so existing callers (e.g. `AdvancedSearchViewModel`) stay byte-identical.
     *   Deck Builder v2 (Phase 0, root cause 1.2.1) passes `"edhrec"` so a candidate pool's first
     *   page is already popularity-ranked instead of alphabetical before any `take(n)` truncation.
     * @param page the Scryfall results page (1-based). Deck Wizard & Engine Rework plan, Workstream
     *   4.1: the Scryfall backstop fill phase pages past page 1 when a query's first page doesn't
     *   clear enough candidates above the placement fit floor. Defaults to `1` so every existing
     *   caller stays byte-identical.
     */
    suspend fun searchWithRawQuery(query: String, order: String? = null, page: Int = 1): List<Card>

    /** Fetches a list of playable Magic sets sorted by release date descending. */
    suspend fun getPlayableSets(): DataResult<List<com.mmg.manahub.core.model.MagicSet>>

    /** Batch-resolves [scryfallIds] to [Card]s from the local cache. IDs not found locally are silently skipped (no network fetch). */
    suspend fun getCardsByIds(scryfallIds: List<String>): List<Card>
    fun observeCard(scryfallId: String): Flow<Card?>

    /**
     * Batch price update (single-scryfall-id sibling: [updatePrices]).
     *
     * Note: the whole-collection, unguarded `refreshCollectionPrices()` that used to live on this
     * interface was DELETED (Backend & Performance Optimization plan, WS1+WS3 Part B item 7a,
     * 2026-07-28) — `PriceRefreshWorker` -> `RefreshCollectionPricesUseCase`
     * (`shared/core-data/.../usecase/collection/`) is now the SOLE price-refresh path.
     */
    suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
        updatedAt:    Long,
    )

    /** Batch sibling of [updatePrices] to reduce database/observer churn. */
    suspend fun updatePricesBatch(updates: List<CardPriceUpdate>)
    suspend fun evictStaleCache()

    /**
     * Replace the confirmed tag list for a card already in the local cache.
     *
     * This is a PLAIN OVERWRITE -- only safe when the caller already holds the full authoritative
     * tag list (e.g. [confirmSuggestedTag]). A caller that only knows a PARTIAL set of tags to add
     * (merged with whatever is already persisted) must use [unionCardTags] instead, which merges
     * atomically and closes the TOCTOU race two concurrent partial-merge callers would otherwise hit.
     */
    suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>)

    /**
     * Atomically merges [tags] into a card's existing confirmed-tag list (union, never a plain
     * overwrite) -- Deck Engine Unification plan RUN 7b (BUG 2 fix). Reads the CURRENT persisted
     * tags and writes back the union inside a single Room transaction, so two concurrent partial
     * enrichment jobs targeting the same card (e.g. the on-device analyzer's background resolution
     * and [com.mmg.manahub.core.domain.usecase.card.RefreshCardStrategyTagsUseCase]'s precomputed-
     * table lookup, both fired on the same cache-miss card-detail view) can never lose one
     * contribution to the other, regardless of write ordering.
     */
    suspend fun unionCardTags(scryfallId: String, tags: List<CardTag>)

    /** Replace the user-added tag list for a card. */
    suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>)

    /** Replace the suggested-tag list (used when the user dismisses suggestions). */
    suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>)

    /** Promote a suggested tag to a confirmed tag (and remove it from suggestions). */
    suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag)

    /** Drop a suggested tag without confirming it. */
    suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag)

    /**
     * Pre-warms the local Room cache for the given [scryfallIds] using a batch Scryfall fetch.
     * IDs already in Room are skipped. Called before bulk card lookups to avoid N sequential
     * network calls. Best-effort: failures are silently swallowed so callers aren't blocked.
     */
    suspend fun warmCacheForIds(scryfallIds: List<String>)
}

/** Carrier for a single card's price update. */
data class CardPriceUpdate(
    val scryfallId:   String,
    val priceUsd:     Double?,
    val priceUsdFoil: Double?,
    val priceEur:     Double?,
    val priceEurFoil: Double?,
    val updatedAt:    Long,
)
