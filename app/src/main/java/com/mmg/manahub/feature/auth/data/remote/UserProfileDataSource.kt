package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.util.recordSafeNonFatal
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
 * Outcome of [UserProfileDataSource.getProfileByUserId].
 *
 * This deliberately distinguishes two cases the old `UserProfileDto?` contract collapsed into
 * a single `null`, which caused the Google sign-in gate to misreport a transient fetch failure
 * as "no profile" and bounce returning users back to the sign-up screen:
 *
 * - [Found]    — the RPC responded with a profile row.
 * - [NotFound] — the RPC responded successfully but no row exists for the user.
 * - [Failure]  — a real network/auth/parse error occurred (e.g. HTTP 401/403 because the
 *   session token had not propagated to the OkHttp interceptor yet). Callers MUST NOT treat
 *   this as "not found"; they should retry or surface a network error instead.
 *
 * A plain sealed type is used rather than [kotlin.Result] on purpose: [kotlin.Result] is an
 * inline value class whose name-mangled accessors are awkward to stub with MockK at the
 * data-source boundary.
 */
sealed interface ProfileFetchResult {
    /** The RPC returned a profile row. */
    data class Found(val profile: UserProfileDto) : ProfileFetchResult
    /** The RPC succeeded but no profile row exists for this user. */
    data object NotFound : ProfileFetchResult
    /** The fetch failed with a real error (network/auth/parse) — never "not found". */
    data class Failure(val error: Throwable) : ProfileFetchResult
}

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
     * Returns a [ProfileFetchResult] that distinguishes the three outcomes the old
     * `null`-on-everything contract collapsed together — a critical distinction for the
     * Google sign-in gate (see [ProfileFetchResult] for the rationale).
     *
     * An invalid UUID is a caller contract violation (not a "not found"), so it is reported
     * as a [ProfileFetchResult.Failure] rather than [ProfileFetchResult.NotFound].
     */
    suspend fun getProfileByUserId(userId: String): ProfileFetchResult = withContext(ioDispatcher) {
        if (!isValidUuid(userId)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "getProfileByUserId: invalid UUID '${userId.take(8)}'")
            return@withContext ProfileFetchResult.Failure(IllegalArgumentException("Invalid UUID"))
        }
        try {
            val dto = service.getProfileByUserId(GetProfileByUserIdDto(pUserId = userId))
                .firstOrNull()
                ?.toUserProfileDto()
            if (dto != null) ProfileFetchResult.Found(dto) else ProfileFetchResult.NotFound
        } catch (e: Exception) {
            // Surface these in production dashboards: a spike here means the OkHttp
            // interceptor is falling back to the anon key (session not yet propagated
            // after signInWith(IDToken)), which the RPC rejects. recordSafeNonFatal
            // strips the message so no PII (user id, token) reaches Crashlytics.
            recordSafeNonFatal("auth_profile_fetch_failed", e)
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "getProfileByUserId failed for user $userId", e)
            } else {
                Log.w(TAG, "getProfileByUserId failed: ${e.javaClass.simpleName}")
            }
            ProfileFetchResult.Failure(e)
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
        ).firstOrNull()
        user.copy(
            nickname = profileDto?.nickname ?: trimmedNickname,
            gameTag = profileDto?.gameTag ?: user.gameTag,
            avatarUrl = profileDto?.avatarUrl ?: user.avatarUrl,
            // The RPC succeeded (no exception), so profile_completed is TRUE even if the
            // array is unexpectedly empty. Prefer the server value when available.
            profileCompleted = profileDto?.profileCompleted ?: true,
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

    /**
     * Updates one or more privacy visibility flags on the current user's `user_profiles` row.
     *
     * Only the fields provided as non-null are included in the PATCH body. Because Gson
     * serializes nulls on the shared Supabase Retrofit instance, callers should pass a value
     * for exactly one field per call to avoid accidentally nullifying the others.
     *
     * Returns [Result.success] on HTTP 2xx, [Result.failure] otherwise.
     *
     * @param userId UUID of the authenticated user (used as the PostgREST row filter).
     * @param collectionPublic New value for `collection_public`, or null to leave unchanged.
     * @param wishlistPublic New value for `wishlist_public`, or null to leave unchanged.
     * @param tradeListPublic New value for `trade_list_public`, or null to leave unchanged.
     */
    suspend fun updatePrivacySettings(
        userId: String,
        collectionPublic: Boolean? = null,
        wishlistPublic: Boolean? = null,
        tradeListPublic: Boolean? = null,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            // Build a sparse map so that only the fields that actually changed are sent.
            // This prevents Gson's serializeNulls from writing null into NOT NULL columns.
            val body = buildMap<String, Any> {
                if (collectionPublic != null) put("collection_public", collectionPublic)
                if (wishlistPublic != null) put("wishlist_public", wishlistPublic)
                if (tradeListPublic != null) put("trade_list_public", tradeListPublic)
            }
            check(body.isNotEmpty()) { "updatePrivacySettings called with all-null arguments" }
            val response = service.updatePrivacySettings(
                idFilter = "eq.$userId",
                body = body,
            )
            if (!response.isSuccessful) {
                error("HTTP ${response.code()} updating privacy settings for user $userId")
            }
        }.onFailure { e ->
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "updatePrivacySettings failed for user $userId", e)
            } else {
                Log.w(TAG, "updatePrivacySettings failed: ${e.javaClass.simpleName}")
            }
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
