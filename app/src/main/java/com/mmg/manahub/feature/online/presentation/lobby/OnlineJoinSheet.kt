package com.mmg.manahub.feature.online.presentation.lobby

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.nearby.presentation.rememberNearbyPermissionsState
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ParticipantListRow
import com.mmg.manahub.core.ui.components.RoomCodeField
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A bottom sheet that drives the full online join flow without navigating away from GameSetupScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnlineJoinSheet(
    prefilledCode: String? = null,
    initialDisplayName: String = "",
    initialThemeKey: String = "Crimson",
    onDismiss: () -> Unit,
    onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    viewModel: LobbyJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val permissionsState = rememberNearbyPermissionsState()
    var pendingJoin by remember { mutableStateOf(false) }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Seed ViewModel with inherited values on first composition.
    LaunchedEffect(Unit) {
        viewModel.onDisplayNameChanged(initialDisplayName)
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

    // Join session once Nearby permissions are granted.
    LaunchedEffect(permissionsState.allPermissionsGranted, pendingJoin) {
        if (pendingJoin && permissionsState.allPermissionsGranted) {
            pendingJoin = false
            viewModel.joinSession(onGameStart)
        }
    }

    val handleJoinSession = {
        if (permissionsState.allPermissionsGranted) {
            viewModel.joinSession(onGameStart)
        } else {
            pendingJoin = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        sheetState = rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.Hidden }
        ),
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.offset(x = (-12).dp)
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.sessionId == null) {
                        // ── Pre-join form ──────────────────────────────────────────
                        Text(
                            text = stringResource(R.string.lobby_join_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )

                        Spacer(Modifier.height(4.dp))

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
                            onClick = handleJoinSession,
                            enabled = uiState.codeInput.length == 6 && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.primaryAccent,
                                contentColor = mc.background,
                                disabledContainerColor = mc.surfaceVariant,
                                disabledContentColor = mc.textDisabled,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = mc.background,
                                    strokeWidth = 2.dp,
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

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }
            }

            MagicToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
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

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(PlayerTheme.ALL, key = { it.name }) { theme ->
            val isSelected = theme.name == selectedKey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
