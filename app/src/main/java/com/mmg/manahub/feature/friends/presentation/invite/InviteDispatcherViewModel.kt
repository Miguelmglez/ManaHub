package com.mmg.manahub.feature.friends.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.feature.friends.domain.usecase.AcceptInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * Activity-scoped ViewModel that processes incoming friend invite deep links.
 *
 * Responsibilities:
 * - If the user is already authenticated when the link arrives, process the code immediately.
 * - If not authenticated, persist the code in [PendingInviteStore] and navigate away.
 * - After the user logs in, the [combine] collector in [init] detects both conditions and
 *   processes the pending code automatically without requiring the user to reopen the link.
 */
@HiltViewModel
class InviteDispatcherViewModel @Inject constructor(
    private val acceptInviteUseCase: AcceptInviteUseCase,
    private val pendingInviteStore: PendingInviteStore,
    private val authRepo: AuthRepository,
) : ViewModel() {

    // ── Session state cache ───────────────────────────────────────────────────

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // ── One-time UI events ────────────────────────────────────────────────────

    sealed interface UiEvent {
        /** The invite was accepted. [inviterNickname] may be null if the profile had no nickname. */
        data class InviteAccepted(val inviterNickname: String?) : UiEvent

        /** The invite could not be accepted due to a known error. */
        data class InviteError(val isSelfInvite: Boolean, val isInvalidCode: Boolean) : UiEvent

        /** Tells the composable to navigate away from the invite screen. */
        data object NavigateAway : UiEvent
    }

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Tracks which code is currently being processed to avoid duplicates ────
    private val _processingCode = MutableStateFlow<String?>(null)

    init {
        // Mirror session state so we can read it synchronously in handleInviteCode().
        authRepo.sessionState
            .onEach { state -> _sessionState.value = state }
            .launchIn(viewModelScope)

        // Auto-process a pending code as soon as the user becomes authenticated.
        combine(authRepo.sessionState, pendingInviteStore.flow) { session, code ->
            session to code
        }
            .onEach { (session, code) ->
                if (session is SessionState.Authenticated && code != null &&
                    _processingCode.value != code &&
                    isValidReferralCode(code)
                ) {
                    processCode(code)
                } else if (code != null && !isValidReferralCode(code)) {
                    // Stale invalid code in store — clear it silently.
                    pendingInviteStore.clear()
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point called by [InviteDispatcherScreen] when the composable first appears.
     *
     * - If authenticated → process the code immediately.
     * - If not authenticated → save the code and emit [UiEvent.NavigateAway] so the user
     *   is sent to Profile/Login; the code will be processed after login via [init].
     *
     * The code is validated against the Crockford base32 format before any processing.
     * Invalid codes are rejected immediately without a network call.
     */
    fun handleInviteCode(code: String) {
        if (!isValidReferralCode(code)) {
            viewModelScope.launch {
                _events.send(UiEvent.InviteError(isSelfInvite = false, isInvalidCode = true))
                _events.send(UiEvent.NavigateAway)
            }
            return
        }
        val currentSession = _sessionState.value
        viewModelScope.launch {
            if (currentSession is SessionState.Authenticated) {
                processCode(code)
            } else {
                pendingInviteStore.save(code)
                _events.send(UiEvent.NavigateAway)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true when [code] matches the 8-character Crockford base32 format used for
     * referral codes. Rejects codes with invalid characters or wrong length before any
     * network call is made.
     */
    private fun isValidReferralCode(code: String): Boolean =
        code.length == 8 && code.all { it in "23456789ABCDEFGHJKMNPQRSTVWXYZ" }

    /**
     * Clears any pending code and calls [acceptInviteUseCase].
     *
     * On success emits [UiEvent.InviteAccepted] then [UiEvent.NavigateAway].
     * On failure inspects the error message for known ERRCODE tokens and emits
     * [UiEvent.InviteError] then [UiEvent.NavigateAway].
     */
    private suspend fun processCode(code: String) {
        // Guard: mark as being processed to prevent the combine from re-triggering.
        _processingCode.value = code
        pendingInviteStore.clear()

        val result = acceptInviteUseCase(code)

        if (result.isSuccess) {
            val inviterNickname = result.getOrNull()?.inviterNickname
            _events.send(UiEvent.InviteAccepted(inviterNickname))
        } else {
            val message = result.exceptionOrNull()?.message ?: ""
            val isSelfInvite = message.contains("SELF_INVITE", ignoreCase = true)
            val isInvalidCode = message.contains("INVALID_CODE", ignoreCase = true)
            _events.send(UiEvent.InviteError(isSelfInvite = isSelfInvite, isInvalidCode = isInvalidCode))
        }

        // Do NOT reset _processingCode to null — the guard must stay set until the
        // ViewModel is cleared. Resetting it creates a re-entry window before
        // NavigateAway actually removes the screen from the back stack.
        _events.send(UiEvent.NavigateAway)
    }
}
