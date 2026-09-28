package com.mmg.manahub.core.model

data class SharedList(
    val id: String,
    val userId: String,
    val listType: SharedListType,
    val createdAt: Long,
)

enum class SharedListType { WISHLIST, OPEN_FOR_TRADE }

sealed class SharedListResult {
    data class Ok(
        val listType: SharedListType,
        val userId: String,
        val ownerNickname: String = "",
        val items: List<SharedListItem>,
    ) : SharedListResult()
    object Private : SharedListResult()
    object NotFound : SharedListResult()
}

/**
 * One card of a resolved shared list. Wishlist rows carry [matchAnyVariant]; open-for-trade rows
 * carry the offered copy's [userCardId] and [quantity].
 */
data class SharedListItem(
    val cardId: String,
    val quantity: Int = 1,
    val isFoil: Boolean? = null,
    val condition: String? = null,
    val language: String? = null,
    val matchAnyVariant: Boolean = false,
    val userCardId: String? = null,
)

private val SHARE_ID_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** True when [shareId] is a UUID; the server rejects anything else before it can say "not found". */
fun isValidShareId(shareId: String): Boolean = SHARE_ID_UUID.matches(shareId)
