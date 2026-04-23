package com.mmg.manahub.core.data.remote.collection

/**
 * Contract for all Supabase operations on the `user_card_collection` table.
 *
 * Sync is driven by [updatedAt] epoch millis (Last-Write-Wins).
 * All methods return [Result] so callers can handle failures without try/catch.
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
     * Upserts a batch of rows into Supabase using the `batch_upsert_collection` RPC.
     *
     * The RPC performs an `INSERT ... ON CONFLICT (id) DO UPDATE` server-side,
     * so individual rows are idempotent. Partial failures are absorbed by the RPC.
     *
     * @param rows List of DTOs to upload.
     */
    suspend fun batchUpsert(rows: List<UserCardCollectionDto>): Result<Unit>
}
