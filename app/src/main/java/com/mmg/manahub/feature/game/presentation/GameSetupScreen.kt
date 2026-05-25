package com.mmg.manahub.feature.game.presentation

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import kotlinx.coroutines.launch

@Composable
fun GameSetupScreen(
    viewModel: GameSetupViewModel,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate, GameSettings) -> Unit,
    onNavigateToTournament: () -> Unit,
    onNavigateToOnline: (GameMode, Int) -> Unit = { _, _ -> },
    onNavigateToJoin: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GameSetupScreenContent(
        uiState = uiState,
        onBack = onBack,
        onStartGame = onStartGame,
        onNavigateToTournament = onNavigateToTournament,
        onNavigateToOnline = onNavigateToOnline,
        onNavigateToJoin = onNavigateToJoin,
        onModeChange = viewModel::onModeChange,
        onPlayerCountChange = viewModel::onPlayerCountChange,
        onUpdatePlayerName = viewModel::updatePlayerName,
        onUpdatePlayerTheme = viewModel::updatePlayerTheme,
        onToggleLandReminder = viewModel::toggleLandReminder,
        onLifeControlModeChange = viewModel::setLifeControlMode,
    )
}

@Composable
private fun GameSetupScreenContent(
    uiState: GameSetupUiState,
    onBack: () -> Unit,
    onStartGame: (GameMode, List<PlayerConfig>, LayoutTemplate, GameSettings) -> Unit,
    onNavigateToTournament: () -> Unit,
    onNavigateToOnline: (GameMode, Int) -> Unit,
    onNavigateToJoin: () -> Unit,
    onModeChange: (GameMode) -> Unit,
    onPlayerCountChange: (Int) -> Unit,
    onUpdatePlayerName: (Int, String) -> Unit,
    onUpdatePlayerTheme: (Int, PlayerThemeColors) -> Unit,
    onToggleLandReminder: () -> Unit,
    onLifeControlModeChange: (LifeControlMode) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
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
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            Box(modifier = Modifier.padding(padding).imePadding()) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 8 },
                    modifier = Modifier.fillMaxSize()
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
                                stringResource(R.string.gamesetup_mode_label),
                                style = MaterialTheme.magicTypography.titleMedium,
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
                        // ── Settings ──────────────────────────────────────────────────────
                        SettingsSection(
                            gameSettings = uiState.gameSettings,
                            onToggleLandReminder = onToggleLandReminder,
                            onLifeControlModeChange = onLifeControlModeChange,
                        )


                        // ── Player count stepper ──────────────────────────────────────────
                        PlayerCountStepper(
                            playerCount = uiState.playerCount,
                            onPlayerCountChange = onPlayerCountChange,
                        )

                        // ── Player config list ────────────────────────────────────────────
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiState.playerConfigs.forEachIndexed { index, config ->
                                AnimatedVisibility(
                                    visible = index < uiState.playerCount,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
                        }

                        // ── Begin button ──────────────────────────────────────────────────
                        val buttonScale by animateFloatAsState(
                            targetValue = if (visible) 1f else 0.9f,
                            animationSpec = tween(800, delayMillis = 400),
                            label = "buttonScale"
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
                                contentColor = mc.background
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.gamesetup_begin_button).uppercase(),
                                style = MaterialTheme.magicTypography.titleLarge,
                                color = mc.background,
                            )
                        }

                        // ── Online multiplayer ────────────────────────────────────────────
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick   = { onNavigateToOnline(uiState.selectedMode, uiState.playerCount) },
                                modifier  = Modifier.weight(1f).height(48.dp),
                                shape     = RoundedCornerShape(12.dp),
                                border    = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                            ) {
                                Text("Create Online", style = MaterialTheme.magicTypography.labelLarge, color = mc.primaryAccent)
                            }
                            OutlinedButton(
                                onClick   = onNavigateToJoin,
                                modifier  = Modifier.weight(1f).height(48.dp),
                                shape     = RoundedCornerShape(12.dp),
                                border    = androidx.compose.foundation.BorderStroke(1.dp, mc.secondaryAccent.copy(alpha = 0.5f)),
                            ) {
                                Text("Join Game", style = MaterialTheme.magicTypography.labelLarge, color = mc.secondaryAccent)
                            }
                        }
                    }
                }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mc.surface.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(
            stringResource(R.string.gamesetup_players_label),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textSecondary,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Decrement button
            StepperButton(
                text = "−",
                enabled = playerCount > 2,
                onClick = { onPlayerCountChange(playerCount - 1) }
            )

            // Current count
            Box(
                modifier = Modifier.widthIn(min = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$playerCount",
                    style = MaterialTheme.magicTypography.displayMedium,
                    color = mc.primaryAccent,
                    textAlign = TextAlign.Center,
                )
            }

            // Increment button
            StepperButton(
                text = "+",
                enabled = playerCount < 6,
                onClick = { onPlayerCountChange(playerCount + 1) }
            )
        }
    }
}

@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val bgColor by animateColorAsState(
        if (enabled) mc.surfaceVariant else mc.surface.copy(alpha = 0.3f),
        label = "btnBg"
    )
    val contentColor by animateColorAsState(
        if (enabled) mc.textPrimary else mc.textDisabled,
        label = "btnContent"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.titleLarge,
            color = contentColor,
        )
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
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    
    val borderColor by animateColorAsState(
        if (showColorPicker) config.theme.accent else Color.Transparent,
        label = "rowBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                shadowElevation = if (showColorPicker) 12f else 2f
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
            .background(mc.surface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Color circle — tap to open picker
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(config.theme.accent)
                .border(3.dp, config.theme.accent.copy(alpha = 0.3f), CircleShape)
                .clickable { showColorPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }

        // Name field
        if (config.isAppUser) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = config.name.ifBlank { stringResource(R.string.game_setup_default_player_name) },
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = if (config.isDefaultName) mc.textDisabled else mc.primaryAccent,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = stringResource(R.string.game_setup_you_badge).uppercase(),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
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
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        if (it.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                textStyle = MaterialTheme.magicTypography.titleMedium.copy(color = mc.textPrimary),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (config.name.isEmpty()) {
                            Text(
                                stringResource(R.string.gamesetup_player_name_hint, config.id + 1),
                                style = MaterialTheme.magicTypography.titleMedium,
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
            availableThemes = PlayerTheme.ALL.filter { it !in usedThemes || it == config.theme },
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
        contentWindowInsets = { WindowInsets(0) },
        containerColor = mc.backgroundSecondary,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp),
                color = mc.textDisabled.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 32.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.gamesetup_choose_color),
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(availableThemes) { theme ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(theme) }
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.accent)
                                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                        }
                        Text(
                            text = theme.name,
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Settings section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    gameSettings: GameSettings,
    onToggleLandReminder: () -> Unit,
    onLifeControlModeChange: (LifeControlMode) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Settings",
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
                text = "Land Reminder",
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
                text = "Life Control",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )

        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeControlOptionTile(
                mode = LifeControlMode.SCROLL,
                label = "Swipe",
                selected = selectedMode == LifeControlMode.SCROLL,
                onClick = { onModeChange(LifeControlMode.SCROLL) },
                modifier = Modifier.weight(1f),
            )
            LifeControlOptionTile(
                mode = LifeControlMode.TAP,
                label = "Tap",
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
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
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
        val goingUp  = listOf(true, false, false, true)
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
        launch {
            numberScale.animateTo(1.15f, spring(dampingRatio = 0.35f, stiffness = 700f))
            numberScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
        if (lastDelta > 0) {
            launch {
                heartScale.snapTo(1f)
                heartScale.animateTo(1.5f, spring(dampingRatio = 0.3f, stiffness = 700f))
                heartScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
            }
        }
        launch { floatY.animateTo(-26f, tween(700)) }
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
    
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "modeScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )
    
    val bgColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
        animationSpec = tween(durationMillis = 300),
        label = "bgColor"
    )

    val elevation by animateFloatAsState(
        targetValue = if (selected) 8f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "elevation"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
    }
}
