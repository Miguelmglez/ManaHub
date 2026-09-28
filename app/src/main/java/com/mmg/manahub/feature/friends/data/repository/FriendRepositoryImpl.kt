package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.local.entity.FriendEntity
import com.mmg.manahub.core.data.local.entity.FriendRequestEntity
import com.mmg.manahub.core.data.local.entity.OutgoingFriendRequestEntity
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.FriendRequestWithProfile
import com.mmg.manahub.core.data.remote.FriendWithProfile
import com.mmg.manahub.core.data.remote.OutgoingRequestWithProfile
import com.mmg.manahub.core.data.remote.UNKNOWN_DISPLAY_NAME
import com.mmg.manahub.core.data.remote.orNullIfBlank
import com.mmg.manahub.core.model.AcceptInviteResult
import com.mmg.manahub.core.model.FolderFilters
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardPage
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.withMetadata
import com.mmg.manahub.core.model.FriendMatchHistory
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.FriendStats
import com.mmg.manahub.core.model.FriendshipGoneException
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.core.domain.repository.FriendRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FriendRepositoryImpl(
    private val dao: FriendDao,
    private val remote: FriendRemoteDataSource,
    private val cardRepo: CardRepository,
    private val progressionEventBus: ProgressionEventBus,
    private val crashReporter: CrashReporter,
    private val cacheOwner: FriendsCacheOwnerStore,
    /** The signed-in account right now; a refresh for any other account never touches the cache. */
    private val activeUserId: () -> String?,
    private val activeSessionFlow: Flow<String?> = flowOf(activeUserId()),
) : FriendRepository {

    // Serializes cache writes with owner claims and clears, so a stale account's refresh can't land after a switch.
    private val cacheLock = Mutex()

    override fun observeFriends(): Flow<List<Friend>> =
        scopedRows(dao.observeFriends()).map { list -> list.map { it.toDomain() } }

    override fun observePendingRequests(): Flow<List<FriendRequest>> =
        scopedRows(dao.observePendingRequests()).map { list -> list.map { it.toDomain() } }

    override fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequest>> =
        scopedRows(dao.observeOutgoingRequests()).map { list -> list.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> = scopedCount(dao.observePendingCount())

    override fun observeFriendCount(): Flow<Int> = scopedCount(dao.observeFriendCount())

    private fun <T> scopedRows(rows: Flow<List<T>>): Flow<List<T>> =
        combine(rows, activeSessionFlow) { list, userId ->
            if (userId != null && userId == activeUserId() && cacheOwner.get() == userId) list else emptyList()
        }

    private fun scopedCount(count: Flow<Int>): Flow<Int> =
        combine(count, activeSessionFlow) { value, userId ->
            if (userId != null && userId == activeUserId() && cacheOwner.get() == userId) value else 0
        }

    override suspend fun refreshFriends(currentUserId: String): Result<Unit> =
        refreshFriends(currentUserId, alreadyEmitted = emptySet())

    /**
     * Replaces the friends cache and emits [ProgressionEvent.FriendAdded] for each friend that was not
     * cached before (restore plan D6: the requester learns of an acceptance here). [alreadyEmitted]
     * user ids were just rewarded by the caller.
     */
    private suspend fun refreshFriends(currentUserId: String, alreadyEmitted: Set<String>): Result<Unit> =
        remote.getFriends(currentUserId).map { friends ->
            applySnapshot(currentUserId, friends, incoming = null, outgoing = null, alreadyEmitted = alreadyEmitted)
        }

    override suspend fun refreshRequests(currentUserId: String): Result<Unit> =
        remote.getPendingRequests(currentUserId).map { requests ->
            applySnapshot(currentUserId, friends = null, incoming = requests, outgoing = null)
        }

    override suspend fun refreshOutgoingRequests(currentUserId: String): Result<Unit> =
        remote.getOutgoingRequests(currentUserId).map { requests ->
            applySnapshot(currentUserId, friends = null, incoming = null, outgoing = requests)
        }

    override suspend fun refreshAll(currentUserId: String): Result<Unit> {
        val (friends, incoming, outgoing) = coroutineScope {
            val friends = async { remote.getFriends(currentUserId) }
            val incoming = async { remote.getPendingRequests(currentUserId) }
            val outgoing = async { remote.getOutgoingRequests(currentUserId) }
            Triple(friends.await(), incoming.await(), outgoing.await())
        }
        applySnapshot(currentUserId, friends.getOrNull(), incoming.getOrNull(), outgoing.getOrNull())
        val failure = listOf(friends, incoming, outgoing).firstNotNullOfOrNull { it.exceptionOrNull() }
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    override suspend fun claimLocalCache(userId: String) {
        cacheLock.withLock { claimLocked(userId) }
    }

    override suspend fun clearLocalCache() {
        cacheLock.withLock {
            dao.clearAll()
            cacheOwner.set(null)
        }
    }

    /** Writes the non-null lists atomically for [currentUserId], then rewards newly seen friends. */
    private suspend fun applySnapshot(
        currentUserId: String,
        friends: List<FriendWithProfile>?,
        incoming: List<FriendRequestWithProfile>?,
        outgoing: List<OutgoingRequestWithProfile>?,
        alreadyEmitted: Set<String> = emptySet(),
    ) {
        if (friends == null && incoming == null && outgoing == null) return
        val newFriendIds: List<String> = cacheLock.withLock {
            if (activeUserId() != currentUserId) {
                crashReporter.log("friends_cache_write_skipped_inactive_account")
                return
            }
            claimLocked(currentUserId)
            val previous = if (friends != null) dao.getFriendUserIds().toSet() else emptySet()
            dao.replaceAll(
                friends = friends?.map { it.toEntity() },
                incoming = incoming?.map { it.toEntity() },
                outgoing = outgoing?.map { it.toEntity() },
            )
            friends.orEmpty()
                .map { it.friendUserId }
                .filter { it !in previous && it !in alreadyEmitted }
                .distinct()
        }
        newFriendIds.forEach { emitFriendAdded(currentUserId, it) }
    }

    // A cache with no owner may predate owner tracking and hold another account's rows, so it is wiped too.
    private suspend fun claimLocked(userId: String) {
        if (cacheOwner.get() == userId) return
        dao.clearAll()
        cacheOwner.set(userId)
    }

    private suspend fun emitFriendAdded(currentUserId: String, otherUserId: String) {
        cacheLock.withLock {
            if (activeUserId() != currentUserId || cacheOwner.get() != currentUserId) return
            progressionEventBus.emit(ProgressionEvent.FriendAdded(friendId = otherUserId, occurredAt = Clock.System.now()))
        }
    }

    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        remote.sendFriendRequest(fromUserId, toUserId)

    override suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit> {
        if (activeUserId() != currentUserId) return Result.failure(IllegalStateException("Account changed"))
        val accepted = remote.acceptRequestReturning(friendshipId).getOrElse { error ->
            // The request is gone server-side: reconcile the list instead of deleting or rewarding locally.
            if (error is FriendshipGoneException && activeUserId() == currentUserId) refreshRequests(currentUserId)
            return Result.failure(error)
        }
        if (activeUserId() != currentUserId) return Result.failure(IllegalStateException("Account changed"))
        val otherUserId = if (accepted.userId1 == currentUserId) accepted.userId2 else accepted.userId1
        cacheLock.withLock {
            if (activeUserId() != currentUserId || cacheOwner.get() != currentUserId) {
                return Result.failure(IllegalStateException("Account changed"))
            }
            dao.deleteRequest(friendshipId)
        }
        // A failed refresh leaves the new friend missing until the next refresh, so retry once and report.
        refreshFriends(currentUserId, setOf(otherUserId)).onFailure { firstError ->
            crashReporter.log("friends_accept_refresh_failed_retrying")
            crashReporter.recordException(RuntimeException("[friends_accept_refresh_failed] ${firstError::class.simpleName}"))
            refreshFriends(currentUserId, setOf(otherUserId)).onFailure { retryError ->
                crashReporter.log("friends_accept_refresh_retry_failed")
                crashReporter.recordException(RuntimeException("[friends_accept_refresh_failed] ${retryError::class.simpleName}"))
            }
        }
        // After the refresh so the DERIVED friend count sees the new row; the per-user key dedupes re-adds.
        emitFriendAdded(currentUserId, otherUserId)
        return Result.success(Unit)
    }

    override suspend fun rejectRequest(friendshipId: String): Result<Unit> {
        val userId = activeUserId() ?: return Result.failure(IllegalStateException("Not authenticated"))
        val result = remote.rejectRequest(friendshipId)
        if (result.isSuccess) cacheLock.withLock {
            if (activeUserId() == userId && cacheOwner.get() == userId) dao.deleteRequest(friendshipId)
        }
        return result
    }

    override suspend fun cancelOutgoingRequest(friendshipId: String): Result<Unit> {
        val userId = activeUserId() ?: return Result.failure(IllegalStateException("Not authenticated"))
        val result = remote.rejectRequest(friendshipId)
        if (result.isSuccess) cacheLock.withLock {
            if (activeUserId() == userId && cacheOwner.get() == userId) dao.deleteOutgoingRequest(friendshipId)
        }
        return result
    }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> {
        val userId = activeUserId() ?: return Result.failure(IllegalStateException("Not authenticated"))
        val result = remote.removeFriend(friendshipId)
        if (result.isSuccess) cacheLock.withLock {
            if (activeUserId() == userId && cacheOwner.get() == userId) dao.deleteFriend(friendshipId)
        }
        return result
    }

    override suspend fun searchByGameTag(gameTag: String): Result<Friend?> =
        remote.searchByGameTag(gameTag).map { dto ->
            dto?.let {
                Friend(
                    id = "",
                    userId = it.id,
                    // Terminal fallback is UNKNOWN_DISPLAY_NAME (never the raw auth UUID),
                    // matching FriendRemoteDataSource so the same user can't show a UUID in
                    // search yet "Unknown" in the friends list.
                    nickname = it.nickname.orNullIfBlank()
                        ?: it.gameTag.orNullIfBlank()
                        ?: UNKNOWN_DISPLAY_NAME,
                    gameTag = it.gameTag ?: "",
                    avatarUrl = it.avatarUrl,
                )
            }
        }

    override suspend fun acceptInvite(referralCode: String): Result<AcceptInviteResult> {
        val initiatingUserId = activeUserId()
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        return runCatching {
            val dto = remote.acceptInvite(referralCode)
            if (activeUserId() != initiatingUserId) throw IllegalStateException("Account changed")
            AcceptInviteResult(
                inviterId = dto.inviterId,
                inviterNickname = dto.inviterNickname,
            )
        }.onSuccess { result ->
            // accept_invite leaves an ACCEPTED friendship with the inviter; show it before rewarding it.
            if (activeUserId() == initiatingUserId) {
                refreshFriends(initiatingUserId, setOf(result.inviterId))
                    .onFailure { error ->
                        crashReporter.log("friends_invite_refresh_failed")
                        crashReporter.recordException(RuntimeException("[friends_invite_refresh_failed] ${error::class.simpleName}"))
                    }
            }
            emitFriendAdded(initiatingUserId, result.inviterId)
        }.recoverCatching { throwable ->
            if (throwable is CancellationException) throw throwable
            // Extract only the known semantic token from the Supabase error body, never the
            // raw PostgreSQL message, to avoid leaking internal schema details.
            // Ktor's ResponseException.message includes the response body text,
            // so we can extract the semantic token from it regardless of exception type.
            val rawBody = throwable.message ?: ""
            val token = when {
                rawBody.contains("SELF_INVITE", ignoreCase = true) -> "SELF_INVITE"
                rawBody.contains("INVALID_CODE", ignoreCase = true) -> "INVALID_CODE"
                rawBody.contains("NOT_AUTHENTICATED", ignoreCase = true) -> "NOT_AUTHENTICATED"
                else -> "UNKNOWN_ERROR"
            }
            throw Exception(token)
        }.let { result ->
            if (activeUserId() == initiatingUserId) result
            else Result.failure(IllegalStateException("Account changed"))
        }
    }

    override suspend fun getMyShareUrl(userId: String): Result<String> {
        val code = remote.getMyReferralCode(userId)
            ?: return Result.failure(IllegalStateException("No referral code found"))
        return Result.success("https://miguelmglez.github.io/invite/$code")
    }

    override suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
        filters: FolderFilters?,
        limit: Int,
        offset: Int,
    ): Result<List<FriendCard>> = runCatching {
        val dtos = remote.getFriendCollection(
            friendUserId = friendUserId,
            list = list,
            query = query,
            sets = filters?.sets?.takeIf { it.isNotEmpty() }?.toList(),
            rarities = filters?.rarities?.takeIf { it.isNotEmpty() }?.map { it.name }?.toList(),
            colors = filters?.colors?.takeIf { it.isNotEmpty() }?.map { it.name }?.toList(),
            foilOnly = filters?.foilOnly?.takeIf { it },
            conditions = filters?.conditions?.takeIf { it.isNotEmpty() }?.map { it.label }?.toList(),
            languages = filters?.languages?.takeIf { it.isNotEmpty() }?.toList(),
            limit = limit,
            offset = offset
        )
        // Pre-warm Room cache in one batch call instead of N sequential Scryfall fetches.
        val distinctIds = dtos.map { it.scryfallId }.distinct()
        cardRepo.warmCacheForIds(distinctIds)

        // Backend & Performance Optimization plan, WS1+WS3 Part B item 10 (2026-07-28): hydrate from
        // Room ONLY. The batched warm above was already correct, but the per-DTO enrichment used to
        // call `cardRepo.getCardById(dto.scryfallId)` — which falls back to a LIVE Scryfall fetch on
        // a cache miss — for every row, re-introducing an N+1 network call for any id
        // `warmCacheForIds` couldn't resolve (it is best-effort/failure-silent by design). A friend's
        // card list degrades gracefully by simply omitting a row Room still can't resolve after the
        // batch warm, same tolerance every other Room-only list path in this codebase already has.
        val cardsById = cardRepo.getCardsByIds(distinctIds).associateBy { it.scryfallId }

        dtos.mapNotNull { dto ->
            // The RPC returns only scryfall_id + user-specific fields. We enrich each row with card
            // metadata from the local Room cache (batch-fetched above).
            val card = cardsById[dto.scryfallId]
            if (card == null) {
                crashReporter.log(
                    "getFriendCollection: card metadata unavailable for ${dto.scryfallId} (list=$list)"
                )
                return@mapNotNull null
            }
            // Apply the optional name filter client-side.
            if (query.isNotBlank() && !card.name.contains(query, ignoreCase = true)) {
                return@mapNotNull null
            }
            FriendCard(
                sourceList = dto.sourceList,
                scryfallId = dto.scryfallId,
                name = card.name,
                typeLine = card.typeLine,
                imageNormal = card.imageNormal,
                imageArtCrop = card.imageArtCrop,
                setCode = card.setCode,
                setName = card.setName,
                rarity = card.rarity,
                priceEur = card.priceEur,
                priceUsd = card.priceUsd,
                priceEurFoil = card.priceEurFoil,
                priceUsdFoil = card.priceUsdFoil,
                quantity = dto.quantity,
                isFoil = dto.isFoil,
                isStale = card.isStale,
                condition = dto.condition,
                language = dto.language,
            )
        }
    }

    override suspend fun searchFriendCards(
        friendUserId: String,
        list: String,
        params: FriendCardSearchParams,
        cursor: FriendCardCursor?,
        limit: Int,
    ): Result<FriendCardPage> = try {
        val rows = remote.searchFriendCards(friendUserId, list, params, cursor, limit)
        val distinctIds = rows.map { it.scryfallId }.distinct()
        // Room-only: the Scryfall warm can stall for a whole 429 cooldown, so it runs later, off the page path.
        val cardsById = if (distinctIds.isEmpty()) emptyMap()
        else cardRepo.getCardsByIds(distinctIds).associateBy { it.scryfallId }

        val cards = rows.map { row ->
            val base = FriendCard(
                sourceList = row.sourceList,
                scryfallId = row.scryfallId,
                name = row.cardName.orEmpty(),
                imageNormal = null,
                imageArtCrop = null,
                setCode = row.setCode,
                setName = null,
                rarity = row.rarity,
                priceEur = null,
                priceUsd = null,
                priceEurFoil = null,
                priceUsdFoil = null,
                quantity = row.quantity,
                isFoil = row.isFoil,
                isStale = false,
                condition = row.condition,
                language = row.language,
                rowId = row.rowId,
            )
            cardsById[row.scryfallId]?.let(base::withMetadata) ?: base
        }
        val unresolved = distinctIds.filterNot { it in cardsById }.toSet()
        if (unresolved.isNotEmpty()) crashReporter.log("friend_cards_search_local_cache_miss_rows count=${unresolved.size}")

        val last = rows.lastOrNull()
        val hasMore = last?.hasMore == true
        Result.success(
            FriendCardPage(
                cards = cards,
                nextCursor = if (hasMore && last != null) FriendCardCursor(last.sortKey, last.rowId) else null,
                hasMore = hasMore,
                unindexedCount = rows.firstOrNull()?.unindexedCount ?: 0,
                unresolvedIds = unresolved,
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getFriendListUnindexedCount(friendUserId: String, list: String): Result<Int> = try {
        Result.success(remote.friendListUnindexedCount(friendUserId, list))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun hydrateFriendCardMetadata(scryfallIds: List<String>): Map<String, Card> {
        if (scryfallIds.isEmpty()) return emptyMap()
        try {
            withTimeoutOrNull(HYDRATION_WARM_TIMEOUT_MS) { cardRepo.warmCacheForIds(scryfallIds) }
                ?: crashReporter.log("friend_cards_hydration_warm_timeout count=${scryfallIds.size}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.log("friend_cards_hydration_warm_failed")
        }
        return try {
            cardRepo.getCardsByIds(scryfallIds).associateBy { it.scryfallId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.log("friend_cards_hydration_lookup_failed")
            crashReporter.setCustomKey("friend_cards_hydration_lookup_count", scryfallIds.size.toString())
            crashReporter.recordException(RuntimeException("[friend_cards_hydration_lookup_failed] ${e::class.simpleName}"))
            emptyMap()
        }
    }

    override suspend fun getFriendStats(friendUserId: String): Result<FriendStats?> =
        runCatching {
            remote.getFriendStats(friendUserId)?.toDomain()
        }

    override suspend fun getFriendMatchHistory(friendUserId: String): Result<FriendMatchHistory?> =
        runCatching {
            remote.getFriendMatchHistory(friendUserId)?.toDomain()
        }

    override suspend fun upsertMyStats(
        uniqueCards: Int,
        totalCards: Int,
        totalValueEur: Double,
        totalValueUsd: Double,
        favouriteColor: String?,
        mostValuableColor: String?,
    ): Result<Unit> =
        remote.upsertCollectionStats(
            uniqueCards = uniqueCards,
            totalCards = totalCards,
            totalValueEur = totalValueEur,
            totalValueUsd = totalValueUsd,
            favouriteColor = favouriteColor,
            mostValuableColor = mostValuableColor,
        )

    private fun com.mmg.manahub.core.data.remote.dto.FriendMatchHistoryDto.toDomain() =
        FriendMatchHistory(
            myWins = myWins,
            opponentWins = opponentWins,
            totalGames = totalGames,
            lastPlayedAt = lastPlayedAt?.let {
                runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrDefault(0L)
            } ?: 0L,
        )

    private fun com.mmg.manahub.core.data.remote.dto.FriendStatsDto.toDomain() =
        FriendStats(
            userId = userId,
            uniqueCards = uniqueCards,
            totalCards = totalCards,
            totalValueEur = totalValueEur,
            totalValueUsd = totalValueUsd,
            favouriteColor = favouriteColor,
            mostValuableColor = mostValuableColor,
            // updatedAt is an ISO-8601 string from Supabase; convert to epoch millis for the UI.
            // If parsing fails we fall back to 0L so the UI can still render the other fields.
            updatedAt = runCatching {
                Instant.parse(updatedAt).toEpochMilliseconds()
            }.getOrDefault(0L),
        )

    private fun FriendEntity.toDomain() =
        Friend(id, friendUserId, friendNickname, friendGameTag, friendAvatarUrl)

    private fun FriendRequestEntity.toDomain() =
        FriendRequest(id, fromUserId, fromNickname, fromGameTag, fromAvatarUrl)

    private fun OutgoingFriendRequestEntity.toDomain() =
        OutgoingFriendRequest(id, toUserId, toNickname, toGameTag, toAvatarUrl)

    private fun FriendWithProfile.toEntity() =
        FriendEntity(id, friendUserId, nickname, gameTag, avatarUrl)

    private fun FriendRequestWithProfile.toEntity() =
        FriendRequestEntity(id, fromUserId, fromNickname, fromGameTag, fromAvatarUrl, createdAt)

    private fun OutgoingRequestWithProfile.toEntity() =
        OutgoingFriendRequestEntity(id, toUserId, toNickname, toGameTag, toAvatarUrl, createdAt)

    private companion object {
        const val HYDRATION_WARM_TIMEOUT_MS = 15_000L
    }
}
