package com.mmg.manahub.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.friends.domain.usecase.ShareInviteUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen-local state for [AccountManagementScreen] that does not belong on the shared
 * [AuthViewModel] (which every other Auth screen also resolves as an entry-scoped default param):
 *
 * - The "share my profile" invite-link lookup, mirroring `ProfileViewModel.fetchShareLink()`
 *   (same [ShareInviteUseCase], cross-module from `friendsKoinModule` — see `AuthKoinModule`'s
 *   registration KDoc for why this is safe to resolve here without a duplicate definition).
 * - The 60-second cooldown after tapping "Resend confirmation email", so the user cannot spam the
 *   resend endpoint. A minimal ViewModel-owned countdown (no dedicated cooldown UI component exists
 *   in this codebase yet) rather than a shared abstraction — this is the only screen that needs it
 *   today; see [SecurityCodeScreen] for the sibling cooldown used there.
 *
 * Every other action on the screen (resend the actual email, unlink/link an identity, sign out,
 * delete the account) goes straight through the screen's own entry-scoped [AuthViewModel] instance,
 * matching the existing Profile/LoginSheet pattern — this ViewModel intentionally does NOT wrap
 * those calls.
 */
class AccountManagementViewModel(
    private val authRepository: AuthRepository,
    private val shareInviteUseCase: ShareInviteUseCase,
) : ViewModel() {

    private val _resendCooldownRemaining = MutableStateFlow(0)

    /** Seconds remaining before "Resend confirmation email" can be tapped again. 0 = ready. */
    val resendCooldownRemaining: StateFlow<Int> = _resendCooldownRemaining.asStateFlow()

    private var cooldownJob: Job? = null

    /**
     * Resolves the current user's invite link. Same contract as
     * `ProfileViewModel.fetchShareLink()` — forwarded verbatim into [ShareProfileSheet].
     */
    suspend fun fetchShareLink(): Result<String> {
        val userId = (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        return shareInviteUseCase(userId)
    }

    /** Starts (or restarts) the resend-confirmation-email cooldown countdown. */
    fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in RESEND_COOLDOWN_SECONDS downTo 0) {
                _resendCooldownRemaining.value = remaining
                if (remaining > 0) delay(1_000)
            }
        }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
