package com.mmg.manahub.feature.friends.presentation

/** First user-perceived character of [name], uppercased; never splits a surrogate pair (emoji). */
internal fun avatarInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

/**
 * Lazy-list keys of the Friends screen. They are section-scoped because one friendship id can sit in
 * two lists for a moment (an accepted request before its outgoing row is dropped).
 */
internal object FriendsListKeys {
    fun incoming(friendshipId: String) = "incoming:$friendshipId"
    fun outgoing(friendshipId: String) = "outgoing:$friendshipId"
    fun friend(friendshipId: String) = "friend:$friendshipId"

    /** Every row key the screen builds for [state], in display order. */
    fun rowKeys(state: FriendsViewModel.UiState): List<String> =
        state.pendingRequests.map { incoming(it.id) } +
            state.outgoingRequests.map { outgoing(it.id) } +
            state.friends.map { friend(it.id) }
}
