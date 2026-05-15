package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.feature.friends.domain.model.FriendCard
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import javax.inject.Inject

/**
 * Fetches a single friend's card list from the server.
 *
 * @param friendUserId Auth UUID of the friend whose list is being requested.
 * @param list         Which list to retrieve: 'collection', 'wishlist', or 'trade'.
 * @param query        Optional name filter (empty = no filter).
 */
class GetFriendCollectionUseCase @Inject constructor(
    private val repo: FriendRepository,
) {
    suspend operator fun invoke(
        friendUserId: String,
        list: String,
        query: String = "",
    ): Result<List<FriendCard>> = repo.getFriendCollection(friendUserId, list, query)
}
