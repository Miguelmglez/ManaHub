package com.mmg.manahub.core.online.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionResponseDto(
    @SerialName("session_id")   val sessionId: String,
    @SerialName("code")         val code: String,
    // Non-null only when the caller had no Supabase Auth session (a guest). See the 2026-08
    // anonymous-sign-in removal: guests no longer mint a Supabase auth.users row, and instead
    // carry this opaque token for the lifetime of the session.
    @SerialName("guest_token")  val guestToken: String? = null,
)
