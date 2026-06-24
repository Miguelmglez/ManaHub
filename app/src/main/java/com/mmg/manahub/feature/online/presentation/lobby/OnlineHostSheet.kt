package com.mmg.manahub.feature.online.presentation.lobby

import android.content.Intent
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.nearby.presentation.rememberNearbyPermissionsState
import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ParticipantListRow
import com.mmg.manahub.core.ui.components.RoomCodeDisplay
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.GameMode

/**
 * A bottom sheet that drives the full online host flow without navigating away from GameSetupScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnlineHostSheet(
    initialMode: GameMode,
    initialPlayerCount: Int,
    initialDisplayName: String = "",
    initialThemeKey: String = "Crimson",
    onDismiss: () -> Unit,
    onGameStart: (sessionId: String, mode: GameMode, playerCount: Int) -> Unit,
    viewModel: LobbyHostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val permissionsState = rememberNearbyPermissionsState()
    val clipboardManager = LocalClipboardManager.current
    val ctx = LocalContext.current
    var pendingStart by remember { mutableStateOf(false) }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Seed ViewModel with the setup screen's current selections on first composition.
    LaunchedEffect(Unit) {
        viewModel.setGameMode(initialMode)
        viewModel.setPlayerCount(initialPlayerCount)
        viewModel.checkForExistingSession()
    }

    // Show error toasts reactively.
    LaunchedEffect(uiState.error) {
        val e = uiState.error ?: return@LaunchedEffect
        toastState.show(e, MagicToastType.ERROR)
        viewModel.clearError()
    }

    // Start session once Nearby permissions are granted.
    LaunchedEffect(permissionsState.allPermissionsGranted, pendingStart) {
        if (pendingStart && permissionsState.allPermissionsGranted) {
            pendingStart = false
            viewModel.startSession(onGameStart)
        }
    }

    val handleStartSession = {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startSession(onGameStart)
        } else {
            pendingStart = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    val shareAction: () -> Unit = action@{
        val code = uiState.sessionCode ?: return@action
        val shareText = ctx.getString(R.string.lobby_share_message, code)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.lobby_share_chooser_title)))
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
                        // ── Pre-creation view ──────────────────────────────────────
                        Text(
                            text = stringResource(R.string.lobby_host_sheet_title),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Summary card showing the inherited setup
                        Surface(
                            color = mc.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, mc.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${initialMode.displayName} · $initialPlayerCount ${stringResource(R.string.lobby_player_count_suffix)}",
                                    style = ty.bodyMedium,
                                    color = mc.textSecondary,
                                )
                            }
                        }

                        // Existing active rooms
                        if (uiState.existingSessions.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.lobby_active_rooms_title),
                                style = ty.labelMedium,
                                color = mc.textSecondary,
                            )
                            uiState.existingSessions.forEach { session ->
                                HostActiveSessionCard(
                                    session = session,
                                    isLoading = uiState.isLoading,
                                    onRejoin = { viewModel.resumeSession(session) },
                                    onAbandon = { viewModel.abandonSession(session.sessionId) },
                                )
                            }
                        }

                        // Color picker
                        Text(
                            text = stringResource(R.string.lobby_your_color_label),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                        )
                        HostThemeSelectorRow(
                            selectedKey = uiState.selectedThemeKey,
                            onThemeSelected = viewModel::onThemeChanged,
                        )

                        Button(
                            onClick = viewModel::createSession,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.primaryAccent,
                                contentColor = mc.background,
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
                                Text(text = stringResource(R.string.lobby_create_button), style = ty.labelLarge)
                            }
                        }
                    } else {
                        // ── Waiting room view ──────────────────────────────────────
                        FirebaseCrashlytics.getInstance()
                            .log("online_host_sheet: session_created code=${uiState.sessionCode}")

                        uiState.sessionCode?.let { code ->
                            RoomCodeDisplay(
                                code = code,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    toastState.show(ctx.getString(R.string.lobby_code_copy_success), MagicToastType.SUCCESS)
                                },
                                shareText = ctx.getString(R.string.lobby_share_message, code),
                                onShare = shareAction,
                            )
                        }

                        Text(
                            text = "${uiState.gameMode.displayName} · ${uiState.playerCount} ${stringResource(R.string.lobby_player_count_suffix)}",
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                        )

                        Text(
                            text = stringResource(R.string.lobby_players_count, uiState.participants.size, uiState.playerCount),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in 0 until uiState.playerCount) {
                                val participant = uiState.participants.find { it.slotIndex == i }
                                if (participant != null) {
                                    ParticipantListRow(
                                        displayName = participant.displayName,
                                        themeKey = participant.themeKey ?: "Crimson",
                                        isHost = participant.isHost,
                                        isCurrentUser = participant.isHost, // host is always the current user here
                                        isReady = participant.isReady,
                                        isEmpty = false,
                                        slotIndex = i,
                                    )
                                } else {
                                    ParticipantListRow(
                                        displayName = "",
                                        themeKey = "",
                                        isHost = false,
                                        isCurrentUser = false,
                                        isReady = false,
                                        isEmpty = true,
                                        slotIndex = i,
                                    )
                                }
                            }
                        }

                        if (uiState.canStart) {
                            Button(
                                onClick = handleStartSession,
                                enabled = !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = mc.lifePositive,
                                    contentColor = mc.background,
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
                                    Text(text = stringResource(R.string.lobby_start_button), style = ty.labelLarge)
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.lobby_waiting_players_msg),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
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

/** Card showing a single existing active session with Rejoin / Abandon actions. */
@Composable
private fun HostActiveSessionCard(
    session: ActiveSession,
    isLoading: Boolean,
    onRejoin: () -> Unit,
    onAbandon: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val statusLabel = when (session.status.uppercase()) {
        "ACTIVE" -> stringResource(R.string.lobby_status_in_progress)
        else -> stringResource(R.string.lobby_waiting_label)
    }
    val statusColor = when (session.status.uppercase()) {
        "ACTIVE" -> mc.lifePositive
        else -> mc.textSecondary
    }

    val modeDisplay = remember(session.gameMode) {
        GameMode.entries.find { it.name == session.gameMode.uppercase() }
    }

    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = session.code,
                    style = ty.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                    color = mc.primaryAccent,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = statusLabel, style = ty.labelSmall, color = statusColor)
                }
            }
            Text(
                text = "${modeDisplay?.displayName ?: session.gameMode} · ${session.playerCount} ${stringResource(R.string.lobby_player_count_suffix)}",
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRejoin,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(text = stringResource(R.string.lobby_action_rejoin), style = ty.labelMedium)
                }
                Button(
                    onClick = onAbandon,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.secondaryAccent,
                        contentColor = mc.background,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(text = stringResource(R.string.lobby_action_abandon), style = ty.labelMedium)
                }
            }
        }
    }
}

/** Horizontal scrollable row of color theme circles. */
@Composable
private fun HostThemeSelectorRow(
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
