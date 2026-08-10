package com.mmg.manahub.feature.online.presentation.lobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ParticipantListRow
import com.mmg.manahub.core.ui.components.RoomCodeField
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A bottom sheet that drives the full online join flow without navigating away from GameSetupScreen.
 *
 * Online sessions are pure Supabase-over-internet — no Nearby/Bluetooth/location permissions are
 * required to join one (a prior version of this sheet incorrectly gated joining behind the Nearby
 * permission set; see audit finding #7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineJoinSheet(
    prefilledCode: String? = null,
    initialDisplayName: String = "",
    initialThemeKey: String = "Crimson",
    onDismiss: () -> Unit,
    onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int, guestToken: String?) -> Unit,
    viewModel: LobbyJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Seed ViewModel with inherited values on first composition.
    LaunchedEffect(Unit) {
        if (initialDisplayName.isNotBlank()) viewModel.onDisplayNameChanged(initialDisplayName)
        viewModel.onThemeChanged(initialThemeKey)
    }

    // Pre-fill code from deep link.
    LaunchedEffect(prefilledCode) {
        if (!prefilledCode.isNullOrBlank()) {
            viewModel.prefillCode(prefilledCode)
        }
    }

    // Show error toasts reactively.
    LaunchedEffect(uiState.error) {
        val e = uiState.error ?: return@LaunchedEffect
        toastState.show(e, MagicToastType.ERROR)
        viewModel.clearError()
    }

    // Dismissing the sheet after joining must leave the session server-side (auto-ready on join
    // otherwise leaves a ghost "ready" participant behind — audit finding #6). Before joining
    // there is nothing to leave, so a plain dismiss is safe.
    val handleDismiss: () -> Unit = {
        if (uiState.sessionId != null) {
            viewModel.leaveSession(onNavigateBack = onDismiss)
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        // Swipe/scrim dismissal is intentionally blocked — the only exit is the ✕ button, which
        // routes through handleDismiss above so a live session is always left cleanly.
        sheetState = rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.Hidden }
        ),
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = spacing.xl, vertical = spacing.sm)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = handleDismiss,
                        modifier = Modifier.offset(x = -spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    if (uiState.sessionId == null) {
                        // ── Pre-join form ──────────────────────────────────────────
                        Text(
                            text = stringResource(R.string.lobby_join_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )

                        Spacer(Modifier.height(spacing.xs))

                        RoomCodeField(
                            code = uiState.codeInput,
                            onCodeChange = viewModel::onCodeChanged,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                        )

                        // Color picker
                        Text(
                            text = stringResource(R.string.lobby_your_color_label),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                        )
                        JoinThemeSelectorRow(
                            selectedKey = uiState.selectedThemeKey,
                            onThemeSelected = viewModel::onThemeChanged,
                        )

                        Button(
                            onClick = { viewModel.joinSession(onGameStart) },
                            enabled = uiState.codeInput.length == 6 && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.primaryAccent,
                                contentColor = mc.background,
                                disabledContainerColor = mc.surfaceVariant,
                                disabledContentColor = mc.textDisabled,
                            ),
                            shape = ButtonShape,
                        ) {
                            if (uiState.isLoading) {
                                MagicLoadingSpinner(
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(text = stringResource(R.string.lobby_join_button), style = ty.labelLarge)
                            }
                        }
                    } else {
                        // ── Waiting room ───────────────────────────────────────────
                        Text(
                            text = stringResource(R.string.lobby_waiting_room_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            uiState.participants.sortedBy { it.slotIndex }.forEach { participant ->
                                ParticipantListRow(
                                    displayName = participant.displayName,
                                    themeKey = participant.themeKey ?: "Crimson",
                                    isHost = participant.isHost,
                                    isCurrentUser = participant.slotIndex == uiState.slotIndex,
                                    isReady = participant.isReady,
                                    isEmpty = false,
                                    slotIndex = participant.slotIndex,
                                )
                            }
                        }

                        if (uiState.sessionStatus == OnlineSessionStatus.ACTIVE) {
                            Text(
                                text = stringResource(R.string.lobby_starting_msg),
                                style = ty.bodyMedium,
                                color = mc.lifePositive,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Explicit leave action — previously the only way out of a live session
                        // was the ✕ (which now routes through handleDismiss), with no affordance
                        // inside the waiting room itself, and no way to un-ready either (the
                        // retired LobbyJoinScreen's ready-toggle was already dead — audit
                        // finding #6/#10).
                        OutlinedButton(
                            onClick = handleDismiss,
                            enabled = !uiState.isLoading && uiState.sessionStatus != OnlineSessionStatus.ACTIVE,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = mc.textSecondary,
                            ),
                            border = BorderStroke(1.dp, mc.surfaceVariant),
                            shape = ButtonShape,
                        ) {
                            Text(text = stringResource(R.string.lobby_action_leave_room), style = ty.labelLarge)
                        }
                    }
                }
            }

            MagicToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = spacing.sm),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/** Horizontal scrollable row of color theme circles for the joining player. */
@Composable
private fun JoinThemeSelectorRow(
    selectedKey: String,
    onThemeSelected: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        contentPadding = PaddingValues(horizontal = spacing.xs),
    ) {
        items(PlayerTheme.ALL, key = { it.name }) { theme ->
            val isSelected = theme.name == selectedKey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.clickable { onThemeSelected(theme.name) },
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                        .then(
                            if (isSelected) Modifier.border(3.dp, mc.textPrimary, CircleShape)
                            else Modifier.border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = mc.background,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Text(
                    text = theme.name,
                    style = ty.labelSmall,
                    color = if (isSelected) mc.textPrimary else mc.textSecondary,
                )
            }
        }
    }
}
