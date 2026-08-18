package com.mmg.manahub.feature.auth.data.remote

import android.util.Log
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.dto.CompleteUserProfileDto
import com.mmg.manahub.core.data.remote.dto.GetProfileByUserIdDto
import com.mmg.manahub.core.data.remote.dto.UpsertUserProfileDto
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import com.mmg.manahub.core.domain.auth.AuthUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
 * All network calls are performed via [UserProfileClient] (Ktor + OkHttp engine),
 * which is authenticated through an OkHttp interceptor that injects the Supabase
 * apikey and the current user's Bearer token.
 */
class UserProfileDataSource(
    private val client: UserProfileClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val crashReporter: CrashReporter,
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
            client.fetchProfile("eq.$userId")
                .firstOrNull()
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
     * Fetches whether the current user has a real, user-manageable password set, via the
     * self-scoped `get_my_has_password` RPC (SECURITY DEFINER) — mirrors
     * `FriendRemoteDataSource.getMyReferralCode`'s pattern on this same table for the same
     * reason: `user_profiles.has_password` is granted `SELECT` to `authenticated` cross-user, so
     * it must never be read via [fetchProfile]'s direct table select (2026-08-17 security fix,
     * see [UserProfileClient.getMyHasPassword]'s KDoc).
     *
     * Non-fatal, mirrors [fetchUserProfile]'s contract: returns `false` on any network/parse
     * failure or invalid [userId], since this is a best-effort UI-gating signal
     * (`AccountManagementScreen`'s "Change password" vs "Set a password" gate), not a security
     * boundary — the RPC itself is what enforces the actual access control.
     *
     * @param userId Unused by the request itself (the RPC is self-scoped server-side via
     *   `(select auth.uid())`, so the caller cannot request another user's value); kept as a
     *   parameter to match this data source's other per-user fetch methods and for UUID
     *   validation before making the call.
     */
    suspend fun fetchHasPassword(
        @Suppress("UNUSED_PARAMETER") userId: String,
    ): Boolean = withContext(ioDispatcher) {
        if (!isValidUuid(userId)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "fetchHasPassword: invalid UUID '${userId.take(8)}'")
            return@withContext false
        }
        try {
            client.getMyHasPassword().firstOrNull()?.hasPassword ?: false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "fetchHasPassword failed for user $userId", e)
            } else {
                Log.w(TAG, "fetchHasPassword failed: ${e.javaClass.simpleName}")
            }
            false
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
            val dto = client.getProfileByUserId(GetProfileByUserIdDto(pUserId = userId))
                .firstOrNull()
            if (dto != null) ProfileFetchResult.Found(dto) else ProfileFetchResult.NotFound
        } catch (e: Exception) {
            // Surface these in production dashboards: a spike here means the OkHttp
            // interceptor is falling back to the anon key (session not yet propagated
            // after signInWith(IDToken)), which the RPC rejects. The message is stripped
            // (mirrors the old recordSafeNonFatal helper) so no PII (user id, token)
            // reaches the crash reporter.
            crashReporter.recordException(
                RuntimeException("[auth_profile_fetch_failed] ${e::class.simpleName}", e),
            )
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

        val profileDto = client.completeUserProfile(
            CompleteUserProfileDto(pNickname = trimmedNickname)
        ).firstOrNull()
        user.copy(
            nickname = profileDto?.nickname ?: trimmedNickname,
            gameTag = profileDto?.gameTag ?: user.gameTag,
            avatarUrl = profileDto?.avatarUrl ?: user.avatarUrl,
            // The RPC succeeded (no exception), so profile_completed is TRUE even if the
            // array is unexpectedly empty. Prefer the server value when available.
            profileCompleted = profileDto?.profileCompleted ?: true,
            // has_password is never part of UserProfileDto (2026-08-17 security fix — see
            // UserProfileClient.fetchProfile's KDoc) and a brand-new Google sign-up never has one
            // at this point anyway, so this simply preserves the incoming value. Only the
            // sessionState enrichment path (AuthRepositoryImpl) queries the real value via the
            // self-scoped get_my_has_password RPC — see AuthUser.hasPassword's KDoc.
            hasPassword = user.hasPassword,
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
            client.upsertProfile(
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
            val profile = client.fetchProfile("eq.${user.id}").firstOrNull()
            if (profile != null) {
                // has_password is never part of UserProfileDto (2026-08-17 security fix — see
                // UserProfileClient.fetchProfile's KDoc); this is always a brand-new-or-fresh
                // profile row here (a real password can only be set afterward, from Account
                // Management), so simply preserving the incoming value is correct — the
                // sessionState enrichment path is the sole source of truth for a live value.
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
            // Build a sparse JsonObject so that only the fields that actually changed are sent.
            val body = buildJsonObject {
                if (collectionPublic != null) put("collection_public", JsonPrimitive(collectionPublic))
                if (wishlistPublic != null) put("wishlist_public", JsonPrimitive(wishlistPublic))
                if (tradeListPublic != null) put("trade_list_public", JsonPrimitive(tradeListPublic))
            }
            check(body.isNotEmpty()) { "updatePrivacySettings called with all-null arguments" }
            client.updatePrivacySettings(
                idFilter = "eq.$userId",
                body = body,
            )
        }.onFailure { e ->
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "updatePrivacySettings failed for user $userId", e)
            } else {
                Log.w(TAG, "updatePrivacySettings failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        private const val TAG = "UserProfileDataSource"
        private val UUID_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun isValidUuid(id: String) = UUID_REGEX.matches(id)
}
