package com.mmg.manahub.core.online.domain.model

/**
 * Result of successfully joining an online session.
 *
 * @property sessionId Server-assigned session identifier.
 * @property slotIndex Seat index assigned by the backend.
 * @property guestToken Opaque identity token minted by the backend when the caller had no
 *   Supabase Auth session at all (a guest — see the 2026-08 anonymous-sign-in removal). Null when
 *   the caller was a real signed-in account, in which case identity is resolved via `auth.uid()`
 *   for every subsequent RPC on this session instead. Guests must persist this value in memory for
 *   the lifetime of the session and pass it back on every subsequent call (`p_guest_token`).
 */
data class JoinSessionResult(
    val sessionId: String,
    val slotIndex: Int,
    val guestToken: String?,
)
