package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for reading back a row from the `user_profiles` table.
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

class UserProfileDataSource(
    private val supabase: SupabaseClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Upserts the user profile into `user_profiles` (including nickname), then reads back
     * the full row to capture the server-generated [AuthUser.gameTag].
     *
     * Returns an updated [AuthUser] with [AuthUser.gameTag] populated from the DB row, or the
     * original [user] unchanged if the operation fails (non-fatal).
     */
    suspend fun upsertUserProfile(user: AuthUser): AuthUser = withContext(ioDispatcher) {
        try {
            supabase.from("user_profiles").upsert(
                mapOf(
                    "id" to user.id,
                    "email" to user.email,
                    "nickname" to user.nickname,
                    "avatar_url" to user.avatarUrl,
                    "provider" to user.provider,
                    "updated_at" to java.time.Instant.now().toString()
                )
            )

            // Read back the full row to retrieve the server-generated game_tag.
            val profile = supabase
                .from("user_profiles")
                .select { filter { eq("id", user.id) } }
                .decodeSingle<UserProfileDto>()

            user.copy(gameTag = profile.gameTag)
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

    private companion object {
        private const val TAG = "UserProfileDataSource"
    }
}
