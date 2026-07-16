package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.WishlistEntry
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeLocal(): Flow<List<WishlistEntry>>
    fun observeByScryfallId(scryfallId: String): Flow<List<WishlistEntry>>

    /**
     * Card Versions & Languages, Phase 1A. Emits every wishlist entry for ANY printing/language
     * that shares the same oracle identity as [oracleId] (falling back to an exact [name] match
     * when [oracleId] is blank — see [com.mmg.manahub.core.model.Card.oracleId]).
     */
    fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<WishlistEntry>>
    fun observeUnsyncedCount(): Flow<Int>
    suspend fun addLocal(entry: WishlistEntry): Result<Unit>
    suspend fun removeLocal(id: String): Result<Unit>
    suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit>
    suspend fun getRemote(userId: String): Result<List<WishlistEntry>>
    suspend fun addRemote(entry: WishlistEntry): Result<Unit>
    suspend fun removeRemote(id: String): Result<Unit>
    suspend fun migrateLocalToRemote(userId: String): Result<Int>

    /**
     * Inserts or increments a [WishlistEntry] in the local Room store and immediately
     * pushes it to Supabase, marking it as synced on success.
     *
     * Use this method when the user is authenticated so offline-add lag is avoided.
     *
     * @param entry The entry to add.
     * @param userId The authenticated user's UUID used for the remote payload.
     */
    suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit>

    /**
     * Decrements the quantity of every local wishlist entry for [scryfallId] by [quantity].
     * Entries whose resulting quantity falls to zero or below are deleted.
     * Used when the user receives a card via a trade and wants their wishlist updated.
     */
    suspend fun decrementByScryfallId(scryfallId: String, quantity: Int): Result<Unit>

    /**
     * Decrements the best-matching wishlist entry for the received card.
     * Priority: exact attribute match > matchAnyVariant entry > any entry for that scryfallId.
     * Entries whose resulting quantity reaches zero are deleted.
     */
    suspend fun decrementByAttributes(
        scryfallId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit>

    suspend fun syncFromRemote(userId: String): Result<Unit>

    /**
     * Card Versions & Languages, Phase 1A. Re-points the wishlist entry identified by [entryId]
     * to a different card/printing/variant, merging into an already-existing entry at the target
     * attribute tuple `(newCardId, isFoil, condition, language)` when one exists (their quantities
     * are combined and [entryId]'s row is deleted) instead of leaving two entries for the same
     * target.
     *
     * Remote-first for a row whose `synced == true` (mirrors [removeLocal]/[updateQuantityLocal]):
     * a quantity-only change pushes via a partial quantity update; an attribute change has no
     * partial-update RPC, so it removes and re-inserts the remote row under the same id — which
     * needs [userId] to rebuild the [com.mmg.manahub.core.data.remote.dto.WishlistEntryDto]. If the
     * row is synced, its attributes changed, and [userId] is null, the edit is applied locally and
     * the row is marked unsynced so the next [migrateLocalToRemote]/[addAndSync] corrects the
     * server row via its upsert-by-id (a deferred-push fallback, not silent data loss).
     *
     * @param userId only consulted when [entryId]'s row is already synced AND its
     *   card/foil/condition/language changed; omit for a quantity-only edit or a not-yet-synced row.
     * @return on success, [UpdateEntryOutcome.ENTRY_NOT_FOUND] when [entryId] no longer exists (no
     *   write occurred) — see [UpdateEntryOutcome]'s KDoc (edge-case audit A2) for why callers must
     *   branch on this instead of treating every `Result.success` as "entry updated".
     */
    suspend fun updateEntryWithMerge(
        entryId: String,
        newCardId: String,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        quantity: Int,
        userId: String? = null,
    ): Result<UpdateEntryOutcome>
}
