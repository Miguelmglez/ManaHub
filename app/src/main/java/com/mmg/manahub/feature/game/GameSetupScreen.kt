package com.mmg.manahub.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.model.GameMode
import com.mmg.manahub.feature.game.model.LayoutTemplate

@Composable
fun GameSetupScreen(
    viewModel: GameSetupViewModel,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate) -> Unit,
    onNavigateToTournament: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GameSetupScreenContent(
        uiState = uiState,
        onBack = onBack,
        onStartGame = onStartGame,
        onNavigateToTournament = onNavigateToTournament,
        onModeChange = viewModel::onModeChange,
        onPlayerCountChange = viewModel::onPlayerCountChange,
        onUpdatePlayerName = viewModel::updatePlayerName,
        onUpdatePlayerTheme = viewModel::updatePlayerTheme
    )
}

@Composable
private fun GameSetupScreenContent(
    uiState: GameSetupUiState,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate) -> Unit,
    onNavigateToTournament: () -> Unit,
    onModeChange: (GameMode) -> Unit,
    onPlayerCountChange: (Int) -> Unit,
    onUpdatePlayerName: (Int, String) -> Unit,
    onUpdatePlayerTheme: (Int, PlayerThemeColors) -> Unit,
) {
    val navController = rememberNavController()
    val activity = LocalContext.current as ComponentActivity
    val gameVm: GameViewModel = hiltViewModel(activity)
    val gameUiState by gameVm.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val hasActiveGame = gameUiState.players.isNotEmpty() && gameUiState.winner == null

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textSecondary
                        )
                    }
                    Text(
                        text = stringResource(R.string.gamesetup_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Mode selector ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.gamesetup_mode_label),
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameMode.entries.forEach { mode ->
                        ModeTile(
                            mode = mode,
                            selected = mode == uiState.selectedMode,
                            onClick = { onModeChange(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Player count stepper ──────────────────────────────────────────
            PlayerCountStepper(
                playerCount = uiState.playerCount,
                onPlayerCountChange = onPlayerCountChange,
            )

            // ── Player config list ────────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(uiState.playerConfigs) { index, config ->
                    PlayerConfigRow(
                        config = config,
                        usedThemes = uiState.playerConfigs
                            .filter { it.id != config.id }
                            .map { it.theme },
                        onNameChange = { name -> onUpdatePlayerName(index, name) },
                        onThemeChange = { theme -> onUpdatePlayerTheme(index, theme) },
                    )
                }
            }

            // ── Begin button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    onStartGame(
                        uiState.selectedMode,
                        uiState.playerConfigs,
                        uiState.selectedLayout
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    stringResource(R.string.gamesetup_begin_button),
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = mc.background,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = onNavigateToTournament) {
                Text(
                    stringResource(R.string.gamesetup_tournament_link),
                    color = mc.goldMtg,
                    style = MaterialTheme.magicTypography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player count stepper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerCountStepper(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.gamesetup_players_label),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.textSecondary,
            modifier = Modifier.weight(1f),
        )
        // Decrement button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mc.surface)
                .clickable(enabled = playerCount > 2) {
                    onPlayerCountChange(playerCount - 1)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "−",
                style = MaterialTheme.magicTypography.titleLarge,
                color = if (playerCount > 2) mc.textPrimary else mc.textDisabled,
            )
        }
        // Current count
        Text(
            text = "$playerCount",
            style = MaterialTheme.magicTypography.displayMedium,
            color = mc.primaryAccent,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center,
        )
        // Increment button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mc.surface)
                .clickable(enabled = playerCount < 6) {
                    onPlayerCountChange(playerCount + 1)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = MaterialTheme.magicTypography.titleLarge,
                color = if (playerCount < 6) mc.textPrimary else mc.textDisabled,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single player config row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerConfigRow(
    config: PlayerConfig,
    usedThemes: List<PlayerThemeColors>,
    onNameChange: (String) -> Unit,
    onThemeChange: (PlayerThemeColors) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Color circle — tap to open picker
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(config.theme.accent)
                .border(2.dp, config.theme.accent.copy(alpha = 0.5f), CircleShape)
                .clickable { showColorPicker = true },
        )

        // Name field — read-only for the app user (Player 1)
        if (config.isAppUser) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = config.name.ifBlank { stringResource(R.string.game_setup_default_player_name) },
                    style = MaterialTheme.magicTypography.bodyLarge,
                    color = if (config.isDefaultName) mc.textDisabled else mc.primaryAccent,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = mc.primaryAccent.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = stringResource(R.string.game_setup_you_badge),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (config.isDefaultName) {
                    Text(
                        text = stringResource(R.string.game_setup_set_name_hint),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textDisabled,
                    )
                }
            }
        } else {
            BasicTextField(
                value = config.name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.magicTypography.bodyLarge.copy(color = mc.textPrimary),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (config.name.isEmpty()) {
                            Text(
                                stringResource(R.string.gamesetup_player_name_hint, config.id + 1),
                                style = MaterialTheme.magicTypography.bodyLarge,
                                color = mc.textDisabled,
                            )
                        }
                        inner()
                    }
                },
            )
        }

    }

    if (showColorPicker) {
        ColorPickerSheet(
            availableThemes = PlayerTheme.ALL.filter { it !in usedThemes },
            onSelect = { theme ->
                onThemeChange(theme)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Color picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(
    availableThemes: List<PlayerThemeColors>,
    onSelect: (PlayerThemeColors) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.gamesetup_choose_color),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                items(availableThemes) { theme ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onSelect(theme) },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(theme.accent),
                        )
                        Text(
                            text = theme.name,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Mode tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeTile(
    mode: GameMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val borderColor = if (selected) mc.primaryAccent else mc.surfaceVariant
    val bgColor = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (mode == GameMode.COMMANDER) "⚔️" else "🔮",
            style = MaterialTheme.magicTypography.displayMedium,
        )
        Text(
            text = mode.displayName,
            style = MaterialTheme.magicTypography.titleMedium,
            color = if (selected) mc.primaryAccent else mc.textPrimary,
        )
        Text(
            text = "${mode.startingLife} life",
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textSecondary,
        )
    }
}
