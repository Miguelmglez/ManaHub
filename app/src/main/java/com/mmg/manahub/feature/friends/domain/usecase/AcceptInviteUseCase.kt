package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.feature.friends.domain.model.AcceptInviteResult
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import javax.inject.Inject

/**
 * Accepts a friend invite identified by a Crockford base32 referral code.
 *
 * Delegates to [FriendRepository.acceptInvite] which calls the `accept_invite` Supabase RPC.
 * On failure the [Result] wraps a [Throwable] whose [Throwable.message] may contain
 * one of the PostgreSQL error tokens: `"SELF_INVITE"` or `"INVALID_CODE"`.
 */
class AcceptInviteUseCase @Inject constructor(
    private val repository: FriendRepository,
) {
    suspend operator fun invoke(referralCode: String): Result<AcceptInviteResult> =
        repository.acceptInvite(referralCode)
}
