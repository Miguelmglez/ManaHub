package com.mmg.manahub.feature.friends.domain.repository

import com.mmg.manahub.feature.friends.domain.model.AcceptInviteResult
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.model.FriendStats
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun observeFriends(): Flow<List<Friend>>
    fun observePendingRequests(): Flow<List<FriendRequest>>
    fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequest>>
    fun observePendingCount(): Flow<Int>
    fun observeFriendCount(): Flow<Int>
    suspend fun refreshFriends(currentUserId: String): Result<Unit>
    suspend fun refreshRequests(currentUserId: String): Result<Unit>
    suspend fun refreshOutgoingRequests(currentUserId: String): Result<Unit>
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit>
    suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit>
    suspend fun rejectRequest(friendshipId: String): Result<Unit>
    suspend fun cancelOutgoingRequest(friendshipId: String): Result<Unit>
    suspend fun removeFriend(friendshipId: String): Result<Unit>
    suspend fun searchByGameTag(gameTag: String): Result<Friend?>

    /**
     * Calls the `accept_invite` Supabase RPC with [referralCode].
     *
     * On failure the [Result] wraps a [Throwable] whose [Throwable.message] may contain
     * `"SELF_INVITE"` or `"INVALID_CODE"` to distinguish known error cases.
     */
    suspend fun acceptInvite(referralCode: String): Result<AcceptInviteResult>

    /**
     * Fetches the authenticated user's referral code and builds the shareable URL.
     *
     * Returns `Result.failure` if the user has no code yet.
     */
    suspend fun getMyShareUrl(userId: String): Result<String>

    /**
     * Fetches the card list for a friend from the `get_friend_collection` RPC.
     *
     * The server enforces access: the caller must be friends with [friendUserId] or
     * the requested [list] must be public. Returns [Result.failure] on any error
     * (network, access denied, etc.).
     *
     * @param friendUserId Auth UUID of the friend.
     * @param list         Which list to fetch: 'collection', 'wishlist', or 'trade'.
     * @param query        Optional name filter; empty string means no filter.
     */
    suspend fun getFriendCollection(
        friendUserId: String,
        list: String,
        query: String,
    ): Result<List<FriendCard>>

    /**
     * Fetches the collection stats snapshot for [friendUserId] from `user_collection_stats`.
     *
     * Returns [Result.success] wrapping null when the user has no stats row yet (privacy
     * gate or the friend has never run the sync worker). Returns [Result.failure] on
     * network or HTTP errors.
     */
    suspend fun getFriendStats(friendUserId: String): Result<FriendStats?>

    /**
     * Pushes the caller's own collection statistics to Supabase via `upsert_collection_stats`.
     *
     * @param uniqueCards       Number of distinct card printings.
     * @param totalCards        Total quantity of cards.
     * @param totalValueEur     Estimated value in EUR.
     * @param totalValueUsd     Estimated value in USD.
     * @param favouriteColor    MTG colour with the most cards, or null.
     * @param mostValuableColor MTG colour with the highest value, or null.
     */
    suspend fun upsertMyStats(
        uniqueCards: Int,
        totalCards: Int,
        totalValueEur: Double,
        totalValueUsd: Double,
        favouriteColor: String?,
        mostValuableColor: String?,
    ): Result<Unit>
}
