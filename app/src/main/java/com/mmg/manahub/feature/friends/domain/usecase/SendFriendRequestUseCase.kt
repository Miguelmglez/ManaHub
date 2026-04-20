package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import javax.inject.Inject

class SendFriendRequestUseCase @Inject constructor(private val repo: FriendRepository) {
    suspend operator fun invoke(fromUserId: String, toUserId: String) =
        repo.sendFriendRequest(fromUserId, toUserId)
}
