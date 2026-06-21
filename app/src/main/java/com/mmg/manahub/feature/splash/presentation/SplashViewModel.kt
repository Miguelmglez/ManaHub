package com.mmg.manahub.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the splash screen.
 *
 * Exposes [sessionState] derived from [AuthRepository.sessionState] so the
 * composable can react to authentication resolution without holding any
 * business logic itself.
 *
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The plain constructor
 * lets `splashKoinModule` build it with the bridged [AuthRepository] singleton.
 */
class SplashViewModel(
    authRepository: AuthRepository,
) : ViewModel() {

    /** Reflects the current authentication session state. Starts as [SessionState.Loading]. */
    val sessionState: StateFlow<SessionState> = authRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading,
        )
}
