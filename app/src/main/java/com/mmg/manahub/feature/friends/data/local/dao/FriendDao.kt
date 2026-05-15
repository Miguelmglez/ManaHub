package com.mmg.manahub.feature.friends.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.manahub.feature.friends.data.local.entity.FriendEntity
import com.mmg.manahub.feature.friends.data.local.entity.FriendRequestEntity
import com.mmg.manahub.feature.friends.data.local.entity.OutgoingFriendRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    @Query("SELECT * FROM friends ORDER BY friend_nickname ASC")
    fun observeFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friend_requests ORDER BY created_at DESC")
    fun observePendingRequests(): Flow<List<FriendRequestEntity>>

    @Query("SELECT COUNT(*) FROM friend_requests")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM friends")
    fun observeFriendCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFriends(friends: List<FriendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequests(requests: List<FriendRequestEntity>)

    @Query("DELETE FROM friends")
    suspend fun clearFriends()

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriend(id: String)

    @Query("DELETE FROM friend_requests WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("DELETE FROM friend_requests")
    suspend fun clearRequests()

    // ── Outgoing friend requests ──────────────────────────────────────────────

    /** Emits the list of requests the current user has sent that are still PENDING. */
    @Query("SELECT * FROM outgoing_friend_requests ORDER BY created_at DESC")
    fun observeOutgoingRequests(): Flow<List<OutgoingFriendRequestEntity>>

    /** Count of pending outgoing requests — used to show badge on "Sent" tab. */
    @Query("SELECT COUNT(*) FROM outgoing_friend_requests")
    fun observeOutgoingCount(): Flow<Int>

    /**
     * Replaces the cached outgoing requests with the latest snapshot from Supabase.
     * Uses REPLACE conflict strategy because [OutgoingFriendRequestEntity.id] is the
     * Supabase row id — identical ids simply overwrite stale cached data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutgoingRequests(requests: List<OutgoingFriendRequestEntity>)

    /** Removes a single outgoing request by its Supabase friendship id. */
    @Query("DELETE FROM outgoing_friend_requests WHERE id = :id")
    suspend fun deleteOutgoingRequest(id: String)

    /** Clears all outgoing requests — called before a full re-fetch from Supabase. */
    @Query("DELETE FROM outgoing_friend_requests")
    suspend fun clearOutgoingRequests()
}
