package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CompleteUserProfileDto
import com.mmg.manahub.core.data.remote.dto.GetProfileByUserIdDto
import com.mmg.manahub.core.data.remote.dto.UpdateAvatarUrlDto
import com.mmg.manahub.core.data.remote.dto.UpdateNicknameDto
import com.mmg.manahub.core.data.remote.dto.UpsertUserProfileDto
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

/**
 * Ktor-based HTTP client for Supabase PostgREST calls against the `user_profiles`
 * table and related RPC functions.
 *
 * All requests are authenticated via the OkHttp engine's interceptor (apikey + Bearer token).
 * The [httpClient] is configured with `expectSuccess = true`, so non-2xx responses
 * throw [io.ktor.client.plugins.ResponseException] automatically.
 */
class UserProfileClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    /** Fetches a single user profile row by id. */
    suspend fun fetchProfile(
        idFilter: String,
        select: String = "id,nickname,game_tag,avatar_url,provider,profile_completed",
    ): List<UserProfileDto> =
        httpClient.get("${baseUrl}user_profiles") {
            parameter("id", idFilter)
            parameter("select", select)
        }.body()

    /** Upserts a user profile row (merge-duplicates). */
    suspend fun upsertProfile(
        prefer: String = "resolution=merge-duplicates,return=minimal",
        profile: UpsertUserProfileDto,
    ) {
        httpClient.post("${baseUrl}user_profiles") {
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(profile)
        }
    }

    /** Calls the `update_user_nickname` RPC. Throws on non-2xx (e.g. 400 for inappropriate). */
    suspend fun updateNickname(body: UpdateNicknameDto) {
        httpClient.post("${baseUrl}rpc/update_user_nickname") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** Calls the `update_user_avatar` RPC. Pass null [UpdateAvatarUrlDto.newAvatarUrl] to remove. */
    suspend fun updateAvatarUrl(body: UpdateAvatarUrlDto) {
        httpClient.post("${baseUrl}rpc/update_user_avatar") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** Calls the `get_profile_by_user_id` RPC. Returns a list (PostgREST wraps single rows). */
    suspend fun getProfileByUserId(body: GetProfileByUserIdDto): List<UserProfileDto> =
        httpClient.post("${baseUrl}rpc/get_profile_by_user_id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    /** Calls `complete_user_profile` RPC (sets nickname + profile_completed = TRUE). */
    suspend fun completeUserProfile(body: CompleteUserProfileDto): List<UserProfileDto> =
        httpClient.post("${baseUrl}rpc/complete_user_profile") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    /** Updates privacy visibility flags via PATCH. Only fields present in [body] are sent. */
    suspend fun updatePrivacySettings(
        idFilter: String,
        prefer: String = "return=minimal",
        body: JsonObject,
    ) {
        httpClient.patch("${baseUrl}user_profiles") {
            parameter("id", idFilter)
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
