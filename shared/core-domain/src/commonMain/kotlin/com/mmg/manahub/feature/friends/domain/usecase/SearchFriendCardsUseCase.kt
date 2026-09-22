package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.FriendCardCursor
import com.mmg.manahub.core.model.FriendCardPage
import com.mmg.manahub.core.model.FriendCardSearchParams

/** Keyset search over a friend's collection, wishlist or trade list, plus its indexing/hydration helpers. */
class SearchFriendCardsUseCase(
    private val repo: FriendRepository,
) {
    suspend operator fun invoke(
        friendUserId: String,
        list: String,
        params: FriendCardSearchParams,
        cursor: FriendCardCursor? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Result<FriendCardPage> = repo.searchFriendCards(friendUserId, list, params, cursor, limit)

    suspend fun unindexedCount(friendUserId: String, list: String): Result<Int> =
        repo.getFriendListUnindexedCount(friendUserId, list)

    suspend fun hydrateMetadata(scryfallIds: List<String>): Map<String, Card> =
        repo.hydrateFriendCardMetadata(scryfallIds)

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
