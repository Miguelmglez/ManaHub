package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import javax.inject.Inject

class GetFriendsUseCase @Inject constructor(private val repo: FriendRepository) {
    operator fun invoke() = repo.observeFriends()
}
