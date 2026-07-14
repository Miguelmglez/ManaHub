package com.mmg.manahub.core.online.presentation

import android.content.Context
import com.mmg.manahub.R

/**
 * Maps a raw Supabase RPC error [message] to a user-facing, English-only string.
 *
 * Unrecognised errors fall back to a generic message so internal backend details are never
 * leaked to the UI. Matching is intentionally brittle (substring match on human-readable RPC
 * text) until the backend returns stable error codes — see memory
 * `feedback_auth_resterror_typed_errorcode` for the typed-error pattern this should eventually
 * follow, mirroring `AuthErrorCode`.
 *
 * Shared by `LobbyHostViewModel` and `LobbyJoinViewModel` (audit finding #14 — this logic was
 * previously duplicated verbatim in both ViewModels). The raw [Throwable] should be recorded to
 * Crashlytics separately by the call site (`recordException`) before invoking this function;
 * this mapper only produces the string shown to the user.
 */
fun mapOnlineBackendError(context: Context, message: String?): String = when {
    message == null -> context.getString(R.string.lobby_error_generic)
    "Too many failed join attempts" in message ->
        context.getString(R.string.lobby_error_too_many_attempts)
    "Invalid session code format" in message ->
        context.getString(R.string.lobby_error_invalid_code)
    "Session limit reached" in message ->
        context.getString(R.string.lobby_error_active_room)
    "Session is full" in message ->
        context.getString(R.string.lobby_error_full)
    "not in LOBBY" in message ->
        context.getString(R.string.lobby_error_not_found)
    else -> context.getString(R.string.lobby_error_generic)
}

/**
 * Returns a short, non-PII token identifying the join failure category, for use as a
 * Crashlytics custom-key value to segment join errors on dashboards without logging raw
 * backend text.
 */
fun classifyOnlineJoinError(message: String?): String = when {
    message == null -> "unknown"
    "Too many failed join attempts" in message -> "rate_limited"
    "Invalid session code format" in message -> "invalid_code_format"
    "Session limit reached" in message -> "session_limit_reached"
    "Session is full" in message -> "session_full"
    "not in LOBBY" in message -> "session_not_found_or_started"
    else -> "unexpected"
}
