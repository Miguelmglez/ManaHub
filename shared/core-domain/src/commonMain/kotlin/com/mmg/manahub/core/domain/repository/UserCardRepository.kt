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
 * Result of [UserCardRepository.updateEntryWithMerge] / [WishlistRepository.updateEntryWithMerge],
 * describing whether the target entry actually existed to update. Edge-case audit A2
 * (2026-07-15): previously a missing `entryId` (deleted concurrently by another device via sync,
 * or a stale UI reference) silently no-op'd and the caller still received a success result — the
 * ViewModel then told the user "Entry updated" even though nothing changed. Callers MUST branch on
 * this to show an honest error on [ENTRY_NOT_FOUND] instead of a false-positive success toast.
 */
enum class UpdateEntryOutcome {
    /** The entry was found and updated (in place, merged into a survivor, or revived). */
    UPDATED,

    /** [entryId] no longer existed — no write occurred. */
    ENTRY_NOT_FOUND,
}

/** One row of [UserCardRepository.addOrIncrementBatch]. */
data class CollectionAddRequest(
    val scryfallId: String,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Int,
)

/**
 * One card line moved by a trade, applied through [UserCardRepository.applyTradeCollectionChanges].
 *
 * @property userCardIdRef the caller's OWN collection row the copies came from, or null when unknown
 *   (the row is then matched by the attribute tuple). Never pass the counterparty's row id.
 */
data class TradeCollectionLine(
    val scryfallId: String,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Int,
    val userCardIdRef: String? = null,
)

/**
 * Outcome of [UserCardRepository.applyTradeCollectionChanges].
 *
 * @property remoteOfferRemovals collection row ids whose already-synced open-for-trade offer was
 *   deleted locally and must still be removed remotely, AFTER the local commit.
 * @property refFallbackCount deductions whose [TradeCollectionLine.userCardIdRef] no longer matched
 *   the traded variant and fell back to attribute matching.
 * @property unmatchedDeductionCount deductions that matched no live collection row at all.
 */
data class TradeCollectionApplyResult(
    val remoteOfferRemovals: List<String> = emptyList(),
    val refFallbackCount: Int = 0,
    val unmatchedDeductionCount: Int = 0,
)

/**
 * Outcome of [UserCardRepository.decrementById].
 *
 * @property rowId the collection row that was decremented, or null when no live row matched.
 * @property remainingQuantity copies left on that row (0 means it was soft-deleted); null when unknown.
 * @property usedAttributeFallback true when the id did not identify the expected live variant and the
 *   row was resolved by its attribute tuple instead.
 */
data class CollectionDecrementResult(
    val rowId: String?,
    val remainingQuantity: Int?,
    val usedAttributeFallback: Boolean,
)

/**
 * Contract for all collection (user card) persistence operations.
 *
 * Sync is NOT part of this interface. The `SyncManager` owns the push/pull cycle. This repository
 * is responsible only for local CRUD and exposing observable streams to the UI layer.
 *
 * This is the platform-agnostic surface (KMP `commonMain`) — free of `androidx.paging`/Room so it
 * can be implemented by both the Android (Room-backed) repository and a future web data source. An
 * Android-only `CollectionPagerSource`/`CollectionRemoteMediator` paging surface used to sit
 * alongside this interface; it was deleted (collection sync data-loss fix, write-path hardening
 * audit, Phase 7) as dead, unreferenced code that also reintroduced the exact tuple-collision and
 * offset-pagination-vs-mutable-order hazards `SyncManager.pullCollectionRow`'s LWW/keyset-cursor
 * logic exists to avoid — do not re-add an offset-paginated Supabase reader for this table.
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

    /**
     * Emits the [limit] most-recently-added (non-deleted) collection rows, newest first.
     * Backs the Home dashboard's Recently Added widget (Home feature overhaul Phase 2.1).
     */
    fun observeRecentlyAdded(limit: Int): Flow<List<UserCardWithCard>>

    /**
     * Card Versions & Languages, Phase 1A. Emits every non-deleted collection row for ANY
     * printing/language that shares the same oracle identity as [oracleId] (falling back to an
     * exact [name] match when [oracleId] is blank — see [com.mmg.manahub.core.model.Card.oracleId]).
     * Feeds CardDetail's "your other copies" section (Phase 1B).
     *
     * @param userId when non-null, scopes to that user's rows (plus guest/NULL-owned rows,
     *   matching [observeByScryfallId]'s convention); when null, follows the current session.
     */
    fun observeVersionsByOracle(oracleId: String, name: String, userId: String?): Flow<List<UserCardWithCard>>

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
     * [addOrIncrement] for every entry of [entries] as ONE atomic write (a single Room transaction
     * on Android), so a bulk add invalidates collection observers once instead of per row.
     *
     * @return one [AddOutcome] per entry, in order. Throws when the batch failed; nothing is written.
     */
    suspend fun addOrIncrementBatch(entries: List<CollectionAddRequest>, userId: String?): List<AddOutcome> =
        entries.map {
            addOrIncrement(it.scryfallId, it.isFoil, it.condition, it.language, isForTrade = false, userId, it.quantity)
        }

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

    /**
     * Removes [quantity] copies from the collection row identified by [id], soft-deleting the row
     * only when nothing is left. Unlike [deleteCard], a partial trade never drops the whole row.
     *
     * When [id] is missing, deleted, owned by another user, or no longer holds the expected variant
     * ([expectedScryfallId]/[isFoil]/[condition]/[language]), the live row with that attribute tuple
     * is decremented instead and [CollectionDecrementResult.usedAttributeFallback] is set.
     */
    suspend fun decrementById(
        id: String,
        quantity: Int,
        expectedScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        userId: String,
    ): CollectionDecrementResult {
        decrementOrRemove(userId, expectedScryfallId, isFoil, condition, language, quantity)
        return CollectionDecrementResult(rowId = null, remainingQuantity = null, usedAttributeFallback = true)
    }

    /**
     * Applies a trade's collection effect: every [deductions] line is removed (by its own row id
     * when given, see [decrementById]) and every [additions] line is added via the [addOrIncrement]
     * rules. Open-for-trade offers never exceed the copies left on their row: an offer is deleted when
     * its row reaches zero and trimmed otherwise.
     *
     * [shouldApply] is evaluated first and [onApplied] last, both inside the same atomic write as the
     * collection changes (one Room transaction on Android), so an idempotency gate and its completion
     * marker can never disagree with the collection. No network call happens here.
     *
     * @return null when [shouldApply] returned false (nothing was written); throws when the write
     *   failed, in which case nothing was written either.
     */
    suspend fun applyTradeCollectionChanges(
        userId: String,
        deductions: List<TradeCollectionLine>,
        additions: List<TradeCollectionLine>,
        shouldApply: suspend () -> Boolean,
        onApplied: suspend () -> Unit,
    ): TradeCollectionApplyResult? {
        if (!shouldApply()) return null
        deductions.forEach {
            decrementOrRemove(userId, it.scryfallId, it.isFoil, it.condition, it.language, it.quantity)
        }
        additions.forEach {
            addOrIncrement(it.scryfallId, it.isFoil, it.condition, it.language, isForTrade = false, userId, it.quantity)
        }
        onApplied()
        return TradeCollectionApplyResult()
    }

    /**
     * Card Versions & Languages, Phase 1A. Re-points the collection entry identified by [entryId]
     * to a different printing/language/foil/condition, atomically merging into an already-existing
     * row at the target attribute tuple `(userId, newScryfallId, isFoil, condition, language)` when
     * one exists, instead of violating [com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity]'s
     * unique index.
     *
     * Merge semantics (mirrors [addOrIncrement]'s reuse-soft-deleted-row convention):
     * - A LIVE (non-deleted) row already at the target tuple → its quantity is incremented by
     *   [quantity] and [entryId]'s row is soft-deleted. Any open-for-trade offer linked to
     *   [entryId] is re-pointed onto the survivor, merging offered quantities and capping at the
     *   survivor's new total quantity.
     * - A SOFT-DELETED row already at the target tuple → it is restored (undeleted) with
     *   [quantity] as its new quantity (same "reuse, don't duplicate the unique key" convention as
     *   [addOrIncrement]'s restore branch) and [entryId]'s row is soft-deleted.
     * - No row at the target tuple (or the target tuple IS [entryId]'s own current tuple) →
     *   [entryId]'s row is updated in place with the new attributes.
     *
     * In every branch `updated_at` is bumped on every row touched so the LWW sync push picks up
     * the change. Implementations MUST perform this atomically (a single database transaction
     * spanning both the collection row and any linked open-for-trade row) so a process death
     * mid-edit can never leave a duplicate row or an orphaned open-for-trade offer.
     *
     * @return [UpdateEntryOutcome.ENTRY_NOT_FOUND] when [entryId] no longer exists (no write
     *   occurred) — see [UpdateEntryOutcome]'s KDoc for why callers must branch on this.
     */
    suspend fun updateEntryWithMerge(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        userId: String?,
    ): UpdateEntryOutcome
}
