package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.SearchFriendCardsRequestDto
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardSearchException
import com.mmg.manahub.core.model.FriendCardSearchParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive

/** Maps a PostgREST error response of `search_friend_cards` to a typed exception by its MESSAGE token. */
internal object FriendCardSearchErrors {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromResponse(status: Int, body: String): FriendCardSearchException {
        val parsed = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val message = parsed.stringField("message").orEmpty()
        val details = parsed.stringField("details")
        // A body that is not PostgREST JSON still carries the raised token as plain text.
        val token = message.takeIf { it in KNOWN_TOKENS }
            ?: KNOWN_TOKENS.firstOrNull { body.contains(it) }
        return when {
            token == "ACCESS_DENIED" -> FriendCardSearchException.AccessDenied()
            token == "PROFILE_INCOMPLETE" -> FriendCardSearchException.ProfileIncomplete()
            token == "NOT_AUTHENTICATED" -> FriendCardSearchException.SessionExpired()
            token == "INVALID_ARGUMENT" -> FriendCardSearchException.InvalidArgument(details)
            token != null -> FriendCardSearchException.Rejected(token)
            status == 401 || JWT_EXPIRED.any { body.contains(it, ignoreCase = true) } ->
                FriendCardSearchException.SessionExpired()
            else -> FriendCardSearchException.Rejected("HTTP_$status")
        }
    }

    // Non-primitive or missing fields read as null; a malformed body must never throw here.
    private fun JsonObject?.stringField(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()

    private val JWT_EXPIRED = listOf("JWT expired", "PGRST301", "PGRST303")

    private val KNOWN_TOKENS = listOf(
        "ACCESS_DENIED", "PROFILE_INCOMPLETE", "NOT_AUTHENTICATED", "INVALID_ARGUMENT", "SELF_LOOKUP",
        "INVALID_LIST",
    )
}

internal fun ColorMatchMode.toRpcValue(): String = when (this) {
    ColorMatchMode.ANY_OF -> "any_of"
    ColorMatchMode.AT_MOST -> "at_most"
    ColorMatchMode.EXACTLY -> "exactly"
    ColorMatchMode.AT_LEAST -> "at_least"
}

internal fun FriendCardSearchParams.toRequestDto(
    friendUserId: String,
    list: String,
    cursor: FriendCardCursor?,
    limit: Int,
): SearchFriendCardsRequestDto = SearchFriendCardsRequestDto(
    pFriendUserId = friendUserId,
    pList = list,
    pName = name,
    pNameExact = nameExact,
    pOracleText = oracleText,
    pTypesAll = typesAll?.ifEmpty { null },
    pTypesAny = typesAny?.ifEmpty { null },
    pTypesExclude = typesExclude?.ifEmpty { null },
    pColors = colors?.ifEmpty { null },
    pColorsMode = colorsMode.toRpcValue(),
    pIdentity = identity?.ifEmpty { null },
    pIdentityMode = identityMode.toRpcValue(),
    pMvMin = mvMin,
    pMvMax = mvMax,
    pPowerMin = powerMin,
    pPowerMax = powerMax,
    pToughnessMin = toughnessMin,
    pToughnessMax = toughnessMax,
    pRarities = rarities?.ifEmpty { null },
    pSetCodes = setCodes?.ifEmpty { null },
    pFormats = formats?.ifEmpty { null },
    pFormatLegal = formatLegal,
    pLanguages = languages?.ifEmpty { null },
    pLimit = limit.coerceIn(1, 100),
    pAfterSortKey = cursor?.sortKey,
    pAfterRowId = cursor?.rowId,
)
