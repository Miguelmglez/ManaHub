package com.mmg.manahub.feature.friends.data.repository

import com.mmg.manahub.feature.friends.data.local.dao.FriendDao
import com.mmg.manahub.feature.friends.data.local.entity.FriendEntity
import com.mmg.manahub.feature.friends.data.local.entity.FriendRequestEntity
import com.mmg.manahub.feature.friends.data.remote.FriendRemoteDataSource
import com.mmg.manahub.feature.friends.data.remote.FriendRequestWithProfile
import com.mmg.manahub.feature.friends.data.remote.FriendWithProfile
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
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

    private fun FriendEntity.toDomain() =
        Friend(id, friendUserId, friendNickname, friendGameTag, friendAvatarUrl)

    private fun FriendRequestEntity.toDomain() =
        FriendRequest(id, fromUserId, fromNickname, fromGameTag, fromAvatarUrl)

    private fun FriendWithProfile.toEntity() =
        FriendEntity(id, friendUserId, nickname, gameTag, avatarUrl)

    private fun FriendRequestWithProfile.toEntity() =
        FriendRequestEntity(id, fromUserId, fromNickname, fromGameTag, fromAvatarUrl, createdAt)
}
