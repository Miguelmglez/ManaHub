package com.mmg.manahub.feature.trades.domain.model

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
        val items: List<Map<String, String?>>,
    ) : SharedListResult()
    object Private : SharedListResult()
    object NotFound : SharedListResult()
}
