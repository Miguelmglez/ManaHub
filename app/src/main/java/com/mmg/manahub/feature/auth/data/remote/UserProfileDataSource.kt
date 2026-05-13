package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for Supabase SDK (kotlinx-serialization) calls against `user_profiles`.
 *
 * [profileCompleted] mirrors the `profile_completed` column that is set to FALSE by the
 * [handle_new_user] trigger on row creation and to TRUE only after the user explicitly
 * finishes the sign-up flow via the [complete_user_profile] RPC.
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
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
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
        if (!isValidUuid(userId)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "fetchUserProfile: invalid UUID '${userId.take(8)}'")
            return@withContext null
        }
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
     * Calls the `get_profile_by_user_id` RPC to fetch the profile for [userId].
     *
     * Unlike [fetchUserProfile] (which queries the table directly), this RPC is necessary
     * to correctly read `profile_completed` for Google OAuth users: the [handle_new_user]
     * trigger always inserts a row immediately, so a table query would never return null,
     * but the row would have `profile_completed = FALSE` until the user finishes sign-up.
     *
     * Returns null on any network/parse failure (non-fatal).
     */
    suspend fun getProfileByUserId(userId: String): UserProfileDto? = withContext(ioDispatcher) {
        if (!isValidUuid(userId)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "getProfileByUserId: invalid UUID '${userId.take(8)}'")
            return@withContext null
        }
        try {
            service.getProfileByUserId(GetProfileByUserIdDto(pUserId = userId))
                ?.toUserProfileDto()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "getProfileByUserId failed for user $userId", e)
            } else {
                Log.w(TAG, "getProfileByUserId failed: ${e.javaClass.simpleName}")
            }
            null
        }
    }

    /**
     * Calls the `complete_user_profile` RPC to atomically set [user]'s nickname
     * and mark `profile_completed = TRUE` in Supabase.
     *
     * This must be used instead of [upsertUserProfile] during the Google sign-up flow
     * to ensure `profile_completed` is correctly set to TRUE in a single server-side operation.
     *
     * Returns an updated [AuthUser] with [AuthUser.profileCompleted] = true and the
     * server-generated [AuthUser.gameTag] populated, or the original [user] unchanged
     * if the RPC fails (non-fatal, caller decides how to handle).
     *
     * @throws Exception when the RPC returns a non-2xx response (e.g. 400 for inappropriate nickname).
     *   The exception is NOT swallowed here — callers must handle it.
     */
    suspend fun completeUserProfile(user: AuthUser): AuthUser = withContext(ioDispatcher) {
        val trimmedNickname = requireNotNull(user.nickname?.trim()) {
            "completeUserProfile called with a null or blank nickname for user ${user.id}"
        }
        check(trimmedNickname.isNotBlank()) {
            "completeUserProfile called with a blank nickname for user ${user.id}"
        }

        val profileDto = service.completeUserProfile(
            CompleteUserProfileDto(pNickname = trimmedNickname)
        )
        user.copy(
            nickname = profileDto.nickname ?: trimmedNickname,
            gameTag = profileDto.gameTag ?: user.gameTag,
            avatarUrl = profileDto.avatarUrl ?: user.avatarUrl,
            profileCompleted = profileDto.profileCompleted,
        )
    }

    /**
     * Upserts the user profile into `user_profiles` (including nickname), then reads back
     * the full row to capture the server-generated [AuthUser.gameTag].
     *
     * Returns an updated [AuthUser] with [AuthUser.gameTag] populated from the DB row, or the
     * original [user] unchanged if the operation fails (non-fatal).
     */
    suspend fun upsertUserProfile(user: AuthUser): AuthUser = withContext(ioDispatcher) {
        if (!isValidUuid(user.id)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "upsertUserProfile: invalid UUID '${user.id.take(8)}'")
            return@withContext user
        }
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
        profileCompleted = profileCompleted,
    )

    private companion object {
        private const val TAG = "UserProfileDataSource"
        private val UUID_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun isValidUuid(id: String) = UUID_REGEX.matches(id)
}
