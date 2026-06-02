package com.mmg.manahub.feature.game.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.mmg.manahub.core.voice.domain.CommandGrammar
import com.mmg.manahub.core.voice.domain.VoiceCommand
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.GameModeSelector
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.PlayerEditSheet
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import com.mmg.manahub.feature.online.presentation.lobby.OnlineHostSheet
import com.mmg.manahub.feature.online.presentation.lobby.OnlineJoinSheet
import com.mmg.manahub.feature.tournament.presentation.TournamentsSheet

/**
 * Entry point for the game setup screen.
 *
 * Collects ViewModel state and delegates rendering to [GameSetupScreenContent].
 *
 * @param viewModel The Hilt-provided [GameSetupViewModel].
 * @param onBack Back navigation callback.
 * @param onStartGame Called when the local game should start.
 * @param onOnlineHostGameStart Called after online session creation succeeds (host path).
 * @param onOnlineJoinGameStart Called after online session join succeeds (join path).
 * @param onNavigateToTournamentSetup Called when the user wants to create a local tournament.
 * @param onNavigateToTournamentDetail Called with a tournament ID to open an existing tournament.
 * @param prefilledJoinCode Optional 6-digit code arriving from a deep link to auto-open the join sheet.
 * @param onNavigateToTournament Legacy no-op callback kept for backward compatibility.
 * @param onNavigateToOnline Legacy no-op callback kept for backward compatibility.
 * @param onNavigateToJoin Legacy no-op callback kept for backward compatibility.
 */
@Composable
fun GameSetupScreen(
    viewModel: GameSetupViewModel,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate, GameSettings) -> Unit,
    onOnlineHostGameStart: (sessionId: String, mode: GameMode, playerCount: Int) -> Unit,
    onOnlineJoinGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    onNavigateToTournamentSetup: () -> Unit,
    onNavigateToTournamentDetail: (Long) -> Unit,
    prefilledJoinCode: String? = null,
    // Legacy callbacks kept for backward compatibility with any remaining call sites.
    onNavigateToTournament: () -> Unit = {},
    onNavigateToOnline: (GameMode, Int) -> Unit = { _, _ -> },
    onNavigateToJoin: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceModelState by viewModel.voiceModelState.collectAsStateWithLifecycle()

    GameSetupScreenContent(
        uiState = uiState,
        voiceModelState = voiceModelState,
        onBack = onBack,
        onStartGame = onStartGame,
        onOnlineHostGameStart = onOnlineHostGameStart,
        onOnlineJoinGameStart = onOnlineJoinGameStart,
        onNavigateToTournamentSetup = onNavigateToTournamentSetup,
        onNavigateToTournamentDetail = onNavigateToTournamentDetail,
        prefilledJoinCode = prefilledJoinCode,
        onModeChange = viewModel::onModeChange,
        onPlayerCountChange = viewModel::onPlayerCountChange,
        onUpdatePlayerName = viewModel::updatePlayerName,
        onUpdatePlayerTheme = viewModel::updatePlayerTheme,
        onToggleLandReminder = viewModel::toggleLandReminder,
        onToggleVoiceLandReminder = viewModel::toggleVoiceLandReminder,
        onToggleVoiceEndTurn = viewModel::toggleVoiceEndTurn,
        onToggleVoiceLanguage = viewModel::toggleVoiceLanguage,
        onDownloadVoiceModel = viewModel::downloadVoiceModel,
        onLifeControlModeChange = viewModel::setLifeControlMode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameSetupScreenContent(
    uiState: GameSetupUiState,
    voiceModelState: VoiceModelState,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate, GameSettings) -> Unit,
    onOnlineHostGameStart: (sessionId: String, mode: GameMode, playerCount: Int) -> Unit,
    onOnlineJoinGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    onNavigateToTournamentSetup: () -> Unit,
    onNavigateToTournamentDetail: (Long) -> Unit,
    prefilledJoinCode: String?,
    onModeChange: (GameMode) -> Unit,
    onPlayerCountChange: (Int) -> Unit,
    onUpdatePlayerName: (Int, String) -> Unit,
    onUpdatePlayerTheme: (Int, PlayerThemeColors) -> Unit,
    onToggleLandReminder: () -> Unit,
    onToggleVoiceLandReminder: () -> Unit,
    onToggleVoiceEndTurn: () -> Unit,
    onToggleVoiceLanguage: (VoiceLanguage) -> Unit,
    onDownloadVoiceModel: () -> Unit,
    onLifeControlModeChange: (LifeControlMode) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Sheet visibility state
    var showFriendsSheet by remember { mutableStateOf(false) }
    var showOnlineHostSheet by remember { mutableStateOf(false) }
    var showOnlineJoinSheet by remember { mutableStateOf(false) }
    var showTournamentsSheet by remember { mutableStateOf(false) }
    var editingPlayerIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-open join sheet when a prefilled code arrives (deep link path).
    LaunchedEffect(prefilledJoinCode) {
        if (!prefilledJoinCode.isNullOrBlank()) {
            showOnlineJoinSheet = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(mc.background)) {
        HexGridBackground(modifier = Modifier.fillMaxSize(), color = mc.primaryAccent.copy(alpha = 0.05f))

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Surface(
                    color = mc.backgroundSecondary.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textSecondary,
                            )
                        }
                        Text(
                            text = stringResource(R.string.gamesetup_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).imePadding()) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 8 },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ── Mode selector ─────────────────────────────────────────────────
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.gamesetup_mode_label),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = mc.textSecondary,
                            )
                            GameModeSelector(
                                selectedMode = uiState.selectedMode,
                                onModeSelected = onModeChange,
                            )
                        }

                        // ── Player count stepper ──────────────────────────────────────────
                        com.mmg.manahub.core.ui.components.PlayerCountStepper(
                            playerCount = uiState.playerCount,
                            onPlayerCountChange = onPlayerCountChange,
                        )

                        // ── Player avatar strip ───────────────────────────────────────────
                        PlayerAvatarStrip(
                            playerConfigs = uiState.playerConfigs,
                            playerCount = uiState.playerCount,
                            onAvatarTap = { idx -> editingPlayerIndex = idx },
                        )

                        // ── Settings ──────────────────────────────────────────────────────
                        SettingsSection(
                            gameSettings = uiState.gameSettings,
                            voiceModelState = voiceModelState,
                            onToggleLandReminder = onToggleLandReminder,
                            onToggleVoiceLandReminder = onToggleVoiceLandReminder,
                            onToggleVoiceEndTurn = onToggleVoiceEndTurn,
                            onToggleVoiceLanguage = onToggleVoiceLanguage,
                            onDownloadVoiceModel = onDownloadVoiceModel,
                            onLifeControlModeChange = onLifeControlModeChange,
                        )

                        // ── Begin Game button ─────────────────────────────────────────────
                        val buttonScale by animateFloatAsState(
                            targetValue = if (visible) 1f else 0.9f,
                            animationSpec = tween(800, delayMillis = 400),
                            label = "buttonScale",
                        )

                        Button(
                            onClick = {
                                onStartGame(
                                    uiState.selectedMode,
                                    uiState.playerConfigs,
                                    uiState.selectedLayout,
                                    uiState.gameSettings,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .graphicsLayer {
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                    shadowElevation = 8f
                                    shape = RoundedCornerShape(12.dp)
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.primaryAccent,
                                contentColor = mc.background,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.gamesetup_begin_button).uppercase(),
                                style = MaterialTheme.magicTypography.titleLarge,
                                color = mc.background,
                            )
                        }

                        // ── Play with friends button ──────────────────────────────────────
                        OutlinedButton(
                            onClick = { showFriendsSheet = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = stringResource(R.string.gamesetup_play_with_friends),
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.primaryAccent,
                            )
                        }
                    }
                }
            }
        }

        // ── Sheet layer (rendered above Scaffold, inside the outer Box) ────────────

        if (showFriendsSheet) {
            FriendsRoutingSheet(
                onDismiss = { showFriendsSheet = false },
                onHostOnline = { showFriendsSheet = false; showOnlineHostSheet = true },
                onJoinWithCode = { showFriendsSheet = false; showOnlineJoinSheet = true },
                onTournaments = { showFriendsSheet = false; showTournamentsSheet = true },
            )
        }

        if (showOnlineHostSheet) {
            OnlineHostSheet(
                initialMode = uiState.selectedMode,
                initialPlayerCount = uiState.playerCount,
                initialDisplayName = uiState.playerConfigs.firstOrNull()?.name ?: "",
                initialThemeKey = uiState.playerConfigs.firstOrNull()?.theme?.name ?: "Crimson",
                onDismiss = { showOnlineHostSheet = false },
                onGameStart = { sessionId, mode, playerCount ->
                    showOnlineHostSheet = false
                    onOnlineHostGameStart(sessionId, mode, playerCount)
                },
            )
        }

        if (showOnlineJoinSheet) {
            OnlineJoinSheet(
                prefilledCode = prefilledJoinCode?.takeIf { it.isNotBlank() },
                initialDisplayName = uiState.playerConfigs.firstOrNull()?.name ?: "",
                initialThemeKey = uiState.playerConfigs.firstOrNull()?.theme?.name ?: "Crimson",
                onDismiss = { showOnlineJoinSheet = false },
                onGameStart = { sessionId, slotIndex, mode, playerCount ->
                    showOnlineJoinSheet = false
                    onOnlineJoinGameStart(sessionId, slotIndex, mode, playerCount)
                },
            )
        }

        if (showTournamentsSheet) {
            TournamentsSheet(
                initialDisplayName = uiState.playerConfigs.firstOrNull()?.name ?: "",
                initialThemeKey = uiState.playerConfigs.firstOrNull()?.theme?.name ?: "Crimson",
                onDismiss = { showTournamentsSheet = false },
                onCreateLocal = { showTournamentsSheet = false; onNavigateToTournamentSetup() },
                onOpenTournament = { id ->
                    showTournamentsSheet = false
                    onNavigateToTournamentDetail(id)
                },
                onNavigateToTournamentList = { showTournamentsSheet = false; onNavigateToTournamentSetup() },
                onOnlineJoinGameStart = { sessionId, slotIndex, mode, playerCount ->
                    showTournamentsSheet = false
                    onOnlineJoinGameStart(sessionId, slotIndex, mode, playerCount)
                },
            )
        }

        editingPlayerIndex?.let { idx ->
            val config = uiState.playerConfigs.getOrNull(idx)
            if (config != null) {
                PlayerEditSheet(
                    playerName = config.name,
                    playerTheme = config.theme,
                    isAppUser = config.isAppUser,
                    usedThemes = uiState.playerConfigs.filter { it.id != config.id }.map { it.theme },
                    onNameChanged = { onUpdatePlayerName(idx, it) },
                    onThemeSelected = { theme -> onUpdatePlayerTheme(idx, theme) },
                    onDismiss = { editingPlayerIndex = null },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player avatar strip
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A horizontal row of player avatar circles.
 *
 * Tapping an avatar opens [PlayerEditSheet] via [onAvatarTap].
 * Only the first [playerCount] entries from [playerConfigs] are shown.
 *
 * @param playerConfigs Full list of player configs.
 * @param playerCount How many entries to show.
 * @param onAvatarTap Called with the index of the tapped avatar.
 */
@Composable
private fun PlayerAvatarStrip(
    playerConfigs: List<PlayerConfig>,
    playerCount: Int,
    onAvatarTap: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(playerConfigs.take(playerCount)) { idx, config ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(config.theme.accent)
                        .clickable { onAvatarTap(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    // Inner white highlight circle
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                    )
                }
                Text(
                    text = when {
                        config.isAppUser -> stringResource(R.string.game_setup_you_badge)
                        config.name.isNotBlank() -> config.name.take(8)
                        else -> stringResource(R.string.gamesetup_player_placeholder, idx + 1)
                    },
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Friends routing sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A bottom sheet offering three entry points into the multiplayer flows:
 * host online, join with code, or tournaments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsRoutingSheet(
    onDismiss: () -> Unit,
    onHostOnline: () -> Unit,
    onJoinWithCode: () -> Unit,
    onTournaments: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
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
            Text(
                text = stringResource(R.string.gamesetup_play_with_friends_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(16.dp))

            FriendsSheetRow(
                emoji = "🌐",
                title = stringResource(R.string.gamesetup_friends_host_title),
                subtitle = stringResource(R.string.gamesetup_friends_host_subtitle),
                onClick = onHostOnline,
            )
            FriendsSheetRow(
                emoji = "🔗",
                title = stringResource(R.string.gamesetup_friends_join_title),
                subtitle = stringResource(R.string.gamesetup_friends_join_subtitle),
                onClick = onJoinWithCode,
            )
            FriendsSheetRow(
                emoji = "🏆",
                title = stringResource(R.string.gamesetup_friends_tournaments_title),
                subtitle = stringResource(R.string.gamesetup_friends_tournaments_subtitle),
                onClick = onTournaments,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * A single tappable row within [FriendsRoutingSheet].
 */
@Composable
private fun FriendsSheetRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = mc.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, style = ty.titleLarge)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ty.titleMedium, color = mc.textPrimary)
            Text(text = subtitle, style = ty.bodySmall, color = mc.textSecondary)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = mc.textDisabled,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Settings section (UNCHANGED — do not modify)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    gameSettings: GameSettings,
    voiceModelState: VoiceModelState,
    onToggleLandReminder: () -> Unit,
    onToggleVoiceLandReminder: () -> Unit,
    onToggleVoiceEndTurn: () -> Unit,
    onToggleVoiceLanguage: (VoiceLanguage) -> Unit,
    onDownloadVoiceModel: () -> Unit,
    onLifeControlModeChange: (LifeControlMode) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.gamesetup_settings_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(mc.surface.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LandReminderToggle(
                enabled = gameSettings.landReminderEnabled,
                onToggle = onToggleLandReminder,
            )
            VoiceControlsSection(
                gameSettings = gameSettings,
                voiceModelState = voiceModelState,
                onToggleLandVoice = onToggleVoiceLandReminder,
                onToggleEndTurnVoice = onToggleVoiceEndTurn,
                onToggleLanguage = onToggleVoiceLanguage,
                onDownloadModel = onDownloadVoiceModel,
            )
            LifeControlSelector(
                selectedMode = gameSettings.lifeControlMode,
                onModeChange = onLifeControlModeChange,
            )
        }
    }
}

@Composable
private fun LandReminderToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val iconTint by animateColorAsState(
        targetValue = if (enabled) mc.primaryAccent else mc.textDisabled,
        animationSpec = tween(300),
        label = "landIconTint",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_land),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.gamesetup_land_reminder_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = mc.primaryAccent,
                checkedThumbColor = mc.background,
                uncheckedTrackColor = mc.surfaceVariant,
                uncheckedThumbColor = mc.textDisabled,
            ),
        )
    }
}

@Composable
private fun LifeControlSelector(
    selectedMode: LifeControlMode,
    onModeChange: (LifeControlMode) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(
                text = stringResource(R.string.gamesetup_life_control_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeControlOptionTile(
                mode = LifeControlMode.SCROLL,
                label = stringResource(R.string.gamesetup_life_control_swipe),
                selected = selectedMode == LifeControlMode.SCROLL,
                onClick = { onModeChange(LifeControlMode.SCROLL) },
                modifier = Modifier.weight(1f),
            )
            LifeControlOptionTile(
                mode = LifeControlMode.TAP,
                label = stringResource(R.string.gamesetup_life_control_tap),
                selected = selectedMode == LifeControlMode.TAP,
                onClick = { onModeChange(LifeControlMode.TAP) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LifeControlOptionTile(
    mode: LifeControlMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val borderColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.surfaceVariant,
        animationSpec = tween(300),
        label = "tileBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        animationSpec = tween(300),
        label = "tileBg",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.textSecondary,
        animationSpec = tween(300),
        label = "tileTitle",
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.magicTypography.titleMedium,
                color = titleColor,
            )
            if (mode == LifeControlMode.SCROLL) {
                ScrollModePreview()
            } else {
                TapModePreview()
            }
        }
    }
}

@Composable
private fun ScrollModePreview() {
    val mc = MaterialTheme.magicColors
    var lifeValue by remember { mutableStateOf(20) }
    var isGoingUp by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val sequence = listOf(21, 20, 19, 20)
        val goingUp = listOf(true, false, false, true)
        var index = 0
        while (true) {
            delay(1500L)
            isGoingUp = goingUp[index]
            lifeValue = sequence[index]
            index = (index + 1) % sequence.size
        }
    }

    AnimatedContent(
        targetState = lifeValue,
        transitionSpec = {
            if (isGoingUp) {
                (slideInVertically { h -> -h } + fadeIn()) togetherWith
                    (slideOutVertically { h -> h } + fadeOut())
            } else {
                (slideInVertically { h -> h } + fadeIn()) togetherWith
                    (slideOutVertically { h -> -h } + fadeOut())
            }
        },
        label = "scrollPreviewLife",
    ) { life ->
        Text(
            text = life.toString(),
            style = MaterialTheme.magicTypography.displayMedium,
            color = mc.textPrimary,
        )
    }
}

@Composable
private fun TapModePreview() {
    val mc = MaterialTheme.magicColors
    var lifeValue by remember { mutableStateOf(20) }
    var lastDelta by remember { mutableStateOf(0) }
    var pulseTrigger by remember { mutableStateOf(0) }

    val numberScale = remember { Animatable(1f) }
    val heartScale = remember { Animatable(1f) }
    val floatY = remember { Animatable(0f) }
    val floatAlpha = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val sequence = listOf(19, 20, 21, 20)
        var index = 0
        while (true) {
            delay(1400L)
            val next = sequence[index]
            lastDelta = next - lifeValue
            lifeValue = next
            pulseTrigger++
            index = (index + 1) % sequence.size
        }
    }

    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger == 0) return@LaunchedEffect
        numberScale.snapTo(0.82f)
        floatY.snapTo(0f)
        floatAlpha.snapTo(1f)
        scope.launch {
            numberScale.animateTo(1.15f, spring(dampingRatio = 0.35f, stiffness = 700f))
            numberScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
        if (lastDelta > 0) {
            scope.launch {
                heartScale.snapTo(1f)
                heartScale.animateTo(1.5f, spring(dampingRatio = 0.3f, stiffness = 700f))
                heartScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
            }
        }
        scope.launch { floatY.animateTo(-26f, tween(700)) }
        floatAlpha.animateTo(0f, tween(700, delayMillis = 200))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = lifeValue.toString(),
            style = MaterialTheme.magicTypography.displayMedium,
            color = mc.textPrimary,
            modifier = Modifier.graphicsLayer {
                scaleX = numberScale.value
                scaleY = numberScale.value
            },
        )
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = mc.lifePositive,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                },
        )
    }
}

/** Identifies which voice toggle requested microphone permission, so the grant callback can re-dispatch. */
private enum class PendingVoiceToggle { LAND, END_TURN }

/**
 * Full voice-controls block: shared model-download affordance, language selector,
 * and one toggle row per voice command (Land reminder, End turn). Each toggle handles
 * the RECORD_AUDIO permission flow and exposes an info dialog listing recognized phrases.
 */
@Composable
private fun VoiceControlsSection(
    gameSettings: GameSettings,
    voiceModelState: VoiceModelState,
    onToggleLandVoice: () -> Unit,
    onToggleEndTurnVoice: () -> Unit,
    onToggleLanguage: (VoiceLanguage) -> Unit,
    onDownloadModel: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val context = LocalContext.current
    var showLandInfoDialog by remember { mutableStateOf(false) }
    var showEndTurnInfoDialog by remember { mutableStateOf(false) }
    var pendingVoiceToggle by remember { mutableStateOf<PendingVoiceToggle?>(null) }
    val isModelReady = voiceModelState is VoiceModelState.Ready

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && voiceModelState is VoiceModelState.Ready) {
            when (pendingVoiceToggle) {
                PendingVoiceToggle.LAND -> onToggleLandVoice()
                PendingVoiceToggle.END_TURN -> onToggleEndTurnVoice()
                null -> {}
            }
        }
        pendingVoiceToggle = null
    }

    // Requests permission if needed, otherwise toggles immediately.
    fun handleToggle(isEnabled: Boolean, which: PendingVoiceToggle, toggle: () -> Unit) {
        if (isEnabled) {
            toggle()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                toggle()
            } else {
                pendingVoiceToggle = which
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Model download / progress / error affordance (shared by both toggles).
        AnimatedVisibility(visible = !isModelReady) {
            when (voiceModelState) {
                is VoiceModelState.NotDownloaded -> TextButton(
                    onClick = onDownloadModel,
                ) {
                    Text(
                        text = stringResource(R.string.gamesetup_voice_download_label),
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.primaryAccent,
                    )
                }
                is VoiceModelState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.gamesetup_voice_downloading_label),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                    LinearProgressIndicator(
                        progress = { voiceModelState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = mc.primaryAccent,
                        trackColor = mc.surfaceVariant,
                    )
                }
                is VoiceModelState.Error -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.gamesetup_voice_download_failed),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifeNegative,
                    )
                    TextButton(onClick = onDownloadModel) {
                        Text(
                            text = stringResource(R.string.retry),
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.primaryAccent,
                        )
                    }
                }
                else -> {}
            }
        }

        // ── Language selector ─────────────────────────────────────────
        AnimatedVisibility(visible = isModelReady || voiceModelState !is VoiceModelState.NotDownloaded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.gamesetup_voice_languages_title),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        val selected = lang in gameSettings.voiceLanguages
                        val bgColor by animateColorAsState(
                            if (selected) mc.primaryAccent.copy(alpha = 0.18f) else mc.surface,
                            tween(200), label = "langBg"
                        )
                        val borderColor by animateColorAsState(
                            if (selected) mc.primaryAccent else mc.surfaceVariant,
                            tween(200), label = "langBorder"
                        )
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onToggleLanguage(lang) },
                                ),
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(if (selected) 1.5.dp else 0.5.dp, borderColor),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = lang.displayFlag, style = MaterialTheme.magicTypography.bodyMedium)
                                Text(
                                    text = lang.displayCode,
                                    style = MaterialTheme.magicTypography.labelMedium,
                                    color = if (selected) mc.primaryAccent else mc.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Voice: Land Reminder row ──────────────────────────────────
        VoiceFeatureToggleRow(
            iconResId = R.drawable.ic_land,
            title = stringResource(R.string.gamesetup_voice_land_reminder_title),
            subtitle = stringResource(R.string.gamesetup_voice_land_reminder_subtitle),
            enabled = gameSettings.voiceLandReminderEnabled,
            isModelReady = isModelReady,
            onToggle = {
                handleToggle(gameSettings.voiceLandReminderEnabled, PendingVoiceToggle.LAND, onToggleLandVoice)
            },
            onInfoClick = { showLandInfoDialog = true },
        )

        // ── Voice: End Turn row ───────────────────────────────────────
        VoiceFeatureToggleRow(
            iconResId = null,
            iconVector = Icons.AutoMirrored.Filled.ArrowForward,
            title = stringResource(R.string.gamesetup_voice_end_turn_title),
            subtitle = stringResource(R.string.gamesetup_voice_end_turn_subtitle),
            enabled = gameSettings.voiceEndTurnEnabled,
            isModelReady = isModelReady,
            onToggle = {
                handleToggle(gameSettings.voiceEndTurnEnabled, PendingVoiceToggle.END_TURN, onToggleEndTurnVoice)
            },
            onInfoClick = { showEndTurnInfoDialog = true },
        )
    }

    // Info dialogs
    if (showLandInfoDialog) {
        VoicePhrasesInfoDialog(
            title = stringResource(R.string.gamesetup_voice_land_phrases_title),
            command = VoiceCommand.PlayLand,
            enabledLanguages = gameSettings.voiceLanguages,
            onDismiss = { showLandInfoDialog = false },
        )
    }
    if (showEndTurnInfoDialog) {
        VoicePhrasesInfoDialog(
            title = stringResource(R.string.gamesetup_voice_end_turn_phrases_title),
            command = VoiceCommand.EndTurn,
            enabledLanguages = gameSettings.voiceLanguages,
            onDismiss = { showEndTurnInfoDialog = false },
        )
    }
}

/**
 * A single voice-command toggle row: leading icon (drawable or vector), title + subtitle,
 * an info button, and a switch gated on model readiness.
 */
@Composable
private fun VoiceFeatureToggleRow(
    iconResId: Int?,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    title: String,
    subtitle: String,
    enabled: Boolean,
    isModelReady: Boolean,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val iconTint by animateColorAsState(
        targetValue = if (enabled) mc.primaryAccent else mc.textDisabled,
        animationSpec = tween(300), label = "voiceIconTint",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            iconResId != null -> Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            iconVector != null -> Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
            Text(text = subtitle, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(R.string.action_info),
                tint = mc.textDisabled,
                modifier = Modifier.size(18.dp),
            )
        }
        Switch(
            checked = enabled,
            enabled = isModelReady,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = mc.primaryAccent,
                checkedThumbColor = mc.background,
                uncheckedTrackColor = mc.surfaceVariant,
                uncheckedThumbColor = mc.textDisabled,
                disabledCheckedTrackColor = mc.surfaceVariant,
                disabledUncheckedTrackColor = mc.surfaceVariant,
            ),
        )
    }
}

/**
 * Dialog listing the recognized voice phrases for a given command, grouped by enabled language.
 */
@Composable
private fun VoicePhrasesInfoDialog(
    title: String,
    command: VoiceCommand,
    enabledLanguages: Set<VoiceLanguage>,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val phrases = CommandGrammar.allEntries
        .filter { it.command == command && it.language in enabledLanguages }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = mc.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (phrases.isEmpty()) {
                    Text(
                        text = stringResource(R.string.gamesetup_voice_no_phrases),
                        color = mc.textSecondary,
                        style = MaterialTheme.magicTypography.bodyMedium,
                    )
                } else {
                    VoiceLanguage.entries.filter { it in enabledLanguages }.forEach { lang ->
                        val langPhrases = phrases.filter { it.language == lang }
                        if (langPhrases.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = lang.displayFlag, style = MaterialTheme.magicTypography.bodyMedium)
                                Text(
                                    text = lang.displayCode,
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.textSecondary,
                                )
                            }
                            langPhrases.forEach { entry ->
                                Row(
                                    modifier = Modifier.padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("•", color = mc.primaryAccent, style = MaterialTheme.magicTypography.bodyMedium)
                                    Text(
                                        text = "\"${entry.phrase}\"",
                                        color = mc.textPrimary,
                                        style = MaterialTheme.magicTypography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok), color = mc.primaryAccent)
            }
        },
        containerColor = mc.surface,
    )
}
