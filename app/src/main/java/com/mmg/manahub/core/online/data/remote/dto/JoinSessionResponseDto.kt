package com.mmg.manahub.core.online.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinSessionResponseDto(
    @SerialName("session_id")   val sessionId: String,
    @SerialName("slot_index")   val slotIndex: Int,
    // Non-null only when the caller had no Supabase Auth session (a guest). See
    // CreateSessionResponseDto.guestToken for the full rationale. A reconnect using a
    // previously-held token returns that same token back.
    @SerialName("guest_token")  val guestToken: String? = null,
)
