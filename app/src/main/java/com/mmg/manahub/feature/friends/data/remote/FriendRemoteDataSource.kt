package com.mmg.manahub.feature.friends.data.remote

import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.data.remote.dto.AcceptInviteRequestDto
import com.mmg.manahub.core.data.remote.dto.AcceptInviteResultDto
import com.mmg.manahub.core.data.remote.dto.FriendCardDto
import com.mmg.manahub.core.data.remote.dto.FriendMatchHistoryDto
import com.mmg.manahub.core.data.remote.dto.FriendStatsDto
import com.mmg.manahub.core.data.remote.dto.GetFriendCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.GetFriendMatchHistoryRequestDto
import com.mmg.manahub.core.data.remote.dto.SendFriendRequestDto
import com.mmg.manahub.core.data.remote.dto.UpdateFriendshipStatusDto
import com.mmg.manahub.core.data.remote.dto.UpsertCollectionStatsDto
import com.mmg.manahub.core.data.remote.dto.UserSearchResultDto
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.friends.data.UNKNOWN_DISPLAY_NAME
import com.mmg.manahub.feature.friends.data.orNullIfBlank
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FriendRemoteDataSource @Inject constructor(
    private val client: FriendshipClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun getFriends(currentUserId: String): Result<List<FriendWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val orFilter = "(user_id_1.eq.$currentUserId,user_id_2.eq.$currentUserId)"
                val friendships = client.getFriendships(or = orFilter)
                if (friendships.isEmpty()) return@runCatching emptyList()
                val otherIds = friendships
                    .map { if (it.userId1 == currentUserId) it.userId2 else it.userId1 }
                    .distinct()
                val profiles = client
                    .getProfilesByIds(idFilter = "in.(${otherIds.joinToString(",")})")
                    .associateBy { it.id }
                friendships.map { fs ->
                    val otherId = if (fs.userId1 == currentUserId) fs.userId2 else fs.userId1
                    val profile = profiles[otherId]
                    FriendWithProfile(
                        id = fs.id,
                        friendUserId = otherId,
                        nickname = profile?.nickname.orNullIfBlank()
                            ?: profile?.gameTag.orNullIfBlank()
                            ?: UNKNOWN_DISPLAY_NAME,
                        gameTag = profile?.gameTag ?: "",
                        avatarUrl = profile?.avatarUrl,
                    )
                }
            }
        }

    suspend fun getPendingRequests(currentUserId: String): Result<List<FriendRequestWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val requests = client.getPendingRequests(userId2Filter = "eq.$currentUserId")
                if (requests.isEmpty()) return@runCatching emptyList()
                val senderIds = requests.map { it.userId1 }.distinct()
                val profiles = client
                    .getProfilesByIds(idFilter = "in.(${senderIds.joinToString(",")})")
                    .associateBy { it.id }
                requests.map { fs ->
                    val profile = profiles[fs.userId1]
                    FriendRequestWithProfile(
                        id = fs.id,
                        fromUserId = fs.userId1,
                        fromNickname = profile?.nickname.orNullIfBlank()
                            ?: profile?.gameTag.orNullIfBlank()
                            ?: UNKNOWN_DISPLAY_NAME,
                        fromGameTag = profile?.gameTag ?: "",
                        fromAvatarUrl = profile?.avatarUrl,
                        createdAt = 0L,
                    )
                }
            }
        }

    suspend fun searchByGameTag(gameTag: String): Result<UserSearchResultDto?> =
        withContext(dispatcher) {
            runCatching {
                client.searchByGameTag(gameTagFilter = "eq.$gameTag").firstOrNull()
            }
        }

    /**
     * Sends a friend request. With Ktor `expectSuccess = true`, non-2xx responses
     * throw automatically — the wrapping [runCatching] catches them.
     */
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                client.sendFriendRequest(
                    body = SendFriendRequestDto(fromUserId, toUserId),
                )
            }
        }

    /**
     * Accepts a pending friend request by updating its status to ACCEPTED.
     */
    suspend fun acceptRequest(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                client.updateFriendshipStatus(
                    idFilter = "eq.$friendshipId",
                    body = UpdateFriendshipStatusDto("ACCEPTED"),
                )
            }
        }

    suspend fun rejectRequest(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                client.deleteFriendship(idFilter = "eq.$friendshipId")
            }
        }

    suspend fun removeFriend(friendshipId: String): Result<Unit> =
        withContext(dispatcher) {
            runCatching {
                client.deleteFriendship(idFilter = "eq.$friendshipId")
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
                client.getMyReferralCode().firstOrNull()?.referralCode
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Calls the `accept_invite` RPC with the given [referralCode].
     *
     * @throws io.ktor.client.plugins.ResponseException when Supabase returns a PostgreSQL
     *   error (e.g. INVALID_CODE or SELF_INVITE). The error body contains the message.
     * @throws Exception on network failures.
     */
    suspend fun acceptInvite(referralCode: String): AcceptInviteResultDto =
        withContext(dispatcher) {
            client.acceptInvite(AcceptInviteRequestDto(referralCode)).firstOrNull()
                ?: throw IllegalStateException("accept_invite returned empty result")
        }

    /**
     * Calls the `get_friend_collection` RPC and returns the raw DTO list.
     *
     * @param friendUserId Auth UUID of the friend.
     * @param list         Which list to fetch: 'collection', 'wishlist', or 'trade'.
     * @param query        Optional name filter (applied client-side after enrichment).
     * @param limit        Maximum rows to return from the server (for pagination).
     * @param offset       Row offset for the current page.
     */
    suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
        sets: List<String>? = null,
        rarities: List<String>? = null,
        colors: List<String>? = null,
        foilOnly: Boolean? = null,
        conditions: List<String>? = null,
        languages: List<String>? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<FriendCardDto> =
        withContext(dispatcher) {
            client.getFriendCollection(
                GetFriendCollectionRequestDto(
                    pFriendUserId = friendUserId,
                    pList = list,
                    pQuery = query,
                    pSets = sets,
                    pRarities = rarities,
                    pColors = colors,
                    pFoilOnly = foilOnly,
                    pConditions = conditions,
                    pLanguages = languages,
                    pLimit = limit,
                    pOffset = offset,
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
            client.getFriendStats(userIdFilter = "eq.$friendUserId").firstOrNull()
        }

    /**
     * Calls the `get_friend_match_history` RPC and returns the single result row,
     * or null if no games have been played between the caller and [friendUserId].
     */
    suspend fun getFriendMatchHistory(friendUserId: String): FriendMatchHistoryDto? =
        withContext(dispatcher) {
            client.getFriendMatchHistory(GetFriendMatchHistoryRequestDto(friendUserId)).firstOrNull()
        }

    /**
     * Calls the `upsert_collection_stats` RPC to persist the caller's own stats.
     * With Ktor `expectSuccess = true`, non-2xx throws automatically.
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
                client.upsertCollectionStats(
                    UpsertCollectionStatsDto(
                        pUniqueCards = uniqueCards,
                        pTotalCards = totalCards,
                        pTotalValueEur = totalValueEur,
                        pTotalValueUsd = totalValueUsd,
                        pFavouriteColor = favouriteColor,
                        pMostValuableColor = mostValuableColor,
                    )
                )
            }
        }

    suspend fun getOutgoingRequests(currentUserId: String): Result<List<OutgoingRequestWithProfile>> =
        withContext(dispatcher) {
            runCatching {
                val requests = client.getOutgoingPendingRequests(userId1Filter = "eq.$currentUserId")
                if (requests.isEmpty()) return@runCatching emptyList()
                val receiverIds = requests.map { it.userId2 }.distinct()
                val profiles = client
                    .getProfilesByIds(idFilter = "in.(${receiverIds.joinToString(",")})")
                    .associateBy { it.id }
                requests.map { fs ->
                    val profile = profiles[fs.userId2]
                    OutgoingRequestWithProfile(
                        id = fs.id,
                        toUserId = fs.userId2,
                        toNickname = profile?.nickname.orNullIfBlank()
                            ?: profile?.gameTag.orNullIfBlank()
                            ?: UNKNOWN_DISPLAY_NAME,
                        toGameTag = profile?.gameTag ?: "",
                        toAvatarUrl = profile?.avatarUrl,
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
