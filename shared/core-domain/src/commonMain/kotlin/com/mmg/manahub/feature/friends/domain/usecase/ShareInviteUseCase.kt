package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.domain.repository.FriendRepository

/**
 * Builds the shareable invite URL for the currently authenticated user.
 *
 * Returns `Result.success("https://miguelmglez.github.io/invite/{referralCode}")` on success,
 * or `Result.failure(...)` if the user has no referral code assigned yet.
 */
class ShareInviteUseCase(
    private val repository: FriendRepository,
) {
    suspend operator fun invoke(userId: String): Result<String> =
        repository.getMyShareUrl(userId)
}
