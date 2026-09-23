package com.mmg.manahub.core.online.presentation

import android.content.Context
import com.mmg.manahub.R
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException

/**
 * Maps a backend failure to a user-facing, English-only string.
 *
 * Classification is by exception TYPE first (offline, timeout, 5xx, auth) and only then by the
 * RPC's human-readable text — a raw `message` match alone reported every transport failure as
 * "an unexpected error occurred". Unrecognised errors still fall back to the generic message so
 * internal backend details are never leaked to the UI.
 *
 * Shared by `LobbyHostViewModel` and `LobbyJoinViewModel`. The raw [Throwable] should be recorded
 * to Crashlytics separately by the call site (`recordException`); this function only produces the
 * string shown to the user.
 */
fun mapOnlineBackendError(context: Context, throwable: Throwable?): String =
    when (val category = classifyOnlineError(throwable)) {
        OnlineErrorCategory.CONNECTION -> context.getString(R.string.lobby_error_connection)
        OnlineErrorCategory.TIMEOUT    -> context.getString(R.string.lobby_error_timeout)
        OnlineErrorCategory.SERVER     -> context.getString(R.string.lobby_error_server)
        OnlineErrorCategory.AUTH       -> context.getString(R.string.lobby_error_sign_in)
        else                           -> mapOnlineBackendError(context, throwable?.message, category)
    }

/** Overload for the message-only sources (`SessionEvent.Error`) that carry no throwable. */
fun mapOnlineBackendError(context: Context, message: String?): String =
    mapOnlineBackendError(context, message, OnlineErrorCategory.UNKNOWN)

private fun mapOnlineBackendError(
    context: Context,
    message: String?,
    category: OnlineErrorCategory,
): String = when {
    message == null -> context.getString(R.string.lobby_error_generic)
    "Too many failed join attempts" in message ->
        context.getString(R.string.lobby_error_too_many_attempts)
    "Invalid session code format" in message ->
        context.getString(R.string.lobby_error_invalid_code)
    "Display name must be" in message ->
        context.getString(R.string.lobby_error_display_name)
    "Session limit reached" in message ->
        context.getString(R.string.lobby_error_active_room)
    "Session is full" in message ->
        context.getString(R.string.lobby_error_full)
    "not in LOBBY" in message ->
        context.getString(R.string.lobby_error_not_found)
    category == OnlineErrorCategory.SERVER -> context.getString(R.string.lobby_error_server)
    else -> context.getString(R.string.lobby_error_generic)
}

/**
 * Returns a short, non-PII token identifying the failure category, for use as a Crashlytics
 * custom-key value to segment errors on dashboards without logging raw backend text.
 */
fun classifyOnlineJoinError(throwable: Throwable?): String =
    when (val category = classifyOnlineError(throwable)) {
        OnlineErrorCategory.CONNECTION -> "network"
        OnlineErrorCategory.TIMEOUT    -> "timeout"
        OnlineErrorCategory.SERVER     -> "server"
        OnlineErrorCategory.AUTH       -> "auth"
        else                           -> classifyOnlineJoinError(throwable?.message, category)
    }

/** Overload for the message-only sources that carry no throwable. */
fun classifyOnlineJoinError(message: String?): String =
    classifyOnlineJoinError(message, OnlineErrorCategory.UNKNOWN)

private fun classifyOnlineJoinError(message: String?, category: OnlineErrorCategory): String = when {
    message == null -> "unknown"
    "Too many failed join attempts" in message -> "rate_limited"
    "Invalid session code format" in message -> "invalid_code_format"
    "Display name must be" in message -> "invalid_display_name"
    "Session limit reached" in message -> "session_limit_reached"
    "Session is full" in message -> "session_full"
    "not in LOBBY" in message -> "session_not_found_or_started"
    category == OnlineErrorCategory.SERVER -> "server"
    else -> "unexpected"
}

/** Transport/backend categories resolved from the exception type, before any message inspection. */
internal enum class OnlineErrorCategory { CONNECTION, TIMEOUT, SERVER, AUTH, UNKNOWN }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_SERVER_ERROR_MIN = 500

internal fun classifyOnlineError(throwable: Throwable?): OnlineErrorCategory {
    var current = throwable
    // The SDK wraps transport failures, so walk the cause chain before giving up
    while (current != null) {
        when {
            current is RestException -> return when {
                current.statusCode >= HTTP_SERVER_ERROR_MIN -> OnlineErrorCategory.SERVER
                current.statusCode == HTTP_UNAUTHORIZED || current.statusCode == HTTP_FORBIDDEN ->
                    OnlineErrorCategory.AUTH
                else -> OnlineErrorCategory.UNKNOWN
            }
            current is HttpRequestTimeoutException || current is SocketTimeoutException ->
                return OnlineErrorCategory.TIMEOUT
            current is UnresolvedAddressException || current is IOException ->
                return OnlineErrorCategory.CONNECTION
        }
        current = current.cause.takeIf { it !== current }
    }
    return OnlineErrorCategory.UNKNOWN
}
