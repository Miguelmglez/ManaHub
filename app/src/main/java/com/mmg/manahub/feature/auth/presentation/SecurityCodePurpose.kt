package com.mmg.manahub.feature.auth.presentation

/**
 * What [SecurityCodeScreen] is gathering a reauthentication code for. Threaded through
 * `Screen.SecurityCode` as a controlled enum `routeArg`, never a free-text nav string — callers
 * always pass a [SecurityCodePurpose] constant (see `Screen.SecurityCode.createRoute`), and the
 * receiving screen parses the raw route arg back into this enum with a safe fallback.
 */
enum class SecurityCodePurpose(val routeArg: String) {
    EMAIL("email"),
    PASSWORD("password"),
    ;

    companion object {
        /** Parses a nav route arg back into a [SecurityCodePurpose], defaulting to [EMAIL]. */
        fun fromRouteArg(raw: String?): SecurityCodePurpose =
            entries.firstOrNull { it.routeArg == raw } ?: EMAIL
    }
}
