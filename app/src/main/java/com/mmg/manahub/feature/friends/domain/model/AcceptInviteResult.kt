package com.mmg.manahub.feature.friends.domain.model

/**
 * Result returned by the `accept_invite` Supabase RPC.
 *
 * @property inviterId UUID of the user whose invite link was followed.
 * @property inviterNickname Display name of the inviter, if available.
 */
data class AcceptInviteResult(
    val inviterId: String,
    val inviterNickname: String?,
)
