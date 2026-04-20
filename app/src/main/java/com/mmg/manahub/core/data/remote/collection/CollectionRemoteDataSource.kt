package com.mmg.manahub.core.data.remote.collection

import java.time.Instant

interface CollectionRemoteDataSource {
    /** Returns the timestamp of the most recently modified row for [userId], or null if none. */
    suspend fun getLastModified(userId: String): Instant?

    /** Returns all rows modified after [since] for [userId] (including soft-deleted ones). */
    suspend fun getChangesSince(userId: String, since: Instant): Result<List<UserCardCollectionDto>>

    /** Upserts a card via the `upsert_user_card` RPC. Returns the Supabase-assigned UUID. */
    suspend fun upsertCard(params: UpsertUserCardParams): Result<String>

    /** Soft-deletes a card in Supabase by setting `is_deleted = true`. */
    suspend fun softDeleteCard(remoteId: String): Result<Unit>
}
