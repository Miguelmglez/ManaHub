package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Legacy DTO kept for backward compatibility with any callers that still reference
 * [UserProfileDto] by name. New code should prefer [UserProfileRetrofitDto].
 *
 * The [gameTag] column is auto-generated server-side and must never be sent by the client.
 */
@Serializable
data class UserProfileDto(
    @SerialName("id") val id: String,
    @SerialName("nickname") val nickname: String?,
    @SerialName("game_tag") val gameTag: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("provider") val provider: String?,
)

/**
 * Data source for the `user_profiles` Supabase table.
 *
 * All network calls are performed via [SupabaseUserProfileService] (Retrofit + OkHttp),
 * which is authenticated through an OkHttp interceptor that injects the Supabase
 * apikey and the current user's Bearer token.
 */
class UserProfileDataSource(
    private val service: SupabaseUserProfileService,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Fetches the full profile row for [userId] from `user_profiles`.
     *
     * Returns null when no row is found or on any network/parse failure (non-fatal).
     * Callers should treat null as "no server profile available" and fall back to
     * locally available data.
     */
    suspend fun fetchUserProfile(userId: String): UserProfileDto? = withContext(ioDispatcher) {
        try {
            service.fetchProfile("eq.$userId")
                .firstOrNull()
                ?.toUserProfileDto()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "fetchUserProfile failed for user $userId", e)
            } else {
                Log.w(TAG, "fetchUserProfile failed: ${e.javaClass.simpleName}")
            }
            null
        }
    }

    /**
     * Upserts the user profile into `user_profiles` (including nickname), then reads back
     * the full row to capture the server-generated [AuthUser.gameTag].
     *
     * Returns an updated [AuthUser] with [AuthUser.gameTag] populated from the DB row, or the
     * original [user] unchanged if the operation fails (non-fatal).
     */
    suspend fun upsertUserProfile(user: AuthUser): AuthUser = withContext(ioDispatcher) {
        try {
            service.upsertProfile(
                profile = UpsertUserProfileDto(
                    id = user.id,
                    email = user.email,
                    nickname = user.nickname,
                    avatarUrl = user.avatarUrl,
                    provider = user.provider,
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            // Read back the full row to retrieve the server-generated game_tag.
            val profile = service.fetchProfile("eq.${user.id}").firstOrNull()
            if (profile != null) {
                user.copy(gameTag = profile.gameTag)
            } else {
                user
            }
        } catch (e: Exception) {
            // Non-fatal: the user is already authenticated even if profile sync fails.
            // Only log the full stack trace in debug builds to avoid leaking internal
            // Supabase error details (table names, constraint violations) in production logs.
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "upsertUserProfile failed — profile sync skipped", e)
            } else {
                Log.w(TAG, "upsertUserProfile failed: ${e.javaClass.simpleName}")
            }
            user
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun UserProfileRetrofitDto.toUserProfileDto() = UserProfileDto(
        id = id,
        nickname = nickname,
        gameTag = gameTag,
        avatarUrl = avatarUrl,
        provider = provider,
    )

    private companion object {
        private const val TAG = "UserProfileDataSource"
    }
}
