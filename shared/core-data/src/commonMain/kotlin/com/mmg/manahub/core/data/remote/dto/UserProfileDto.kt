package com.mmg.manahub.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for Supabase `user_profiles` table rows.
 *
 * [profileCompleted] mirrors the `profile_completed` column: FALSE while the user
 * has not yet finished the sign-up flow (nickname not chosen), TRUE afterwards.
 */
@Serializable
data class UserProfileDto(
    @SerialName("id") val id: String,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("game_tag") val gameTag: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
)

/**
 * DTO sent to the upsert endpoint on `user_profiles`.
 * The `game_tag` column is intentionally omitted — it is auto-generated server-side.
 */
@Serializable
data class UpsertUserProfileDto(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

/** DTO for the `update_user_nickname` RPC body. */
@Serializable
data class UpdateNicknameDto(
    @SerialName("new_nickname") val newNickname: String,
)

/**
 * DTO for the `update_user_avatar` RPC body.
 * [newAvatarUrl] may be null to remove the avatar.
 */
@Serializable
data class UpdateAvatarUrlDto(
    @SerialName("new_avatar_url") val newAvatarUrl: String? = null,
)

/** DTO for the `get_profile_by_user_id` RPC body. */
@Serializable
data class GetProfileByUserIdDto(
    @SerialName("p_user_id") val pUserId: String,
)

/**
 * DTO for the `complete_user_profile` RPC body.
 * The RPC atomically sets the nickname and marks profile_completed = TRUE.
 */
@Serializable
data class CompleteUserProfileDto(
    @SerialName("p_nickname") val pNickname: String,
)
