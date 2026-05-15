package com.mmg.manahub.feature.friends.data.repository

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
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.model.FriendStats
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import retrofit2.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FriendRepositoryImpl @Inject constructor(
    private val dao: FriendDao,
    private val remote: FriendRemoteDataSource,
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
                refreshFriends(currentUserId)
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
                    nickname = it.nickname ?: it.id,
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
    ): Result<List<FriendCard>> = runCatching {
        remote.getFriendCollection(friendUserId, list, query).map { dto ->
            FriendCard(
                sourceList = dto.sourceList,
                scryfallId = dto.scryfallId,
                name = dto.cardName,
                imageNormal = dto.imageNormal,
                setName = dto.setName,
                rarity = dto.rarity,
                priceEur = dto.priceEur,
                priceUsd = dto.priceUsd,
                quantity = dto.quantity,
                isFoil = dto.isFoil,
                condition = dto.condition,
                language = dto.language,
            )
        }
    }

    override suspend fun getFriendStats(friendUserId: String): Result<FriendStats?> =
        runCatching {
            remote.getFriendStats(friendUserId)?.toDomain()
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
