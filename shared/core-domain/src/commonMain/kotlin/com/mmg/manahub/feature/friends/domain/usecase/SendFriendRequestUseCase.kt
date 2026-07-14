package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.domain.repository.FriendRepository

class SendFriendRequestUseCase(private val repo: FriendRepository) {
    suspend operator fun invoke(fromUserId: String, toUserId: String) =
        repo.sendFriendRequest(fromUserId, toUserId)
}
