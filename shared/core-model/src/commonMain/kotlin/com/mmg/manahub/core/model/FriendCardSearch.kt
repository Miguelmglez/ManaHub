package com.mmg.manahub.core.model

/**
 * Server-side filters for the `search_friend_cards` RPC. Null / empty means "no constraint".
 * Ranges are inclusive; [colorsMode] / [identityMode] follow [ColorMatchMode]'s truth table.
 */
data class FriendCardSearchParams(
    val name: String? = null,
    val nameExact: Boolean = false,
    val oracleText: String? = null,
    val typesAll: List<String>? = null,
    val typesAny: List<String>? = null,
    val typesExclude: List<String>? = null,
    val colors: List<String>? = null,
    val colorsMode: ColorMatchMode = ColorMatchMode.AT_LEAST,
    val identity: List<String>? = null,
    val identityMode: ColorMatchMode = ColorMatchMode.AT_MOST,
    val mvMin: Int? = null,
    val mvMax: Int? = null,
    val powerMin: Int? = null,
    val powerMax: Int? = null,
    val toughnessMin: Int? = null,
    val toughnessMax: Int? = null,
    val rarities: List<String>? = null,
    val setCodes: List<String>? = null,
    val formats: List<String>? = null,
    val formatLegal: Boolean = true,
    val languages: List<String>? = null,
) {
    /** True when no filter at all is active (plain browse of the list). */
    fun isEmpty(): Boolean = this == FriendCardSearchParams()

    /** Number of active filter groups besides the free-text name (telemetry only). */
    fun activeFilterCount(): Int = listOf(
        nameExact,
        oracleText != null,
        typesAll != null,
        typesAny != null,
        typesExclude != null,
        colors != null,
        identity != null,
        mvMin != null || mvMax != null,
        powerMin != null || powerMax != null,
        toughnessMin != null || toughnessMax != null,
        rarities != null,
        setCodes != null,
        formats != null,
        languages != null,
    ).count { it }
}

/** Keyset cursor: the last row's `sort_key` + `row_id`, passed back verbatim for the next page. */
data class FriendCardCursor(val sortKey: String, val rowId: String)

/**
 * One page of a friend's list; [nextCursor] is non-null exactly when [hasMore] is true.
 *
 * @property unindexedCount rows of the list the server could not evaluate against metadata filters yet.
 * @property unresolvedIds scryfall ids whose display metadata is not in the local card cache.
 */
data class FriendCardPage(
    val cards: List<FriendCard>,
    val nextCursor: FriendCardCursor?,
    val hasMore: Boolean,
    val unindexedCount: Int = 0,
    val unresolvedIds: Set<String> = emptySet(),
)

/** Overlays locally cached [card] metadata onto a server-built row, keeping the ownership fields. */
fun FriendCard.withMetadata(card: Card): FriendCard = copy(
    name = card.name,
    typeLine = card.typeLine,
    imageNormal = card.imageNormal,
    imageArtCrop = card.imageArtCrop,
    setCode = card.setCode,
    setName = card.setName,
    rarity = card.rarity,
    priceEur = card.priceEur,
    priceUsd = card.priceUsd,
    priceEurFoil = card.priceEurFoil,
    priceUsdFoil = card.priceUsdFoil,
    isStale = card.isStale,
)

/** Typed failures of a friend-card search, mapped from the RPC's error MESSAGE tokens. */
sealed class FriendCardSearchException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The friend's list is neither shared with the caller nor public. */
    class AccessDenied : FriendCardSearchException("ACCESS_DENIED")

    /** No valid session: NOT_AUTHENTICATED, an expired JWT or HTTP 401. Retrying cannot succeed. */
    class SessionExpired : FriendCardSearchException("SESSION_EXPIRED")

    /** The viewer's own profile is incomplete, so friend lists are not browsable yet. */
    class ProfileIncomplete : FriendCardSearchException("PROFILE_INCOMPLETE")

    /** A server-side cap or format check rejected the request; [detail] is the RPC's DETAIL token. */
    class InvalidArgument(val detail: String?) : FriendCardSearchException("INVALID_ARGUMENT:${detail.orEmpty()}")

    /** Any other server rejection (SELF_LOOKUP, INVALID_LIST, unknown token, 5xx). */
    class Rejected(val reason: String) : FriendCardSearchException(reason)

    /** The request never produced an HTTP response (offline, timeout, DNS). */
    class Network(cause: Throwable) : FriendCardSearchException("NETWORK", cause)
}

/**
 * A friendship mutation matched no row: the request or friendship no longer exists, or row-level
 * security hides it from the caller. The server changed nothing, so the local cache must not either.
 */
class FriendshipGoneException : Exception("The friend request or friendship no longer exists")
