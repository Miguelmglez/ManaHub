package com.mmg.manahub.core.data.remote

/**
 * Shared display-name fallback helpers for the Friends data layer.
 *
 * Both the remote data source and the repository resolve a friend's display name through
 * the same chain (nickname -> game_tag -> terminal fallback). Centralising the chain here
 * keeps the fallback semantics single-sourced so the two layers can never drift (e.g. one
 * surfacing a raw auth UUID while the other shows "Unknown").
 */

/**
 * Terminal display-name fallback used when neither a nickname nor a game tag is available.
 *
 * English-only literal by project convention (no `res` lookup in the data layer): a raw auth
 * UUID must NEVER be surfaced to the UI, so the chain always ends here instead of `it.id`.
 */
const val UNKNOWN_DISPLAY_NAME: String = "Unknown"

/**
 * Returns this string only when it is non-null and not blank; otherwise null.
 *
 * Used for display-name fallback chains where a present-but-empty value must be treated the
 * same as a missing one (e.g. nickname -> game_tag -> [UNKNOWN_DISPLAY_NAME]).
 */
fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
