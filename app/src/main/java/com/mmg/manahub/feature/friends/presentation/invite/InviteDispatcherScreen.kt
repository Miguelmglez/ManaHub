package com.mmg.manahub.feature.friends.presentation.invite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.theme.magicColors

/**
 * A "phantom" screen that shows a loading spinner while [InviteDispatcherViewModel] processes
 * the incoming referral [code].
 *
 * Navigation away from this screen is handled exclusively by [AppNavGraph], which collects
 * [InviteDispatcherViewModel.UiEvent.NavigateAway] from the activity-scoped ViewModel and
 * also displays the success / error toast. This composable only triggers the processing.
 *
 * @param code            The 8-character Crockford base32 referral code from the deep link.
 * @param onNavigateAway  Fallback called if navigation needs to be triggered from this side
 *                        (e.g., if the AppNavGraph LaunchedEffect has not started yet).
 * @param inviteVm        Activity-scoped [InviteDispatcherViewModel] passed from [AppNavGraph].
 */
@Composable
fun InviteDispatcherScreen(
    code: String,
    onNavigateAway: () -> Unit,
    inviteVm: InviteDispatcherViewModel,
) {
    val mc = MaterialTheme.magicColors

    // Trigger processing once per unique code value.
    // Navigation is performed by AppNavGraph's LaunchedEffect which collects the same events.
    LaunchedEffect(code) {
        inviteVm.handleInviteCode(code)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = mc.primaryAccent)
    }
}
