package com.mmg.manahub.core.domain.repository

import androidx.paging.PagingData
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import com.mmg.manahub.core.domain.model.UserCard
import kotlinx.coroutines.flow.Flow
import com.mmg.manahub.core.domain.model.UserCardWithCard as DomainUserCardWithCard

/**
 * Contract for all collection (user card) persistence operations.
 *
 * Sync is NOT part of this interface. The [com.mmg.manahub.core.sync.SyncManager]
 * owns the push/pull cycle. This repository is responsible only for local CRUD
 * and exposing observable streams to the UI layer.
 */
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

interface UserCardRepository {

    // ── Observables ───────────────────────────────────────────────────────────

    /** Emits the full collection (non-deleted) ordered by most recently added. */
    fun observeCollection(): Flow<List<DomainUserCardWithCard>>

    /** Emits collection rows filtered by color identity. */
    fun observeByColor(color: String): Flow<List<DomainUserCardWithCard>>

    /** Emits collection rows filtered by rarity. */
    fun observeByRarity(rarity: String): Flow<List<DomainUserCardWithCard>>

    /** Full-text search on card name within the local collection. */
    fun searchInCollection(query: String): Flow<List<DomainUserCardWithCard>>

    /** Emits all user card rows for a specific scryfall card (all variants). */
    fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>>

    /** Total number of (non-deleted) collection entries. */
    fun observeCount(userId: String?): Flow<Int>

    /**
     * Returns a [Flow] of [PagingData] backed by [CollectionRemoteMediator].
     * The mediator loads pages from Supabase and caches them in Room automatically.
     */
    fun getCollectionPager(userId: String?): Flow<PagingData<UserCardWithCard>>

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Adds a new collection entry or increments the quantity of an existing one.
     *
     * The "existing" match is determined by the unique key:
     * (userId, scryfallId, isFoil, condition, language).
     * A new UUID is generated client-side when inserting a new row.
     *
     * The row's [updatedAt] is set to [System.currentTimeMillis] so the next
     * sync push picks it up automatically.
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
     * Also bumps [updatedAt] so the sync engine picks up the change.
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
