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

    /**
     * Fetches the referral code for [userId] from `user_profiles`.
     * Returns null if the user has no referral code or on any network error.
     */
    suspend fun getMyReferralCode(@Suppress("UNUSED_PARAMETER") userId: String): String? =
        withContext(dispatcher) {
            try {
                // Uses the server-side get_my_referral_code() RPC (SECURITY DEFINER)
                // so the caller cannot query another user's code by passing a different userId.
                service.getMyReferralCode().firstOrNull()?.referralCode
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Calls the `accept_invite` RPC with the given [referralCode].
     *
     * @throws retrofit2.HttpException when Supabase returns a PostgreSQL error
     *   (e.g. INVALID_CODE or SELF_INVITE). The error body contains the message.
     * @throws Exception on network failures.
     */
    suspend fun acceptInvite(referralCode: String): AcceptInviteResultDto =
        withContext(dispatcher) {
            service.acceptInvite(AcceptInviteRequestDto(referralCode)).firstOrNull()
                ?: throw IllegalStateException("accept_invite returned empty result")
        }

    /**
     * Calls the `get_friend_collection` RPC and returns the raw DTO list.
     *
     * @param friendUserId Auth UUID of the friend.
     * @param list         Which list to fetch: 'collection', 'wishlist', or 'trade'.
     * @param query        Optional name filter; empty string means no filter.
     */
    suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
    ): List<FriendCardDto> =
        withContext(dispatcher) {
            service.getFriendCollection(
                GetFriendCollectionRequestDto(
                    pFriendUserId = friendUserId,
                    pList = list,
                    pQuery = query,
                )
            )
        }

    /**
     * Fetches the collection stats for [friendUserId] from `user_collection_stats`.
     * Returns the first row or null if the user has no stats row yet.
     *
     * @throws Exception on network failure; callers should wrap with [runCatching].
     */
    suspend fun getFriendStats(friendUserId: String): FriendStatsDto? =
        withContext(dispatcher) {
            service.getFriendStats(userIdFilter = "eq.$friendUserId").firstOrNull()
        }

    /**
     * Calls the `upsert_collection_stats` RPC to persist the caller's own stats.
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] on any error.
     */
    suspend fun upsertCollectionStats(
        uniqueCards: Int,
        totalCards: Int,
        totalValueEur: Double,
        totalValueUsd: Double,
        favouriteColor: String?,
        mostValuableColor: String?,
    ): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                val response = service.upsertCollectionStats(
                    UpsertCollectionStatsDto(
                        pUniqueCards = uniqueCards,
                        pTotalCards = totalCards,
                        pTotalValueEur = totalValueEur,
                        pTotalValueUsd = totalValueUsd,
                        pFavouriteColor = favouriteColor,
                        pMostValuableColor = mostValuableColor,
                    )
                )
                if (!response.isSuccessful) error("upsert_collection_stats failed: ${response.code()}")
            }
        }

    suspend fun getOutgoingRequests(currentUserId: String): Result<List<OutgoingRequestWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val requests = service.getOutgoingPendingRequests(userId1Filter = "eq.$currentUserId")
                if (requests.isEmpty()) return@runCatching emptyList()
                val receiverIds = requests.map { it.userId2 }.distinct()
                val profiles = service
                    .getProfilesByIds(idFilter = "in.(${receiverIds.joinToString(",")})")
                    .associateBy { it.id }
                requests.mapNotNull { fs ->
                    val profile = profiles[fs.userId2] ?: return@mapNotNull null
                    OutgoingRequestWithProfile(
                        id = fs.id,
                        toUserId = fs.userId2,
                        toNickname = profile.nickname ?: fs.userId2,
                        toGameTag = profile.gameTag ?: "",
                        toAvatarUrl = profile.avatarUrl,
                        createdAt = 0L,
                    )
                }
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

data class OutgoingRequestWithProfile(
    val id: String,
    val toUserId: String,
    val toNickname: String,
    val toGameTag: String,
    val toAvatarUrl: String?,
    val createdAt: Long,
)
