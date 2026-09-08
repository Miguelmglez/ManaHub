package com.mmg.manahub.core.data.remote.collection

/**
 * Contract for all Supabase operations on the `user_card_collection` table.
 *
 * Sync is driven by [updatedAt] epoch millis (Last-Write-Wins).
 * All methods return [Result] so callers can handle failures without try/catch.
 *
 * KMP web roadmap W3d (master plan §5/§6): moved to `:shared:core-data` commonMain so Android's
 * `com.mmg.manahub.core.sync.SyncManager` and the web target's `WebUserCardRepository` share the
 * exact same Supabase-calling logic — no duplicated RPC-wiring class per platform. Mirrors the
 * W3c move of `DeckRemoteDataSource`.
 */
interface CollectionRemoteDataSource {

    /**
     * Fetches all rows (including soft-deleted) that were modified after [since].
     *
     * Delegates to the `get_collection_changes_since` Supabase RPC.
     * The RPC uses `auth.uid()` server-side for RLS — no explicit user_id param needed.
     *
     * @param since Epoch millis watermark; pass 0L for a full pull.
     */
    suspend fun getChangesSince(since: Long): Result<List<UserCardCollectionDto>>

    /**
     * Keyset-paginated counterpart of [getChangesSince] (collection sync data-loss fix,
     * `linear-moseying-yeti` plan, Phase 1/3). `get_collection_changes_since` has no `LIMIT`, so
     * PostgREST silently truncates its response at `db-max-rows` (1000) with no signal to the
     * client — this is the root cause of the 2026-09 data-loss incident. Delegates to the
     * `get_collection_changes_page` RPC, which is server-capped at 500 rows/page and paginates
     * via a true keyset cursor on `(updated_at, id)` — NOT `p_since`, which stays fixed as the
     * window filter for the whole drain (see [com.mmg.manahub.core.data.sync.drainPages]).
     *
     * @param since Epoch millis watermark — the RPC's `p_since` window filter (`updated_at >
     *   since`), fixed for every page of one drain.
     * @param afterUpdatedAt Keyset cursor: the previous page's LAST row's `updatedAt`, or `null`
     *   for the first page.
     * @param afterId Keyset cursor: the previous page's LAST row's `id`, or `null` for the first
     *   page. Ties on `afterUpdatedAt` are broken by this column server-side.
     * @param limit Requested page size; the server clamps to `LEAST(limit, 500)`.
     */
    suspend fun getChangesPage(
        since: Long,
        afterUpdatedAt: Long? = null,
        afterId: String? = null,
        limit: Int = 500,
    ): Result<List<UserCardCollectionDto>>

    /**
     * Upserts a batch of rows into Supabase using the `batch_upsert_collection` RPC.
     *
     * The RPC performs an `INSERT ... ON CONFLICT (id) DO UPDATE` server-side, so individual rows
     * are idempotent. It also atomically merges a row whose (new) attribute tuple
     * `(user_id, scryfall_id, is_foil, condition, language)` collides with a DIFFERENT live row
     * for the same user (unique-violation branch: adds quantity into the live survivor instead of
     * aborting the whole batch) — this is the server-side building block
     * [com.mmg.manahub.core.data.repository.WebUserCardRepository.updateEntryWithMerge] relies on.
     * Partial failures are absorbed by the RPC.
     *
     * @param rows List of DTOs to upload.
     */
    suspend fun batchUpsert(rows: List<UserCardCollectionDto>): Result<Unit>

    /**
     * KMP web roadmap W3d. Atomically re-points a single collection entry ([entryId]) to a
     * different printing/language/foil/condition tuple, merging into an already-existing LIVE or
     * soft-deleted row at the target tuple when one exists — the server-side counterpart to
     * Android's `UserCardRepositoryImpl.updateEntryWithMerge` Room transaction. Delegates to the
     * `merge_collection_entry` Supabase RPC (backend-supabase-expert, 2026-08-03), which performs
     * the exact 3-branch semantics documented on
     * [com.mmg.manahub.core.domain.repository.UserCardRepository.updateEntryWithMerge]'s KDoc in
     * ONE server-side transaction. Returns `Result.success(false)` (never an exception) when
     * [entryId] no longer exists — callers map that to `UpdateEntryOutcome.ENTRY_NOT_FOUND`.
     *
     * Does NOT touch `open_for_trade` rows — unlike Android's Room transaction, which also
     * re-points a linked `local_open_for_trade` row in the SAME transaction, there is no web
     * consumer of Open-for-Trade yet (out of scope for this slice); a future web Trades slice must
     * extend the RPC (or add a companion one) before this repository can honor that part of the
     * interface contract.
     *
     * @return `true` when [entryId] was found and updated/merged, `false` when it no longer existed.
     */
    suspend fun mergeEntry(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
    ): Result<Boolean>

    /**
     * Calls the parameterless `get_collection_integrity()` RPC (collection sync data-loss fix,
     * Phase 6) — server-side row counts for `auth.uid()`, used by
     * [com.mmg.manahub.core.sync.SyncManager]'s post-sync integrity self-check to detect a
     * client/server row-count disagreement and trigger a full re-pull automatically.
     */
    suspend fun getIntegrity(): Result<CollectionIntegrityDto>
}
