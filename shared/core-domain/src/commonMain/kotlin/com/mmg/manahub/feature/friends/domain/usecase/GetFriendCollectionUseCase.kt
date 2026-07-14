package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.model.FolderFilters
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.domain.repository.FriendRepository

/**
 * Fetches a single friend's card list from the server.
 *
 * @param friendUserId Auth UUID of the friend whose list is being requested.
 * @param list         Which list to retrieve: 'collection', 'wishlist', or 'trade'.
 * @param query        Optional name filter (empty = no filter).
 */
class GetFriendCollectionUseCase(
    private val repo: FriendRepository,
) {
    suspend operator fun invoke(
        friendUserId: String,
        list: String,
        query: String = "",
        filters: FolderFilters? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<FriendCard>> = repo.getFriendCollection(friendUserId, list, query, filters, limit, offset)
}
