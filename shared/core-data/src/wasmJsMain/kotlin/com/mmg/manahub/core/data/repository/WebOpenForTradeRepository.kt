package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.dto.OpenForTradeEntryDto
import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.OpenForTradeEntry
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Web [OpenForTradeRepository] implementation (KMP web roadmap — Trades slice, Open-for-Trade
 * minimal surface). Same in-memory-cache-instead-of-Room shape as [WebWishlistRepository]/
 * [WebUserCardRepository]. `localCollectionId` on this interface is documented (Android's
 * `OpenForTradeRepositoryImpl` KDoc) as being 1:1 with `user_card_collection.id` server-side — the
 * same collection row id [WebUserCardRepository] already produces, so no separate id-mapping layer
 * is needed on web either.
 *
 * ## Deliberate web v1 scope cut
 * [observeVersionsByOracle] mirrors Android's documented A9 caveat verbatim: it does NOT dedupe
 * rows sharing the same (scryfallId, isFoil, condition, language) tuple across different collection
 * rows — safe because its only intended caller pattern (a future Card Detail "open for trade"
 * toggle) is not wired on web yet. [addAndSync]/`removeByCollectionId(AndSync)` are implemented for
 * interface completeness; the only web screen consuming this repository in this slice is a
 * read-only list view.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class WebOpenForTradeRepository(
    private val remote: OpenForTradeRemoteDataSource,
    private val cardRepository: CardRepository,
    private val supabaseClient: SupabaseClient,
) : OpenForTradeRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val addMutex = Mutex()

    private val entriesCache = MutableStateFlow<Map<String, OpenForTradeEntry>>(emptyMap())
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
            entriesCache.collect { entries -> ensureCardsResolved(entries.values.map { it.scryfallId }) }
        }
    }

    private data class GroupKey(val scryfallId: String, val isFoil: Boolean, val condition: String, val language: String)

    private fun joinedFlow(): Flow<List<OpenForTradeEntry>> =
        combine(entriesCache, cardsCache) { entries, cards ->
            entries.values
                .map { entry -> entry.copy(card = cards[entry.scryfallId]) }
                // Same de-dup-by-variant-tuple convention as Android's observeLocal (a card open
                // for trade from two different collection rows should render as one line).
                .groupBy { GroupKey(it.scryfallId, it.isFoil, it.condition, it.language) }
                .values
                .map { group -> group.minBy { it.createdAt }.copy(quantity = group.sumOf { it.quantity }) }
                .sortedByDescending { it.createdAt }
        }

    override fun observeLocal(): Flow<List<OpenForTradeEntry>> = joinedFlow()

    override fun observeByScryfallId(scryfallId: String): Flow<List<OpenForTradeEntry>> =
        joinedFlow().map { list -> list.filter { it.scryfallId == scryfallId } }

    override fun observeVersionsByOracle(oracleId: String, name: String): Flow<List<OpenForTradeEntry>> =
        combine(entriesCache, cardsCache) { entries, cards ->
            entries.values.mapNotNull { entry ->
                val card = cards[entry.scryfallId] ?: return@mapNotNull null
                val matches = if (oracleId.isNotBlank()) card.oracleId == oracleId else card.name == name
                if (matches) entry.copy(card = card) else null
            }
        }

    override fun observeUnsyncedCount(): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)

    override suspend fun addLocal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
    ): Result<Unit> = addAndSyncInternal(scryfallId, localCollectionId, quantity, isFoil, condition, language, pushRemote = false, userId = null)

    override suspend fun removeByCollectionId(localCollectionId: String): Result<Unit> = runCatching {
        entriesCache.update { current -> current.filterValues { it.userCardId != localCollectionId } }
    }

    override suspend fun removeByCollectionIdAndSync(localCollectionId: String): Result<Unit> = runCatching {
        remote.removeByUserCardId(localCollectionId).getOrThrow()
        entriesCache.update { current -> current.filterValues { it.userCardId != localCollectionId } }
    }

    override suspend fun removeLocal(id: String): Result<Unit> = runCatching {
        entriesCache.update { it - id }
    }

    override suspend fun getRemote(userId: String): Result<List<OpenForTradeEntry>> =
        remote.getOpenForTrade(userId).map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun addRemote(userCardId: String): Result<Unit> = remote.addOpenForTradeEntry(userCardId)

    override suspend fun removeRemote(id: String): Result<Unit> = remote.removeOpenForTradeEntry(id)

    override suspend fun migrateLocalToRemote(userId: String): Result<Int> = Result.success(0)

    override suspend fun addAndSync(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
        userId: String,
    ): Result<Unit> = addAndSyncInternal(scryfallId, localCollectionId, quantity, isFoil, condition, language, pushRemote = true, userId = userId)

    private suspend fun addAndSyncInternal(
        scryfallId: String,
        localCollectionId: String,
        quantity: Int,
        isFoil: Boolean,
        condition: String,
        language: String,
        pushRemote: Boolean,
        userId: String?,
    ): Result<Unit> = addMutex.withLock {
        runCatching {
            val existing = entriesCache.value.values.firstOrNull { it.userCardId == localCollectionId }
            val entry = OpenForTradeEntry(
                id = existing?.id ?: Uuid.random().toString(),
                userId = userId ?: existing?.userId ?: "",
                userCardId = localCollectionId,
                scryfallId = scryfallId,
                quantity = quantity,
                isFoil = isFoil,
                condition = condition,
                language = language,
                createdAt = existing?.createdAt ?: Clock.System.now().toEpochMilliseconds(),
            )
            if (pushRemote) {
                // localCollectionId == user_card_collection.id server-side — no lookup needed
                // (same invariant Android's OpenForTradeRepositoryImpl documents).
                remote.batchAddOpenForTradeEntries(listOf(localCollectionId)).getOrThrow()
            }
            entriesCache.update { it + (entry.id to entry) }
        }
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val dtos = remote.getOpenForTrade(userId).getOrThrow()
        entriesCache.value = dtos.associate { it.id to it.toDomain() }
    }

    private fun ensureCardsResolved(scryfallIds: Collection<String>) {
        val missing = scryfallIds.filter { it.isNotBlank() }.distinct().filterNot { cardsCache.value.containsKey(it) }
        if (missing.isEmpty()) return
        repositoryScope.launch {
            cardRepository.warmCacheForIds(missing)
            val resolved = cardRepository.getCardsByIds(missing)
            if (resolved.isNotEmpty()) cardsCache.update { it + resolved.associateBy(Card::scryfallId) }
        }
    }

    private fun OpenForTradeEntryDto.toDomain() = OpenForTradeEntry(
        id = id,
        userId = userId,
        userCardId = userCardId,
        scryfallId = scryfallId ?: "",
        quantity = 1, // Remote entries are always 1:1, matches Android's documented convention.
        isFoil = isFoil ?: false,
        condition = condition ?: "NM",
        language = language ?: "en",
        createdAt = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L),
    )
}
