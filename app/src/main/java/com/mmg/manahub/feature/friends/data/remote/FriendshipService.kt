package com.mmg.manahub.feature.friends.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

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

    @GET("friendships")
    suspend fun getOutgoingPendingRequests(
        @Query("user_id_1") userId1Filter: String,
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

    /**
     * Calls the `get_my_referral_code` Supabase RPC (SECURITY DEFINER).
     *
     * The server uses `auth.uid()` to return only the caller's own referral code,
     * preventing any authenticated user from reading another user's code via the
     * generic GET /user_profiles endpoint.
     */
    @POST("rpc/get_my_referral_code")
    suspend fun getMyReferralCode(): List<ReferralCodeDto>

    /**
     * Calls the `accept_invite` Supabase RPC.
     *
     * Returns the inviter's id and nickname on success.
     * On error (INVALID_CODE, SELF_INVITE) Supabase returns an HTTP 4xx whose error body
     * contains the PostgreSQL `ERRCODE` message, which Retrofit surfaces as [retrofit2.HttpException].
     */
    @POST("rpc/accept_invite")
    suspend fun acceptInvite(
        @Body body: AcceptInviteRequestDto,
    ): List<AcceptInviteResultDto>

    /**
     * Calls the `get_friend_collection` Supabase RPC.
     *
     * Access is controlled server-side: the caller must be a friend of [GetFriendCollectionRequestDto.pFriendUserId]
     * or the requested list must be public.
     */
    @POST("rpc/get_friend_collection")
    suspend fun getFriendCollection(
        @Body body: GetFriendCollectionRequestDto,
    ): List<FriendCardDto>

    /**
     * Fetches the collection stats snapshot for a single user from `user_collection_stats`.
     *
     * PostgREST equality filter — pass `"eq.{userId}"` as [userIdFilter].
     */
    @GET("user_collection_stats")
    suspend fun getFriendStats(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "user_id,unique_cards,total_cards,total_value_eur,total_value_usd,favourite_color,most_valuable_color,updated_at",
    ): List<FriendStatsDto>

    /**
     * Calls the `upsert_collection_stats` Supabase RPC to push the caller's own stats.
     *
     * Returns an empty 200 on success; Retrofit surfaces HTTP errors as [retrofit2.HttpException].
     */
    @POST("rpc/upsert_collection_stats")
    suspend fun upsertCollectionStats(
        @Body body: UpsertCollectionStatsDto,
    ): Response<Unit>
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

/** DTO for the referral code fetched from `user_profiles`. */
data class ReferralCodeDto(
    @SerializedName("referral_code") val referralCode: String?,
)

/** Request body for the `accept_invite` RPC. */
data class AcceptInviteRequestDto(
    @SerializedName("p_referral_code") val pReferralCode: String,
)

/** Response row returned by the `accept_invite` RPC. */
data class AcceptInviteResultDto(
    @SerializedName("inviter_id") val inviterId: String,
    @SerializedName("inviter_nickname") val inviterNickname: String?,
)

/** Request body for the `get_friend_collection` RPC. */
data class GetFriendCollectionRequestDto(
    @SerializedName("p_friend_user_id") val pFriendUserId: String,
    @SerializedName("p_list") val pList: String,
    @SerializedName("p_query") val pQuery: String = "",
)

/** A single card row returned by the `get_friend_collection` RPC. */
data class FriendCardDto(
    @SerializedName("source_list") val sourceList: String,
    @SerializedName("scryfall_id") val scryfallId: String,
    @SerializedName("card_name") val cardName: String,
    @SerializedName("image_normal") val imageNormal: String?,
    @SerializedName("set_name") val setName: String?,
    @SerializedName("rarity") val rarity: String?,
    @SerializedName("price_eur") val priceEur: Double?,
    @SerializedName("price_usd") val priceUsd: Double?,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("is_foil") val isFoil: Boolean,
    @SerializedName("condition") val condition: String?,
    @SerializedName("language") val language: String?,
)

/** A row from `user_collection_stats` returned by the PostgREST GET endpoint. */
data class FriendStatsDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("unique_cards") val uniqueCards: Int,
    @SerializedName("total_cards") val totalCards: Int,
    @SerializedName("total_value_eur") val totalValueEur: Double,
    @SerializedName("total_value_usd") val totalValueUsd: Double,
    @SerializedName("favourite_color") val favouriteColor: String?,
    @SerializedName("most_valuable_color") val mostValuableColor: String?,
    @SerializedName("updated_at") val updatedAt: String,
)

/** Request body for the `upsert_collection_stats` RPC. */
data class UpsertCollectionStatsDto(
    @SerializedName("p_unique_cards") val pUniqueCards: Int,
    @SerializedName("p_total_cards") val pTotalCards: Int,
    @SerializedName("p_total_value_eur") val pTotalValueEur: Double,
    @SerializedName("p_total_value_usd") val pTotalValueUsd: Double,
    @SerializedName("p_favourite_color") val pFavouriteColor: String?,
    @SerializedName("p_most_valuable_color") val pMostValuableColor: String?,
)
