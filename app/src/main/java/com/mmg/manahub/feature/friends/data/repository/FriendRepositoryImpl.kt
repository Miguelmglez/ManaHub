package com.mmg.manahub.feature.friends.data.repository

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.friends.data.local.entity.FriendEntity
import com.mmg.manahub.feature.friends.data.local.entity.FriendRequestEntity
import com.mmg.manahub.feature.friends.data.local.entity.OutgoingFriendRequestEntity
import com.mmg.manahub.feature.friends.data.remote.FriendRemoteDataSource
import com.mmg.manahub.feature.friends.data.remote.FriendRequestWithProfile
import com.mmg.manahub.feature.friends.data.remote.FriendWithProfile
import com.mmg.manahub.feature.friends.data.remote.OutgoingRequestWithProfile
import com.mmg.manahub.feature.friends.domain.model.AcceptInviteResult
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.model.FriendMatchHistory
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.model.FriendStats
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import retrofit2.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.friends.data.UNKNOWN_DISPLAY_NAME
import com.mmg.manahub.feature.friends.data.orNullIfBlank

class FriendRepositoryImpl @Inject constructor(
    private val dao: FriendDao,
    private val remote: FriendRemoteDataSource,
    private val cardRepo: CardRepository,
    private val progressionEventBus: ProgressionEventBus,
) : FriendRepository {

    override fun observeFriends(): Flow<List<Friend>> =
        dao.observeFriends().map { list -> list.map { it.toDomain() } }

    override fun observePendingRequests(): Flow<List<FriendRequest>> =
        dao.observePendingRequests().map { list -> list.map { it.toDomain() } }

    override fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequest>> =
        dao.observeOutgoingRequests().map { list -> list.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override fun observeFriendCount(): Flow<Int> = dao.observeFriendCount()

    override suspend fun refreshFriends(currentUserId: String): Result<Unit> =
        remote.getFriends(currentUserId).map { friends ->
            dao.clearFriends()
            dao.upsertFriends(friends.map { it.toEntity() })
        }

    override suspend fun refreshRequests(currentUserId: String): Result<Unit> =
        remote.getPendingRequests(currentUserId).map { requests ->
            dao.clearRequests()
            dao.upsertRequests(requests.map { it.toEntity() })
        }

    override suspend fun refreshOutgoingRequests(currentUserId: String): Result<Unit> =
        remote.getOutgoingRequests(currentUserId).map { requests ->
            dao.clearOutgoingRequests()
            dao.upsertOutgoingRequests(requests.map { it.toEntity() })
        }

    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        remote.sendFriendRequest(fromUserId, toUserId)

    override suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit> =
        remote.acceptRequest(friendshipId).also { result ->
            if (result.isSuccess) {
                dao.deleteRequest(friendshipId)
                // refreshFriends repopulates the local cache from the server. A silent
                // failure here leaves the just-accepted friend missing from the list until
                // the next manual refresh, so make it observable (and retry once) instead of
                // discarding the Result fire-and-forget.
                refreshFriends(currentUserId).onFailure { firstError ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("acceptRequest: refreshFriends failed after ACCEPT (friendshipId=$friendshipId), retrying once")
                        recordException(firstError)
                    }
                    refreshFriends(currentUserId).onFailure { retryError ->
                        FirebaseCrashlytics.getInstance().apply {
                            log("acceptRequest: refreshFriends retry also failed (friendshipId=$friendshipId); local friends cache may be stale")
                            recordException(retryError)
                        }
                    }
                }
                // Emit after the friendship is confirmed ACCEPTED (ADR-002 §1). The
                // friendshipId is a stable per-friendship id, so the idempotency key
                // friend:{friendshipId} dedupes retries; the weekly cap limits farming.
                progressionEventBus.emit(
                    ProgressionEvent.FriendAdded(
                        friendId = friendshipId,
                        occurredAt = Instant.now(),
                    )
                )
            }
        }

    override suspend fun rejectRequest(friendshipId: String): Result<Unit> =
        remote.rejectRequest(friendshipId).also { result ->
            if (result.isSuccess) dao.deleteRequest(friendshipId)
        }

    override suspend fun cancelOutgoingRequest(friendshipId: String): Result<Unit> =
        remote.rejectRequest(friendshipId).also { result ->
            if (result.isSuccess) dao.deleteOutgoingRequest(friendshipId)
        }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> =
        remote.removeFriend(friendshipId).also { result ->
            if (result.isSuccess) dao.deleteFriend(friendshipId)
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

    override suspend fun acceptInvite(referralCode: String): Result<AcceptInviteResult> =
        runCatching {
            val dto = remote.acceptInvite(referralCode)
            AcceptInviteResult(
                inviterId = dto.inviterId,
                inviterNickname = dto.inviterNickname,
            )
        }.recoverCatching { throwable ->
            // Extract only the known semantic token from the Supabase error body, never the
            // raw PostgreSQL message, to avoid leaking internal schema details.
            val rawBody = if (throwable is HttpException) {
                throwable.response()?.errorBody()?.string() ?: ""
            } else {
                throwable.message ?: ""
            }
            val token = when {
                rawBody.contains("SELF_INVITE", ignoreCase = true) -> "SELF_INVITE"
                rawBody.contains("INVALID_CODE", ignoreCase = true) -> "INVALID_CODE"
                rawBody.contains("NOT_AUTHENTICATED", ignoreCase = true) -> "NOT_AUTHENTICATED"
                else -> "UNKNOWN_ERROR"
            }
            throw Exception(token)
        }

    override suspend fun getMyShareUrl(userId: String): Result<String> {
        val code = remote.getMyReferralCode(userId)
            ?: return Result.failure(Exception("No referral code found for user $userId"))
        return Result.success("https://miguelmglez.github.io/invite/$code")
    }

    override suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
        filters: com.mmg.manahub.feature.friends.presentation.detail.FolderFilters?,
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
        cardRepo.warmCacheForIds(dtos.map { it.scryfallId }.distinct())

        dtos.mapNotNull { dto ->
            // The RPC returns only scryfall_id + user-specific fields. We enrich
            // each row with card metadata from the local Room cache. getCardById
            // automatically falls back to Scryfall and caches the result when the
            // card is not yet in Room (e.g. first-time viewing a friend's collection).
            val card = when (val r = cardRepo.getCardById(dto.scryfallId)) {
                is DataResult.Success -> r.data
                is DataResult.Error -> {
                    Log.w("FriendRepository", "Card metadata unavailable: ${dto.scryfallId} — ${r.message}")
                    FirebaseCrashlytics.getInstance().log(
                        "getFriendCollection: card metadata unavailable for ${dto.scryfallId} (list=$list): ${r.message}"
                    )
                    null
                }
            }
            if (card == null) return@mapNotNull null
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

    private fun com.mmg.manahub.feature.friends.data.remote.FriendMatchHistoryDto.toDomain() =
        FriendMatchHistory(
            myWins = myWins,
            opponentWins = opponentWins,
            totalGames = totalGames,
            lastPlayedAt = lastPlayedAt?.let {
                runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrDefault(0L)
            } ?: 0L,
        )

    private fun com.mmg.manahub.feature.friends.data.remote.FriendStatsDto.toDomain() =
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
                java.time.OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
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
}
