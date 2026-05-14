package com.mmg.manahub.feature.auth.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service for Supabase PostgREST calls against the `user_profiles` table
 * and related RPC functions.
 *
 * All requests are authenticated via the OkHttp interceptor defined in [AuthModule]
 * (apikey + Bearer token headers injected automatically).
 */
interface SupabaseUserProfileService {

    /**
     * Fetches a single user profile row by id.
     *
     * @param idFilter PostgREST equality filter, format: "eq.{userId}"
     * @param select Comma-separated column list to return.
     */
    @GET("user_profiles")
    suspend fun fetchProfile(
        @Query("id") idFilter: String,
        @Query("select") select: String = "id,nickname,game_tag,avatar_url,provider,profile_completed",
    ): List<UserProfileRetrofitDto>

    /**
     * Upserts a user profile row. Uses merge-duplicates resolution so an existing
     * row is updated rather than replaced.
     */
    @POST("user_profiles")
    suspend fun upsertProfile(
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body profile: UpsertUserProfileDto,
    ): Response<Unit>

    /**
     * Calls the `update_user_nickname` Supabase RPC.
     * Returns HTTP 400 when the nickname is flagged as inappropriate by the server trigger.
     */
    @POST("rpc/update_user_nickname")
    suspend fun updateNickname(@Body body: UpdateNicknameDto): Response<Unit>

    /**
     * Calls the `update_user_avatar` Supabase RPC.
     * Pass null to remove the avatar.
     */
    @POST("rpc/update_user_avatar")
    suspend fun updateAvatarUrl(@Body body: UpdateAvatarUrlDto): Response<Unit>

    /**
     * Calls the `get_profile_by_user_id` Supabase RPC.
     * Returns the full [UserProfileRetrofitDto] row for the given user, including [profileCompleted].
     *
     * PostgREST always wraps RPC results in a JSON array, even for single rows, so the return
     * type is List. Callers should use [List.firstOrNull] to get the single expected element.
     * This is the preferred way to check whether a Google OAuth user has already completed
     * the sign-up flow, since the [handle_new_user] trigger always creates a row — meaning
     * a direct table query can never return null for a newly authenticated user.
     */
    @POST("rpc/get_profile_by_user_id")
    suspend fun getProfileByUserId(
        @Body body: GetProfileByUserIdDto,
    ): List<UserProfileRetrofitDto>

    /**
     * Calls the `complete_user_profile` Supabase RPC.
     * Atomically sets the user's [nickname] and marks `profile_completed = TRUE`.
     * Must be called after Google OAuth completes during the sign-up flow.
     *
     * PostgREST always wraps RPC results in a JSON array, even for single rows, so the return
     * type is List. Callers should use [List.firstOrNull] to get the single expected element.
     * Throws [retrofit2.HttpException] on failure (e.g. 400 for inappropriate nickname).
     */
    @POST("rpc/complete_user_profile")
    suspend fun completeUserProfile(
        @Body body: CompleteUserProfileDto,
    ): List<UserProfileRetrofitDto>

}

// ── DTOs ──────────────────────────────────────────────────────────────────────

/**
 * DTO returned by [SupabaseUserProfileService.fetchProfile],
 * [SupabaseUserProfileService.getProfileByUserId], and
 * [SupabaseUserProfileService.completeUserProfile].
 *
 * [profileCompleted] mirrors the `profile_completed` column: FALSE while the user
 * has not yet finished the sign-up flow (nickname not chosen), TRUE afterwards.
 */
data class UserProfileRetrofitDto(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("game_tag") val gameTag: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("provider") val provider: String?,
    @SerializedName("profile_completed") val profileCompleted: Boolean = false,
)

/**
 * DTO sent to [SupabaseUserProfileService.upsertProfile].
 * The [gameTag] column is intentionally omitted — it is auto-generated server-side and
 * must never be written by the client.
 */
data class UpsertUserProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String?,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("provider") val provider: String?,
    @SerializedName("updated_at") val updatedAt: Long,
)

/**
 * DTO for the `update_user_nickname` RPC body.
 */
data class UpdateNicknameDto(
    @SerializedName("new_nickname") val newNickname: String,
)

/**
 * DTO for the `update_user_avatar` RPC body.
 * [newAvatarUrl] may be null to remove the avatar.
 */
data class UpdateAvatarUrlDto(
    @SerializedName("new_avatar_url") val newAvatarUrl: String?,
)

/**
 * DTO for the `get_profile_by_user_id` RPC body.
 * [pUserId] must be a valid UUID matching an existing auth.users row.
 */
data class GetProfileByUserIdDto(
    @SerializedName("p_user_id") val pUserId: String,
)

/**
 * DTO for the `complete_user_profile` RPC body.
 * [pNickname] is the nickname chosen by the user during the sign-up flow.
 * The RPC atomically sets the nickname and marks profile_completed = TRUE.
 */
data class CompleteUserProfileDto(
    @SerializedName("p_nickname") val pNickname: String,
)
