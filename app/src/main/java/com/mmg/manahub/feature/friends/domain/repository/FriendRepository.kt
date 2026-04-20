package com.mmg.manahub.feature.friends.domain.repository

import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun observeFriends(): Flow<List<Friend>>
    fun observePendingRequests(): Flow<List<FriendRequest>>
    fun observePendingCount(): Flow<Int>
    fun observeFriendCount(): Flow<Int>
    suspend fun refreshFriends(currentUserId: String): Result<Unit>
    suspend fun refreshRequests(currentUserId: String): Result<Unit>
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit>
    suspend fun acceptRequest(friendshipId: String, currentUserId: String): Result<Unit>
    suspend fun rejectRequest(friendshipId: String): Result<Unit>
    suspend fun removeFriend(friendshipId: String): Result<Unit>
    suspend fun searchByGameTag(gameTag: String): Result<Friend?>
}
