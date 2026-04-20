package com.mmg.manahub.feature.friends.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface FriendshipService {

    @GET("friendships")
    suspend fun getFriendships(
        @Query("status") statusFilter: String = "eq.ACCEPTED",
        @Query("select") select: String = "id,user_id_1,user_id_2,status,created_at",
        @Query("or") or: String,
    ): List<FriendshipDto>

    @GET("friendships")
    suspend fun getPendingRequests(
        @Query("user_id_2") userId2Filter: String,
        @Query("status") statusFilter: String = "eq.PENDING",
        @Query("select") select: String = "id,user_id_1,user_id_2,status,created_at",
    ): List<FriendshipDto>

    @GET("user_profiles")
    suspend fun searchByGameTag(
        @Query("game_tag") gameTagFilter: String,
        @Query("select") select: String = "id,nickname,game_tag,avatar_url",
    ): List<UserSearchResultDto>

    @POST("friendships")
    suspend fun sendFriendRequest(
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SendFriendRequestDto,
    ): Response<Unit>

    @PATCH("friendships")
    suspend fun updateFriendshipStatus(
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=minimal",
        @Body body: UpdateFriendshipStatusDto,
    ): Response<Unit>

    @DELETE("friendships")
    suspend fun deleteFriendship(
        @Query("id") idFilter: String,
    ): Response<Unit>

    @GET("user_profiles")
    suspend fun getProfilesByIds(
        @Query("id") idFilter: String,
        @Query("select") select: String = "id,nickname,game_tag,avatar_url",
    ): List<UserSearchResultDto>
}

data class FriendshipDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id_1") val userId1: String,
    @SerializedName("user_id_2") val userId2: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
)

data class UserSearchResultDto(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("game_tag") val gameTag: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
)

data class SendFriendRequestDto(
    @SerializedName("user_id_1") val userId1: String,
    @SerializedName("user_id_2") val userId2: String,
)

data class UpdateFriendshipStatusDto(
    @SerializedName("status") val status: String,
)
