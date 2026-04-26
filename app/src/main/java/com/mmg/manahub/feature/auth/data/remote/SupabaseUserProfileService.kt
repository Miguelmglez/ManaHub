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
        @Query("select") select: String = "id,nickname,game_tag,avatar_url,provider",
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

}

// ── DTOs ──────────────────────────────────────────────────────────────────────

/**
 * DTO returned by [SupabaseUserProfileService.fetchProfile].
 * Uses Gson [SerializedName] since the Supabase Kotlin SDK serialization is no longer used
 * for PostgREST calls.
 */
data class UserProfileRetrofitDto(
    @SerializedName("id") val id: String,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("game_tag") val gameTag: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("provider") val provider: String?,
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
