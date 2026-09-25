package com.mmg.manahub.feature.friends.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.friends.domain.usecase.AcceptInviteUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Why an invite could not be accepted; the host maps each value to its copy. */
enum class InviteErrorReason { SELF_INVITE, INVALID_CODE, SESSION_EXPIRED, GENERIC }

/**
 * Activity-scoped ViewModel that processes friend invite deep links.
 *
 * A signed-in user's code is accepted immediately. Otherwise the code is persisted in
 * [PendingInviteStore] and accepted automatically once a session exists. The stored code is dropped
 * only on success or a permanent refusal, so a transient failure never loses it.
 *
 * Must stay Activity-scoped (`koinViewModel(viewModelStoreOwner = activity)` in `AppNavGraph`) so a
 * rotation mid-RPC reuses this instance, whose in-flight guard then ignores the re-run.
 */
class InviteDispatcherViewModel(
    private val acceptInviteUseCase: AcceptInviteUseCase,
    private val pendingInviteStore: PendingInviteStore,
    private val authRepo: AuthRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    sealed interface UiEvent {
        /** The invite was accepted. [inviterNickname] may be null if the profile had no nickname. */
        data class InviteAccepted(val inviterNickname: String?) : UiEvent

        /** The invite could not be accepted. */
        data class InviteError(val reason: InviteErrorReason) : UiEvent

        /** Leave the invite screen; only sent for codes opened through it. */
        data object NavigateAway : UiEvent
    }

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Main-thread confined: every read and write happens on viewModelScope's Main dispatcher.
    private var processingCode: String? = null
    private val acceptedCodes = mutableSetOf<String>()
    // Codes whose last attempt failed transiently; the auto-processor skips them so offline never loops.
    private val failedCodes = mutableSetOf<String>()

    init {
        combine(authRepo.sessionState, pendingInviteStore.flow) { session, code -> session to code }
            .onEach { (session, stored) ->
                val code = stored?.let(::normalize) ?: return@onEach
                when {
                    !isValidReferralCode(code) -> pendingInviteStore.clear()
                    session is SessionState.Authenticated && canAutoProcess(code) -> processCode(code, fromScreen = false)
                }
            }
            .catch { e -> crashReporter.recordException(RuntimeException("[invite_pending_flow_failed] ${e::class.simpleName}")) }
            .launchIn(viewModelScope)
    }

    /**
     * Entry point of [InviteDispatcherScreen]. Codes are case-insensitive; a code already being
     * processed or already accepted is ignored, so a recreated screen never sends a second request.
     */
    fun handleInviteCode(rawCode: String) {
        val code = normalize(rawCode)
        crashReporter.log("invite_link_received")
        if (!isValidReferralCode(code)) {
            crashReporter.log("invite_link_invalid_format")
            viewModelScope.launch {
                _events.send(UiEvent.InviteError(InviteErrorReason.INVALID_CODE))
                _events.send(UiEvent.NavigateAway)
            }
            return
        }
        if (code == processingCode) return
        if (code in acceptedCodes) {
            viewModelScope.launch { _events.send(UiEvent.NavigateAway) }
            return
        }
        failedCodes -= code
        processingCode = code
        viewModelScope.launch {
            // Loading is not signed out: wait for the session to resolve before deciding.
            val session = authRepo.sessionState.first { it !is SessionState.Loading }
            if (session is SessionState.Authenticated) {
                processCode(code, fromScreen = true)
            } else {
                crashReporter.log("invite_deferred_until_sign_in")
                processingCode = null
                pendingInviteStore.save(code)
                _events.send(UiEvent.NavigateAway)
            }
        }
    }

    private fun canAutoProcess(code: String): Boolean =
        code != processingCode && code !in acceptedCodes && code !in failedCodes

    private suspend fun processCode(code: String, fromScreen: Boolean) {
        processingCode = code
        val result = acceptInviteUseCase(code)
        if (result.isSuccess) {
            crashReporter.log("invite_accept_success")
            acceptedCodes += code
            pendingInviteStore.clear()
            _events.send(UiEvent.InviteAccepted(result.getOrNull()?.inviterNickname))
        } else {
            val reason = reasonOf(result.exceptionOrNull())
            crashReporter.log("invite_accept_failed_${reason.name.lowercase()}")
            when (reason) {
                // Permanent: retrying the same code can never succeed.
                InviteErrorReason.SELF_INVITE, InviteErrorReason.INVALID_CODE -> pendingInviteStore.clear()
                else -> {
                    failedCodes += code
                    pendingInviteStore.save(code)
                }
            }
            _events.send(UiEvent.InviteError(reason))
        }
        processingCode = null
        if (fromScreen) _events.send(UiEvent.NavigateAway)
    }

    private fun reasonOf(error: Throwable?): InviteErrorReason {
        val message = error?.message.orEmpty()
        return when {
            message.contains("SELF_INVITE", ignoreCase = true) -> InviteErrorReason.SELF_INVITE
            message.contains("INVALID_CODE", ignoreCase = true) -> InviteErrorReason.INVALID_CODE
            message.contains("NOT_AUTHENTICATED", ignoreCase = true) -> InviteErrorReason.SESSION_EXPIRED
            else -> InviteErrorReason.GENERIC
        }
    }

    companion object {
        private const val REFERRAL_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val REFERRAL_LENGTH = 8

        /** Crockford base32 is case-insensitive and links are often lowercased, so codes compare uppercased. */
        fun normalize(code: String): String = code.trim().uppercase()

        /** True for an 8-character code over the referral alphabet (already [normalize]d). */
        fun isValidReferralCode(code: String): Boolean =
            code.length == REFERRAL_LENGTH && code.all { it in REFERRAL_ALPHABET }
    }
}
