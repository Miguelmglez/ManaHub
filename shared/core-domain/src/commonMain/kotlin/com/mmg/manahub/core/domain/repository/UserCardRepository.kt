package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow

/**
 * Result of [UserCardRepository.addOrIncrement], describing whether the operation created a
 * brand-new unique collection row or incremented an already-present one. Used by the
 * gamification layer to weight "new unique card" XP differently from "additional copy" XP.
 */
enum class AddOutcome {
    /** A new (or restored soft-deleted) unique row was inserted. */
    CREATED_NEW,

    /** An existing row's quantity was incremented. */
    INCREMENTED_EXISTING,
}

/**
 * Contract for all collection (user card) persistence operations.
 *
 * Sync is NOT part of this interface. The `SyncManager` owns the push/pull cycle. This repository
 * is responsible only for local CRUD and exposing observable streams to the UI layer.
 *
 * This is the platform-agnostic surface (KMP `commonMain`). The Android-only `PagingData` pager
 * lives on a separate `CollectionPagerSource` interface in `:app` so this contract stays free of
 * `androidx.paging`/Room and can be implemented by both the Android (Room-backed) repository and a
 * future web data source.
 */
interface UserCardRepository {

    // ── Observables ───────────────────────────────────────────────────────────

    /** Emits the full collection (non-deleted) ordered by most recently added. */
    fun observeCollection(): Flow<List<UserCardWithCard>>

    /** Emits collection rows filtered by color identity. */
    fun observeByColor(color: String): Flow<List<UserCardWithCard>>

    /** Emits collection rows filtered by rarity. */
    fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>>

    /** Full-text search on card name within the local collection. */
    fun searchInCollection(query: String): Flow<List<UserCardWithCard>>

    /** Emits all user card rows for a specific scryfall card (all variants). */
    fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>>

    /** Total number of (non-deleted) collection entries. */
    fun observeCount(userId: String?): Flow<Int>

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Adds a new collection entry or increments the quantity of an existing one.
     *
     * The "existing" match is determined by the unique key:
     * (userId, scryfallId, isFoil, condition, language).
     * A new UUID is generated client-side when inserting a new row.
     *
     * The row's `updatedAt` is bumped so the next sync push picks it up automatically.
     *
     * @return [AddOutcome.CREATED_NEW] when a brand-new (or previously soft-deleted)
     *   unique row was created, [AddOutcome.INCREMENTED_EXISTING] when an existing
     *   row's quantity was bumped. Callers that only care about the side effect may
     *   ignore the result. This distinction lets the gamification layer split
     *   "new unique card" from "additional copy" XP without re-querying the row.
     */
    suspend fun addOrIncrement(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        isForTrade: Boolean,
        userId: String?,
        quantity: Int = 1,
    ): AddOutcome

    /**
     * Updates the trade flag and quantity for an existing row.
     * Also bumps `updatedAt` so the sync engine picks up the change.
     */
    suspend fun updateAttributes(
        id: String,
        isForTrade: Boolean,
        quantity: Int,
    )

    /**
     * Soft-deletes the row identified by [id].
     * Sets `isDeleted = true` and bumps `updatedAt`; does NOT physically remove the row.
     * The next sync push will propagate the deletion to Supabase.
     */
    suspend fun deleteCard(id: String)

    /** Returns all distinct Scryfall IDs present in the local collection. */
    suspend fun getScryfallIds(): List<String>

    /**
     * Decrements the quantity of a collection entry by [quantityToDeduct].
     *
     * The matching row is identified by the composite key:
     * (userId, scryfallId, isFoil, condition, language).
     *
     * - If no matching row exists the call is a no-op (silent skip).
     * - If the resulting quantity would be <= 0 the row is soft-deleted instead
     *   (isDeleted = true, updatedAt bumped) so the sync engine propagates the
     *   removal to Supabase on the next push cycle.
     * - Otherwise the row's quantity is updated and updatedAt is bumped.
     */
    suspend fun decrementOrRemove(
        userId: String,
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantityToDeduct: Int,
    )
}
