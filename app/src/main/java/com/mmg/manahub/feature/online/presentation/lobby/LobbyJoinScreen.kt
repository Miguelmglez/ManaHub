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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.google.firebase.crashlytics.FirebaseCrashlytics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.mmg.manahub.core.nearby.presentation.rememberNearbyPermissionsState

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.MagicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  LobbyJoinScreen — stateful entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen lobby for a player joining an existing session by code.
 *
 * Flow:
 * 1. Enter 6-char code, display name, and choose a color theme.
 * 2. Tap "Join" — backend assigns a slot.
 * 3. View participant list; toggle the "Ready" switch.
 * 4. When the host starts the game, the screen auto-navigates via [onGameStart].
 *
 * @param prefilledCode Optional code pre-filled by a deep link or invite.
 * @param onNavigateBack Invoked when leaving the lobby.
 * @param onGameStart Invoked when [OnlineSessionStatus.ACTIVE] is received.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LobbyJoinScreen(
    prefilledCode: String = "",
    onNavigateBack: () -> Unit,
    onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    viewModel: LobbyJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val permissionsState = rememberNearbyPermissionsState()
    var pendingJoin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: lobby_join")
        if (prefilledCode.isNotBlank()) {
            FirebaseCrashlytics.getInstance().log("lobby_join_deep_link_prefill: code_length=${prefilledCode.length}")
        }
    }

    // Pre-fill code from deep link on first composition
    LaunchedEffect(prefilledCode) {
        if (prefilledCode.isNotBlank()) {
            viewModel.prefillCode(prefilledCode)
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted, pendingJoin) {
        if (pendingJoin && permissionsState.allPermissionsGranted) {
            pendingJoin = false
            viewModel.joinSession(onGameStart)
        }
    }

    // Show error toasts reactively
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        toastState.show(error, MagicToastType.ERROR)
        viewModel.clearError()
    }

    val handleJoinSession = {
        if (permissionsState.allPermissionsGranted) {
            viewModel.joinSession(onGameStart)
        } else {
            pendingJoin = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HexGridBackground(modifier = Modifier.fillMaxSize())

        LobbyJoinContent(
            uiState = uiState,
            onNavigateBack = { viewModel.leaveSession(onNavigateBack) },
            onCodeChanged = viewModel::onCodeChanged,
            onDisplayNameChanged = viewModel::onDisplayNameChanged,
            onThemeChanged = viewModel::onThemeChanged,
            onJoinSession = handleJoinSession,
            onSetReady = viewModel::setReady,
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
//  LobbyJoinContent — stateless layout
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyJoinContent(
    uiState: LobbyJoinViewModel.UiState,
    onNavigateBack: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onThemeChanged: (String) -> Unit,
    onJoinSession: () -> Unit,
    onSetReady: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val hasJoined = uiState.sessionId != null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (hasJoined) stringResource(R.string.lobby_waiting_room_title) 
                               else stringResource(R.string.lobby_join_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = mc.textPrimary,
                        )
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
            if (!hasJoined) {
                // ── Header Section ─────────────────────────────────────────────
                item {
                    LobbyHeader(
                        icon = Icons.Default.GroupAdd,
                        title = stringResource(R.string.lobby_join_title),
                        subtitle = stringResource(R.string.lobby_header_subtitle),
                        mc = mc,
                        ty = ty
                    )
                }

                // ── Pre-join form ─────────────────────────────────────────────
                item {
                    SectionLabel(text = stringResource(R.string.lobby_code_label), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.codeInput,
                        onValueChange = onCodeChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.lobby_join_code_hint),
                                style = ty.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                color = mc.textDisabled,
                            )
                        },
                        textStyle = ty.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 8.sp,
                            color = mc.primaryAccent,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                            cursorColor = mc.primaryAccent,
                            focusedContainerColor = mc.surface,
                            unfocusedContainerColor = mc.surface,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
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
                            text = uiState.displayName.ifBlank { "Player" },
                            style = ty.bodyLarge.copy(color = mc.textPrimary),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }

                item {
                    SectionLabel(text = stringResource(R.string.lobby_join_color_label), mc = mc, ty = ty)
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeSelector(
                        selectedKey = uiState.selectedThemeKey,
                        onThemeSelected = onThemeChanged,
                        mc = mc,
                        ty = ty,
                    )
                }

                item {
                    Button(
                        onClick = onJoinSession,
                        enabled = !uiState.isLoading && uiState.codeInput.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                            contentColor = mc.background,
                            disabledContainerColor = mc.surfaceVariant,
                            disabledContentColor = mc.textDisabled,
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
                            Text(text = stringResource(R.string.lobby_join_button), style = ty.labelLarge)
                        }
                    }
                }
            } else {
                // ── Post-join: waiting room ───────────────────────────────────
                item {
                    SectionLabel(
                        text = stringResource(R.string.lobby_players_count_joined, uiState.participants.size),
                        mc = mc,
                        ty = ty,
                    )
                }

                items(
                    items = uiState.participants.sortedBy { it.slotIndex },
                    key = { it.id },
                ) { participant ->
                    JoinedParticipantRow(participant = participant, mc = mc, ty = ty)
                }

                if (uiState.sessionStatus == OnlineSessionStatus.ACTIVE) {
                    item {
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
 * Horizontal scrollable list of color theme circles for the player to choose from.
 */
@Composable
private fun ThemeSelector(
    selectedKey: String,
    onThemeSelected: (String) -> Unit,
    mc: MagicColors,
    ty: MagicTypography,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(PlayerTheme.ALL) { theme ->
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
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = mc.background,
                            modifier = Modifier.size(24.dp)
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

/** Row displaying a participant's name, host badge, and ready status. */
@Composable
private fun JoinedParticipantRow(
    participant: OnlineParticipant,
    mc: MagicColors,
    ty: MagicTypography,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .border(
                width = 1.dp,
                color = if (participant.isReady) mc.lifePositive.copy(alpha = 0.4f)
                else mc.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.2f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = participant.displayName,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                )
                if (participant.isHost) {
                    Text(
                        text = stringResource(R.string.lobby_host_badge),
                        style = ty.labelSmall,
                        color = mc.goldMtg
                    )
                }
            }
        }

        if (participant.isReady) {
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
                        contentDescription = "Ready",
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
                color = mc.textDisabled
            )
        }
    }
}

/** Uppercase section label helper. */
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
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF14020D)
@Composable
private fun LobbyJoinPreJoinPreview() {
    com.mmg.manahub.core.ui.theme.MagicTheme {
        LobbyJoinContent(
            uiState = LobbyJoinViewModel.UiState(
                codeInput = "XK9T2A",
                displayName = "Archmage",
                selectedThemeKey = "Crimson",
            ),
            onNavigateBack = {},
            onCodeChanged = {},
            onDisplayNameChanged = {},
            onThemeChanged = {},
            onJoinSession = {},
            onSetReady = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF14020D)
@Composable
private fun LobbyJoinWaitingRoomPreview() {
    com.mmg.manahub.core.ui.theme.MagicTheme {
        LobbyJoinContent(
            uiState = LobbyJoinViewModel.UiState(
                sessionId = "preview-session",
                slotIndex = 1,
                isReady = false,
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
            onCodeChanged = {},
            onDisplayNameChanged = {},
            onThemeChanged = {},
            onJoinSession = {},
            onSetReady = {},
        )
    }
}
