package com.mmg.manahub.feature.friends.presentation.invite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner

/**
 * A "phantom" screen that shows a loading spinner while [InviteDispatcherViewModel] processes
 * the incoming referral [code].
 *
 * Navigation away from this screen is handled exclusively by [AppNavGraph], which collects
 * [InviteDispatcherViewModel.UiEvent.NavigateAway] from the activity-scoped ViewModel and
 * also displays the success / error toast. This composable only triggers the processing.
 *
 * @param code     The 8-character Crockford base32 referral code from the deep link (any case).
 * @param inviteVm Activity-scoped [InviteDispatcherViewModel] passed from [AppNavGraph].
 */
@Composable
fun InviteDispatcherScreen(
    code: String,
    inviteVm: InviteDispatcherViewModel,
) {
    // Re-runs after a recreation are ignored by the ViewModel's in-flight guard.
    LaunchedEffect(code) {
        inviteVm.handleInviteCode(code)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MagicLoadingSpinner()
    }
}
