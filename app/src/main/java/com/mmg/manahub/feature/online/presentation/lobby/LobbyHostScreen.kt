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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.nearby.presentation.rememberNearbyPermissionsState
import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.MagicTypography
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.GameMode

// ─────────────────────────────────────────────────────────────────────────────
//  LobbyHostScreen — stateful entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen lobby for the session host.
 *
 * The host selects a game mode and player count, taps "Create Room", then shares
 * the displayed 6-character code with other players. Once all players are ready
 * and conditions are met, the host can tap "Start Game".
 *
 * @param onNavigateBack Invoked when the back button is pressed or leave completes.
 * @param onGameStart Invoked when the session successfully transitions to ACTIVE.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LobbyHostScreen(
    onNavigateBack: () -> Unit,
    onGameStart: (sessionId: String, mode: GameMode, playerCount: Int) -> Unit,
    viewModel: LobbyHostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val permissionsState = rememberNearbyPermissionsState()
    val clipboardManager = LocalClipboardManager.current
    var pendingStart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: lobby_host")
    }

    LaunchedEffect(permissionsState.allPermissionsGranted, pendingStart) {
        if (pendingStart && permissionsState.allPermissionsGranted) {
            pendingStart = false
            viewModel.startSession(onGameStart)
        }
    }

    // Show error toasts reactively
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        toastState.show(error, MagicToastType.ERROR)
        viewModel.clearError()
    }

    val handleStartSession = {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startSession(onGameStart)
        } else {
            pendingStart = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    val copySuccessMsg = stringResource(R.string.lobby_code_copy_success)
    val onCopyCode = { code: String ->
        clipboardManager.setText(AnnotatedString(code))
        toastState.show(copySuccessMsg, MagicToastType.SUCCESS)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HexGridBackground(modifier = Modifier.fillMaxSize())

        LobbyHostContent(
            uiState = uiState,
            onNavigateBack = { viewModel.leaveSession(onNavigateBack) },
            onSetGameMode = viewModel::setGameMode,
            onSetPlayerCount = viewModel::setPlayerCount,
            onThemeChanged = viewModel::onThemeChanged,
            onCreateSession = viewModel::createSession,
            onStartSession = handleStartSession,
            onCopyCode = onCopyCode,
            onResumeSession = viewModel::resumeSession,
            onAbandonSession = viewModel::abandonSession,
            onRefresh = viewModel::refreshParticipants,
        )
        MagicToastHost(
            state = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LobbyHostContent — stateless layout
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyHostContent(
    uiState: LobbyHostViewModel.UiState,
    onNavigateBack: () -> Unit,
    onSetGameMode: (GameMode) -> Unit,
    onSetPlayerCount: (Int) -> Unit,
    onThemeChanged: (String) -> Unit,
    onCreateSession: () -> Unit,
    onStartSession: () -> Unit,
    onCopyCode: (String) -> Unit,
    onResumeSession: (ActiveSession) -> Unit,
    onAbandonSession: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lobby_host_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                },
                actions = {
                    if (uiState.sessionId != null) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = mc.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Header Section ────────────────────────────────────────────────
            if (uiState.sessionId == null) {
                item {
                    LobbyHeader(
                        icon = Icons.Default.People,
                        title = stringResource(R.string.lobby_host_title),
                        subtitle = stringResource(R.string.lobby_header_subtitle),
                        mc = mc,
                        ty = ty
                    )
                }
            }

            // ── Session code display (only shown after creation) ──────────────
            if (uiState.sessionCode != null) {
                item {
                    SessionCodeCard(
                        code = uiState.sessionCode,
                        onCopy = { onCopyCode(uiState.sessionCode) },
                        mc = mc,
                        ty = ty,
                    )
                }
            }

            // ── Active sessions list (shown before create form when sessions exist) ──
            if (uiState.existingSessions.isNotEmpty() && uiState.sessionId == null) {
                item {
                    SectionLabel(text = stringResource(R.string.lobby_active_rooms_title), mc = mc, ty = ty)
                }
                items(
                    items = uiState.existingSessions,
                    key = { it.sessionId },
                ) { session ->
                    ActiveSessionCard(
                        session = session,
                        isLoading = uiState.isLoading,
                        onRejoin = { onResumeSession(session) },
                        onAbandon = { onAbandonSession(session.sessionId) },
                        mc = mc,
                        ty = ty,
                    )
                }
            }

            // ── Game mode selector (only configurable before creation) ────────
            if (uiState.sessionId == null) {
                item {
                    SectionLabel(text = stringResource(R.string.lobby_section_game_mode), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    GameModeSelector(
                        selectedMode = uiState.gameMode,
                        onModeSelected = onSetGameMode,
                        mc = mc,
                        ty = ty,
                    )
                }

                item {
                    SectionLabel(text = stringResource(R.string.lobby_section_player_count), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerCountStepper(
                        count = uiState.playerCount,
                        onCountChanged = onSetPlayerCount,
                        mc = mc,
                        ty = ty,
                    )
                }

                item {
                    SectionLabel(text = stringResource(R.string.lobby_join_name_label), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = mc.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, mc.surfaceVariant),
                    ) {
                        Text(
                            text = uiState.displayName.ifBlank { stringResource(R.string.lobby_host_badge) },
                            style = ty.bodyLarge.copy(color = mc.textPrimary),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }

                item {
                    SectionLabel(text = stringResource(R.string.lobby_join_color_label), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    HostThemeSelector(
                        selectedKey = uiState.selectedThemeKey,
                        onThemeSelected = onThemeChanged,
                        mc = mc,
                        ty = ty,
                    )
                }

                item {
                    Button(
                        onClick = onCreateSession,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.background,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = mc.background,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.lobby_create_button),
                                style = ty.labelLarge,
                            )
                        }
                    }
                }
            }

            // ── Participant slots ─────────────────────────────────────────────
            if (uiState.sessionId != null) {
                item {
                    SectionLabel(
                        text = stringResource(
                            R.string.lobby_players_count,
                            uiState.participants.size,
                            uiState.playerCount
                        ),
                        mc = mc,
                        ty = ty
                    )
                }

                items(
                    items = buildSlotList(uiState.participants, uiState.playerCount),
                    key = { it.slotIndex },
                ) { slot ->
                    ParticipantSlotRow(slot = slot, mc = mc, ty = ty)
                }

                // ── Start button ──────────────────────────────────────────────
                item {
                    if (uiState.canStart) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStartSession,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.lifePositive,
                                contentColor = mc.background,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = mc.background,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.lobby_start_button),
                                    style = ty.labelLarge,
                                )
                            }
                        }
                    } else if (uiState.sessionId != null) {
                        Text(
                            text = stringResource(R.string.lobby_waiting_ready),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LobbyHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    mc: MagicColors,
    ty: MagicTypography
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(mc.primaryAccent.copy(alpha = 0.1f))
                .border(2.dp, mc.primaryAccent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = ty.titleLarge,
            color = mc.textPrimary
        )
        Text(
            text = subtitle,
            style = ty.bodyMedium,
            color = mc.textSecondary
        )
    }
}

/**
 * Prominent card that displays the 6-character session code in monospace font,
 * plus a button to copy it.
 */
@Composable
private fun SessionCodeCard(
    code: String,
    onCopy: () -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.lobby_code_label),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = code,
                    style = ty.lifeNumberMd.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                    ),
                    color = mc.primaryAccent,
                )

            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.surfaceVariant,
                    contentColor = mc.primaryAccent,
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.lobby_action_copy_code),
                    style = ty.labelLarge,
                )
            }
        }
    }
}

/** Row of [FilterChip]s for selecting a [GameMode]. */
@Composable
private fun GameModeSelector(
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameMode.entries, key = { it.name }) { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = {
                    Text(
                        text = mode.displayName,
                        style = ty.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent,
                    selectedLabelColor = mc.background,
                    containerColor = mc.surfaceVariant,
                    labelColor = mc.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = mc.surfaceVariant,
                    selectedBorderColor = mc.primaryAccent,
                    enabled = true,
                    selected = selectedMode == mode,
                ),
            )
        }
    }
}

/** Increment/decrement stepper for the player count (2–6). */
@Composable
private fun PlayerCountStepper(
    count: Int,
    onCountChanged: (Int) -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(
            onClick = { onCountChanged(count - 1) },
            enabled = count > 2,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(mc.surfaceVariant),
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.action_remove),
                tint = if (count > 2) mc.textPrimary else mc.textDisabled,
            )
        }
        Text(
            text = count.toString(),
            style = ty.displayMedium,
            color = mc.textPrimary,
        )
        IconButton(
            onClick = { onCountChanged(count + 1) },
            enabled = count < 6,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(mc.surfaceVariant),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_add),
                tint = if (count < 6) mc.textPrimary else mc.textDisabled,
            )
        }
        Text(
            text = stringResource(R.string.lobby_player_count_suffix),
            style = ty.bodyMedium,
            color = mc.textSecondary,
        )
    }
}

/** A single participant slot row, showing either an occupied or empty state. */
@Composable
private fun ParticipantSlotRow(
    slot: ParticipantSlot,
    mc: MagicColors,
    ty: MagicTypography,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .border(
                width = 1.dp,
                color = if (slot.participant != null) mc.primaryAccent.copy(alpha = 0.3f)
                else mc.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Slot index indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (slot.participant != null) mc.primaryAccent.copy(alpha = 0.2f)
                        else mc.surfaceVariant,
                    ),
            ) {
                if (slot.participant != null) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = (slot.slotIndex + 1).toString(),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }

            Column {
                Text(
                    text = slot.participant?.displayName ?: stringResource(R.string.lobby_waiting_player),
                    style = ty.bodyMedium,
                    color = if (slot.participant != null) mc.textPrimary else mc.textDisabled,
                )
                if (slot.participant?.isHost == true) {
                    Text(
                        text = stringResource(R.string.lobby_host_badge),
                        style = ty.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            }
        }

        // Ready badge
        if (slot.participant != null) {
            ReadyBadge(isReady = slot.participant.isReady, mc = mc, ty = ty)
        }
    }
}

/** Green check badge when ready, orange "Waiting" label otherwise. */
@Composable
private fun ReadyBadge(
    isReady: Boolean,
    mc: MagicColors,
    ty: MagicTypography,
) {
    if (isReady) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(mc.lifePositive.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.lobby_ready_label),
                    tint = mc.lifePositive,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.lobby_ready_label),
                    style = ty.labelSmall,
                    color = mc.lifePositive
                )
            }
        }
    } else {
        Text(
            text = stringResource(R.string.lobby_waiting_label),
            style = ty.labelSmall,
            color = mc.textDisabled,
        )
    }
}

/**
 * Card representing a single active session in the pre-create list.
 *
 * Displays the session code (monospace), game mode, player count, and status chip,
 * with "Rejoin" and "Abandon" actions.
 */
@Composable
private fun ActiveSessionCard(
    session: ActiveSession,
    isLoading: Boolean,
    onRejoin: () -> Unit,
    onAbandon: () -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
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
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f)),
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
                    Text(
                        text = statusLabel,
                        style = ty.labelSmall,
                        color = statusColor,
                    )
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = mc.background,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(R.string.lobby_action_rejoin), style = ty.labelMedium)
                    }
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

/** Uppercase section label with letter spacing. */
@Composable
private fun SectionLabel(
    text: String,
    mc: MagicColors,
    ty: MagicTypography,
) {
    Text(
        text = text,
        style = ty.labelMedium.copy(letterSpacing = 1.sp),
        color = mc.textSecondary
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Data helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single seat in the lobby, which may or may not be occupied.
 *
 * @property slotIndex Zero-based seat index.
 * @property participant The [OnlineParticipant] occupying this seat, or null if empty.
 */
private data class ParticipantSlot(
    val slotIndex: Int,
    val participant: OnlineParticipant?,
)

/**
 * Builds a fixed-length list of [ParticipantSlot]s padded to [playerCount] with empty slots.
 * Participants without a matching slot (index >= playerCount) are ignored.
 */
private fun buildSlotList(
    participants: List<OnlineParticipant>,
    playerCount: Int,
): List<ParticipantSlot> {
    val bySlot = participants.associateBy { it.slotIndex }
    return (0 until playerCount).map { index ->
        ParticipantSlot(slotIndex = index, participant = bySlot[index])
    }
}

/** Horizontal scrollable list of color theme circles for the host to choose from. */
@Composable
private fun HostThemeSelector(
    selectedKey: String,
    onThemeSelected: (String) -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
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

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF14020D)
@Composable
private fun LobbyHostContentPreview() {
    com.mmg.manahub.core.ui.theme.MagicTheme {
        LobbyHostContent(
            uiState = LobbyHostViewModel.UiState(
                sessionCode = "XK9T2A",
                sessionId = "preview-session",
                playerCount = 4,
                gameMode = GameMode.COMMANDER,
                participants = listOf(
                    OnlineParticipant(
                        id = "p1", sessionId = "s1", slotIndex = 0,
                        userId = "u1", displayName = "Archmage", themeKey = "Crimson",
                        isHost = true, isReady = true, status = ParticipantStatus.JOINED,
                        lastSeenAt = "",
                    ),
                    OnlineParticipant(
                        id = "p2", sessionId = "s1", slotIndex = 1,
                        userId = "u2", displayName = "DarkLord", themeKey = "Azure",
                        isHost = false, isReady = false, status = ParticipantStatus.JOINED,
                        lastSeenAt = "",
                    ),
                ),
            ),
            onNavigateBack = {},
            onSetGameMode = {},
            onSetPlayerCount = {},
            onThemeChanged = {},
            onCreateSession = {},
            onStartSession = {},
            onCopyCode = {},
            onResumeSession = {},
            onAbandonSession = {},
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14020D, name = "Active Sessions List")
@Composable
private fun LobbyHostActiveSessonsPreview() {
    com.mmg.manahub.core.ui.theme.MagicTheme {
        LobbyHostContent(
            uiState = LobbyHostViewModel.UiState(
                existingSessions = listOf(
                    com.mmg.manahub.core.online.domain.model.ActiveSession(
                        sessionId = "s1", code = "ABC123", status = "LOBBY",
                        gameMode = "COMMANDER", playerCount = 4,
                    ),
                    com.mmg.manahub.core.online.domain.model.ActiveSession(
                        sessionId = "s2", code = "XYZ789", status = "ACTIVE",
                        gameMode = "STANDARD", playerCount = 2,
                    ),
                ),
            ),
            onNavigateBack = {},
            onSetGameMode = {},
            onSetPlayerCount = {},
            onThemeChanged = {},
            onCreateSession = {},
            onStartSession = {},
            onCopyCode = {},
            onResumeSession = {},
            onAbandonSession = {},
            onRefresh = {},
        )
    }
}
