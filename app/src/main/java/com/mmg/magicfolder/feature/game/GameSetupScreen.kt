package com.mmg.magicfolder.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.GameMode
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    viewModel:   GameSetupViewModel,
    onBack:                () -> Unit,
    onStartGame:           (GameMode, List<PlayerConfig>) -> Unit,
    onNavigateToTournament: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Game",
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = mc.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement   = Arrangement.spacedBy(28.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text      = "ManaHub",
                style     = MaterialTheme.magicTypography.displayMedium,
                color     = mc.goldMtg,
                textAlign = TextAlign.Center,
            )

            // ── Mode selector ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Game Mode",
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameMode.entries.forEach { mode ->
                        ModeTile(
                            mode     = mode,
                            selected = mode == uiState.selectedMode,
                            onClick  = { viewModel.onModeChange(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Player count stepper ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerCountStepper(
                    playerCount       = uiState.playerCount,
                    onPlayerCountChange = viewModel::onPlayerCountChange,
                )
            }

            // ── Player config list ────────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                itemsIndexed(uiState.playerConfigs) { index, config ->
                    PlayerConfigRow(
                        config      = config,
                        usedThemes  = uiState.playerConfigs
                            .filter { it.id != config.id }
                            .map { it.theme },
                        onNameChange  = { name -> viewModel.updatePlayerName(index, name) },
                        onThemeChange = { theme -> viewModel.updatePlayerTheme(index, theme) },
                    )
                }
            }

            // ── Mini grid preview ─────────────────────────────────────────────
            if (uiState.playerConfigs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tap a player to change position",
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                    MiniGridPreview(
                        playerConfigs    = uiState.playerConfigs,
                        onSwapPositions  = viewModel::swapPlayerPositions,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Begin button ──────────────────────────────────────────────────
            Button(
                onClick  = { onStartGame(uiState.selectedMode, uiState.playerConfigs) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape    = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Begin the Game",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.background,
                )
            }

            TextButton(onClick = onNavigateToTournament) {
                Text(
                    "Create a tournament instead →",
                    color = mc.textSecondary,
                    style = MaterialTheme.magicTypography.bodySmall,
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
    playerCount:        Int,
    onPlayerCountChange: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Players",
            style    = MaterialTheme.magicTypography.labelLarge,
            color    = mc.textSecondary,
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
            text      = "$playerCount",
            style     = MaterialTheme.magicTypography.displayMedium,
            color     = mc.primaryAccent,
            modifier  = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center,
        )
        // Increment button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mc.surface)
                .clickable(enabled = playerCount < 10) {
                    onPlayerCountChange(playerCount + 1)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = MaterialTheme.magicTypography.titleLarge,
                color = if (playerCount < 10) mc.textPrimary else mc.textDisabled,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single player config row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerConfigRow(
    config:       PlayerConfig,
    usedThemes:   List<PlayerThemeColors>,
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
        verticalAlignment     = Alignment.CenterVertically,
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

        // Name field
        BasicTextField(
            value       = config.name,
            onValueChange = onNameChange,
            modifier    = Modifier.weight(1f),
            textStyle   = MaterialTheme.magicTypography.bodyLarge.copy(color = mc.textPrimary),
            singleLine  = true,
            decorationBox = { inner ->
                Box {
                    if (config.name.isEmpty()) {
                        Text(
                            "Player ${config.id + 1}",
                            style = MaterialTheme.magicTypography.bodyLarge,
                            color = mc.textDisabled,
                        )
                    }
                    inner()
                }
            },
        )

        // Position badge
        Text(
            text  = "P${config.id + 1}",
            style = MaterialTheme.magicTypography.labelSmall,
            color = config.theme.accent.copy(alpha = 0.6f),
        )
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
    onSelect:        (PlayerThemeColors) -> Unit,
    onDismiss:       () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Choose color",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns               = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.heightIn(max = 200.dp),
            ) {
                items(availableThemes) { theme ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier            = Modifier.clickable { onSelect(theme) },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(theme.accent),
                        )
                        Text(
                            text      = theme.name,
                            style     = MaterialTheme.magicTypography.labelSmall,
                            color     = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini grid preview — shows player layout, tap to swap positions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniGridPreview(
    playerConfigs:   List<PlayerConfig>,
    onSwapPositions: (Int, Int) -> Unit,
    modifier:        Modifier = Modifier,
) {
    val columns = when (playerConfigs.size) {
        2    -> 1
        3    -> 1
        4    -> 2
        5, 6 -> 2
        else -> 4
    }
    val rows = ceil(playerConfigs.size.toDouble() / columns).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (columns == 1) 2f else 1.5f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (col in 0 until columns) {
                    val index  = row * columns + col
                    val config = playerConfigs.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                config?.theme?.accent?.copy(alpha = 0.3f)
                                    ?: Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = config?.theme?.accent?.copy(alpha = 0.5f)
                                    ?: Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                if (config != null) {
                                    val nextIndex = (index + 1).coerceAtMost(playerConfigs.size - 1)
                                    onSwapPositions(index, nextIndex)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (config != null) {
                            Text(
                                text     = config.name.ifEmpty { "P${config.id + 1}" },
                                style    = MaterialTheme.magicTypography.labelSmall,
                                color    = config.theme.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
                // Empty filler cell for odd player counts
                if (columns > 1 &&
                    row == rows - 1 &&
                    playerConfigs.size % columns != 0
                ) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mode tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeTile(
    mode:     GameMode,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc          = MaterialTheme.magicColors
    val borderColor = if (selected) mc.primaryAccent else mc.surfaceVariant
    val bgColor     = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface

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
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text  = if (mode == GameMode.COMMANDER) "⚔" else "🔮",
            style = MaterialTheme.magicTypography.displayMedium,
        )
        Text(
            text  = mode.displayName,
            style = MaterialTheme.magicTypography.titleMedium,
            color = if (selected) mc.primaryAccent else mc.textPrimary,
        )
        Text(
            text  = "${mode.startingLife} life",
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textSecondary,
        )
    }
}
