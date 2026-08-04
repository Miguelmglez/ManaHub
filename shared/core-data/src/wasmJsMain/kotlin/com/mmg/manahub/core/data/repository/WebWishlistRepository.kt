package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.WishlistEntry
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Web [WishlistRepository] implementation (KMP web roadmap — Trades slice, Wishlist minimal
 * surface). Same "two in-memory caches, no Room" shape as [WebUserCardRepository]/
 * [WebFriendRepository]: [entriesCache] stands in for Android's `LocalWishlistDao`, hydrated by
 * [SupabaseClient.auth] `sessionStatus` via [syncFromRemote] rather than a one-shot `init` fetch.
 *
 * ## Deliberate web v1 scope cut
 * Android's `WishlistRepositoryImpl.updateEntryWithMerge` is a ~130-line, 4-branch atomic merge
 * (collision-merge / in-place-quantity / in-place-attribute-reinsert / deferred-unsynced) with
 * per-branch Crashlytics breadcrumbs. This web port keeps the SAME branching decisions (merge vs.
 * in-place, quantity-only vs. full-attribute-change) but drops the Crashlytics instrumentation (no
 * web Crashlytics wiring exists yet) and the "unsynced, deferred" fallback branch — on web every
 * write is remote-first with a real userId always available from the current session (guest
 * sessions are real anonymous-auth sessions, not a true offline mode), so the "no userId to push
 * with" case Android defers for is not reachable here. This repository is currently consumed by
 * NOTHING beyond a read-only wishlist list view — `addLocal`/`updateEntryWithMerge`/
 * `migrateLocalToRemote` are implemented for interface completeness and future use, not exercised
 * by any web screen yet (Add Card / Card Detail have no "Add to wishlist" affordance on web v1).
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class WebWishlistRepository(
    private val remote: WishlistRemoteDataSource,
    private val cardRepository: CardRepository,
    private val supabaseClient: SupabaseClient,
) : WishlistRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val addMutex = Mutex()

    /** Raw entries (card = null), keyed by id — mirrors Android's Room table. */
    private val entriesCache = MutableStateFlow<Map<String, WishlistEntry>>(emptyMap())

    /** Resolved [Card] data per scryfall id, populated lazily. Session-scoped only. */
    private val cardsCache = MutableStateFlow<Map<String, Card>>(emptyMap())

    init {
        repositoryScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@collect
                    syncFromRemote(userId)
                }
            }
        }
        repositoryScope.launch {
            entriesCache.collect { entries -> ensureCardsResolved(entries.values.map { it.cardId }) }
        }
    }

    private fun joinedFlow(): Flow<List<WishlistEntry>> =
        combine(entriesCache, cardsCache) { entries, cards ->
            entries.values
                .map { entry -> entry.copy(card = cards[entry.cardId]) }
                .sortedByDescending { it.createdAt }
        }

    override fun observeLocal(): Flow<List<WishlistEntry>> = joinedFlow()

    override fun observeByScryfallId(scryfallId: String): Flow<List<WishlistEntry>> =
        joinedFlow().map { list -> list.filter { it.cardId == scryfallId } }

    override fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<WishlistEntry>> =
        joinedFlow().map { list ->
            list.filter { entry ->
                val card = entry.card ?: return@filter false
                if (oracleId.isNotBlank()) card.oracleId == oracleId else card.name == name
            }
        }

    override fun observeUnsyncedCount(): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)

    override suspend fun addLocal(entry: WishlistEntry): Result<Unit> = addMutex.withLock {
        runCatching {
            val existing = findByAttributes(entry.cardId, entry.matchAnyVariant, entry.isFoil, entry.condition, entry.language)
            val stored = existing?.copy(quantity = existing.quantity + entry.quantity)
                ?: entry.copy(id = entry.id.ifBlank { Uuid.random().toString() })
            entriesCache.update { it + (stored.id to stored) }
        }
    }

    override suspend fun removeLocal(id: String): Result<Unit> = runCatching {
        remote.removeWishlistEntry(id)
        entriesCache.update { it - id }
    }

    override suspend fun updateQuantityLocal(id: String, quantity: Int): Result<Unit> = runCatching {
        if (quantity <= 0) {
            remote.removeWishlistEntry(id)
            entriesCache.update { it - id }
        } else {
            remote.updateWishlistQuantity(id, quantity).getOrThrow()
            entriesCache.value[id]?.let { existing ->
                entriesCache.update { it + (id to existing.copy(quantity = quantity)) }
            }
        }
    }

    override suspend fun getRemote(userId: String): Result<List<WishlistEntry>> =
        remote.getWishlist(userId).map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun addRemote(entry: WishlistEntry): Result<Unit> =
        remote.addWishlistEntry(entry.toDto())

    override suspend fun removeRemote(id: String): Result<Unit> = remote.removeWishlistEntry(id)

    override suspend fun migrateLocalToRemote(userId: String): Result<Int> = Result.success(0)

    override suspend fun addAndSync(entry: WishlistEntry, userId: String): Result<Unit> = addMutex.withLock {
        runCatching {
            val existing = findByAttributes(entry.cardId, entry.matchAnyVariant, entry.isFoil, entry.condition, entry.language)
            if (existing != null) {
                val merged = existing.copy(quantity = existing.quantity + entry.quantity)
                remote.updateWishlistQuantity(existing.id, merged.quantity).getOrThrow()
                entriesCache.update { it + (merged.id to merged) }
            } else {
                val stored = entry.copy(id = entry.id.ifBlank { Uuid.random().toString() }, userId = userId)
                remote.addWishlistEntry(stored.toDto()).getOrThrow()
                entriesCache.update { it + (stored.id to stored) }
            }
        }
    }

    override suspend fun decrementByScryfallId(scryfallId: String, quantity: Int): Result<Unit> = runCatching {
        entriesCache.value.values.filter { it.cardId == scryfallId }.forEach { entry ->
            val newQty = entry.quantity - quantity
            if (newQty <= 0) {
                remote.removeWishlistEntry(entry.id).getOrThrow()
                entriesCache.update { it - entry.id }
            } else {
                remote.updateWishlistQuantity(entry.id, newQty).getOrThrow()
                entriesCache.update { it + (entry.id to entry.copy(quantity = newQty)) }
            }
        }
    }

    override suspend fun decrementByAttributes(
        scryfallId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit> = runCatching {
        val candidates = entriesCache.value.values.filter { it.cardId == scryfallId }
        if (candidates.isEmpty()) return@runCatching
        val exactMatch = candidates.firstOrNull { e ->
            e.isFoil == isFoil &&
                (e.condition == null || e.condition.equals(condition, ignoreCase = true)) &&
                (e.language == null || e.language.equals(language, ignoreCase = true))
        }
        val target = exactMatch ?: candidates.firstOrNull { it.matchAnyVariant } ?: return@runCatching
        val newQty = target.quantity - quantity
        if (newQty <= 0) {
            remote.removeWishlistEntry(target.id).getOrThrow()
            entriesCache.update { it - target.id }
        } else {
            remote.updateWishlistQuantity(target.id, newQty).getOrThrow()
            entriesCache.update { it + (target.id to target.copy(quantity = newQty)) }
        }
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val dtos = remote.getWishlist(userId).getOrThrow()
        entriesCache.value = dtos.associate { it.id to it.toDomain() }
    }

    override suspend fun updateEntryWithMerge(
        entryId: String,
        newCardId: String,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        quantity: Int,
        userId: String?,
    ): Result<UpdateEntryOutcome> = addMutex.withLock {
        runCatching {
            val edited = entriesCache.value[entryId]
                ?: return@runCatching UpdateEntryOutcome.ENTRY_NOT_FOUND

            val collision = findByAttributes(newCardId, edited.matchAnyVariant, isFoil, condition, language)
                ?.takeIf { it.id != entryId }

            if (collision != null) {
                val merged = collision.copy(quantity = collision.quantity + quantity)
                remote.updateWishlistQuantity(collision.id, merged.quantity).getOrThrow()
                remote.removeWishlistEntry(edited.id).getOrThrow()
                entriesCache.update { it + (merged.id to merged) - edited.id }
                return@runCatching UpdateEntryOutcome.UPDATED
            }

            val attributesChanged = edited.cardId != newCardId || edited.isFoil != isFoil ||
                edited.condition != condition || edited.language != language
            val updated = edited.copy(
                cardId = newCardId,
                isFoil = isFoil ?: false,
                condition = condition,
                language = language,
                quantity = quantity,
            )
            if (!attributesChanged) {
                remote.updateWishlistQuantity(edited.id, quantity).getOrThrow()
            } else {
                remote.removeWishlistEntry(edited.id).getOrThrow()
                remote.addWishlistEntry(updated.toDto()).getOrThrow()
            }
            entriesCache.update { it + (updated.id to updated) }
            UpdateEntryOutcome.UPDATED
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findByAttributes(
        cardId: String,
        matchAnyVariant: Boolean,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
    ): WishlistEntry? = entriesCache.value.values.firstOrNull { e ->
        e.cardId == cardId && e.matchAnyVariant == matchAnyVariant &&
            e.isFoil == (isFoil ?: false) && e.condition == condition && e.language == language
    }

    private fun ensureCardsResolved(scryfallIds: Collection<String>) {
        val missing = scryfallIds.distinct().filterNot { cardsCache.value.containsKey(it) }
        if (missing.isEmpty()) return
        repositoryScope.launch {
            cardRepository.warmCacheForIds(missing)
            val resolved = cardRepository.getCardsByIds(missing)
            if (resolved.isNotEmpty()) cardsCache.update { it + resolved.associateBy(Card::scryfallId) }
        }
    }

    private fun WishlistEntryDto.toDomain() = WishlistEntry(
        id = id,
        userId = userId,
        cardId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil ?: false,
        condition = condition,
        language = language,
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L),
    )

    private fun WishlistEntry.toDto() = WishlistEntryDto(
        id = id,
        userId = userId,
        cardId = cardId,
        quantity = quantity,
        matchAnyVariant = matchAnyVariant,
        isFoil = isFoil,
        condition = condition,
        language = language,
        createdAt = Instant.fromEpochMilliseconds(createdAt).toString(),
    )
}
