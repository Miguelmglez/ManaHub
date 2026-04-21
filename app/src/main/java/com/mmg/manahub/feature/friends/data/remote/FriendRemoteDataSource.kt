package com.mmg.manahub.feature.friends.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FriendRemoteDataSource @Inject constructor(
    private val service: FriendshipService,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun getFriends(currentUserId: String): Result<List<FriendWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val orFilter = "(user_id_1.eq.$currentUserId,user_id_2.eq.$currentUserId)"
                val friendships = service.getFriendships(or = orFilter)
                if (friendships.isEmpty()) return@runCatching emptyList()
                val otherIds = friendships
                    .map { if (it.userId1 == currentUserId) it.userId2 else it.userId1 }
                    .distinct()
                val profiles = service
                    .getProfilesByIds(idFilter = "in.(${otherIds.joinToString(",")})")
                    .associateBy { it.id }
                friendships.mapNotNull { fs ->
                    val otherId = if (fs.userId1 == currentUserId) fs.userId2 else fs.userId1
                    val profile = profiles[otherId] ?: return@mapNotNull null
                    FriendWithProfile(
                        id = fs.id,
                        friendUserId = otherId,
                        nickname = profile.nickname ?: otherId,
                        gameTag = profile.gameTag ?: "",
                        avatarUrl = profile.avatarUrl,
                    )
                }
            }
        }

    suspend fun getPendingRequests(currentUserId: String): Result<List<FriendRequestWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val requests = service.getPendingRequests(userId2Filter = "eq.$currentUserId")
                if (requests.isEmpty()) return@runCatching emptyList()
                val senderIds = requests.map { it.userId1 }.distinct()
                val profiles = service
                    .getProfilesByIds(idFilter = "in.(${senderIds.joinToString(",")})")
                    .associateBy { it.id }
                requests.mapNotNull { fs ->
                    val profile = profiles[fs.userId1] ?: return@mapNotNull null
                    FriendRequestWithProfile(
                        id = fs.id,
                        fromUserId = fs.userId1,
                        fromNickname = profile.nickname ?: fs.userId1,
                        fromGameTag = profile.gameTag ?: "",
                        fromAvatarUrl = profile.avatarUrl,
                        createdAt = 0L,
                    )
                }
            }
        }

    suspend fun searchByGameTag(gameTag: String): Result<UserSearchResultDto?> =
        withContext(dispatcher) {
            runCatching {
                service.searchByGameTag(gameTagFilter = "eq.$gameTag").firstOrNull()
            }
        }

    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                val response = service.sendFriendRequest(
                    body = SendFriendRequestDto(fromUserId, toUserId)
                )
                if (!response.isSuccessful) error("Send request failed: ${response.code()}")
            }
        }

    suspend fun acceptRequest(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                val response = service.updateFriendshipStatus(
                    idFilter = "eq.$friendshipId",
                    body = UpdateFriendshipStatusDto("ACCEPTED"),
                )
                if (!response.isSuccessful) error("Accept failed: ${response.code()}")
            }
        }

    suspend fun rejectRequest(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                val response = service.deleteFriendship(idFilter = "eq.$friendshipId")
                if (!response.isSuccessful) error("Reject failed: ${response.code()}")
            }
        }

    suspend fun removeFriend(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                val response = service.deleteFriendship(idFilter = "eq.$friendshipId")
                if (!response.isSuccessful) error("Remove failed: ${response.code()}")
            }
        }
}

data class FriendWithProfile(
    val id: String,
    val friendUserId: String,
    val nickname: String,
    val gameTag: String,
    val avatarUrl: String?,
)

data class FriendRequestWithProfile(
    val id: String,
    val fromUserId: String,
    val fromNickname: String,
    val fromGameTag: String,
    val fromAvatarUrl: String?,
    val createdAt: Long,
)
