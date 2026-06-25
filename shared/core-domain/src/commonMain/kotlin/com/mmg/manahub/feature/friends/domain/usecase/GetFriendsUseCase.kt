package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.domain.repository.FriendRepository

class GetFriendsUseCase(private val repo: FriendRepository) {
    operator fun invoke() = repo.observeFriends()
}
