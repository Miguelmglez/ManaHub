package com.mmg.magicfolder.feature.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.mmg.magicfolder.core.ui.components.FloatingDelta
import com.mmg.magicfolder.core.ui.components.HexGridBackground
import com.mmg.magicfolder.core.ui.theme.CinzelFontFamily
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.core.ui.theme.coloredShadow
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.*

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point (matches nav graph signature)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamePlayScreen(
    onNewGame:  () -> Unit,
    onBackHome: () -> Unit,
    onSurvey:   (sessionId: Long) -> Unit = {},
    viewModel:  GameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    var showLayoutEditor by remember { mutableStateOf(false) }
    var showGameResult   by remember { mutableStateOf(false) }
    var showExitDialog   by remember { mutableStateOf(false) }
    var showResetDialog  by remember { mutableStateOf(false) }

    // Intercept system back button — show exit dialog instead of navigating
    BackHandler(enabled = uiState.winner == null) {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameTopBar(
                mode           = uiState.mode,
                turnNumber     = uiState.turnNumber,
                activePlayer   = uiState.players.find { it.id == uiState.activePlayerId },
                onReset        = { showResetDialog = true },
                onExit         = { showExitDialog  = true },
                onLayoutEdit   = { showLayoutEditor = true },
            )
            PlayerGrid(
                players    = uiState.players,
                uiState    = uiState,
                viewModel  = viewModel,
                modifier   = Modifier.weight(1f),
            )
        }

        // ── Commander damage panel ─────────────────────────────────────────────
        uiState.showCmdPanelForPlayerId?.let { targetId ->
            val target = uiState.players.find { it.id == targetId } ?: return@let
            CmdDamagePanel(
                target    = target,
                allPlayers = uiState.players,
                onDamage  = { srcId, d -> viewModel.changeCommanderDamage(targetId, srcId, d) },
                onDismiss = { viewModel.showCmdPanel(null) },
            )
        }

        // ── Counters panel ────────────────────────────────────────────────────
        uiState.showCounterPanelForPlayerId?.let { pid ->
            val player = uiState.players.find { it.id == pid } ?: return@let
            CountersPanel(
                player          = player,
                mode            = uiState.mode,
                onCounter       = { type, d -> viewModel.changeCounter(pid, type, d) },
                onCustomChange  = { cid, d -> viewModel.changeCustomCounter(pid, cid, d) },
                onCustomRemove  = { cid -> viewModel.removeCustomCounter(pid, cid) },
                onAddCustom     = { name -> viewModel.addCustomCounter(pid, name) },
                onDismiss       = { viewModel.showCounterPanel(null) },
            )
        }

        // ── Name edit dialog ──────────────────────────────────────────────────
        uiState.editingNameForPlayerId?.let { pid ->
            RenameDialog(
                current   = uiState.players.find { it.id == pid }?.name ?: "",
                onConfirm = { viewModel.renamePlayer(pid, it); viewModel.showEditName(null) },
                onDismiss = { viewModel.showEditName(null) },
            )
        }

        // ── Layout editor sheet ───────────────────────────────────────────────
        if (showLayoutEditor) {
            LayoutEditorSheet(
                players         = uiState.players,
                onSwapPositions = viewModel::swapPlayerPositions,
                onDismiss       = { showLayoutEditor = false },
            )
        }

        // ── Exit confirmation dialog ──────────────────────────────────────────
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title   = { Text("Leave game?", color = mc.textPrimary) },
                text    = { Text("Game progress will be lost.", color = mc.textSecondary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetGame(); onBackHome() }) {
                        Text("Leave", color = mc.lifeNegative)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Stay", color = mc.primaryAccent)
                    }
                },
                containerColor = mc.surface,
            )
        }

        // ── Reset confirmation dialog ─────────────────────────────────────────
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title   = { Text("Reset game?", color = mc.textPrimary) },
                text    = { Text("All life totals and counters will reset.", color = mc.textSecondary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetGame(); showResetDialog = false }) {
                        Text("Reset", color = mc.lifeNegative)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = mc.primaryAccent)
                    }
                },
                containerColor = mc.surface,
            )
        }

        // ── Winner overlay ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.winner != null && !showGameResult,
            enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
        ) {
            uiState.winner?.let { winner ->
                WinnerOverlay(
                    winner        = winner,
                    onViewResults = { showGameResult = true },
                    onPlayAgain   = { viewModel.resetGame(); onNewGame() },
                )
            }
        }

        // ── Game result screen ────────────────────────────────────────────────
        if (showGameResult) {
            uiState.gameResult?.let { result ->
                GameResultScreen(
                    gameResult = result,
                    onNewGame  = { viewModel.resetGame(); onNewGame() },
                    onBackHome = onBackHome,
                    onSurvey   = { onSurvey(uiState.lastSessionId ?: 0L) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(
    mode:         GameMode,
    turnNumber:   Int,
    activePlayer: Player?,
    onReset:      () -> Unit,
    onExit:       () -> Unit,
    onLayoutEdit: () -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    TopAppBar(
        modifier = Modifier.height(40.dp),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Æther Tracker",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.goldMtg,
                    maxLines = 1,
                )
                ModeBadge(mode)
                if (activePlayer != null) {
                    val theme = MaterialTheme.magicColors.playerColors.getOrNull(activePlayer.themeIndex % 10)
                    theme?.let {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(it.accent)
                        )
                    }
                    Text(
                        "T$turnNumber",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Exit", tint = mc.textSecondary)
            }
        },
        actions = {
            IconButton(onClick = onLayoutEdit) {
                Icon(Icons.Default.GridView, contentDescription = "Edit layout", tint = mc.textSecondary)
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = mc.textSecondary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
    )
}

@Composable
private fun ModeBadge(mode: GameMode) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.goldMtg.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text     = mode.displayName.uppercase(),
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.goldMtg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player grid  (dynamic layout)
// ─────────────────────────────────────────────────────────────────────────────

private fun gridConfig(count: Int): Pair<Int, Int> = when (count) {
    2    -> Pair(1, 2)
    3    -> Pair(1, 3)
    4    -> Pair(2, 2)
    5, 6 -> Pair(2, 3)
    7, 8 -> Pair(4, 2)
    else -> Pair(5, 2)  // 9-10
}

@Composable
private fun PlayerGrid(
    players:   List<Player>,
    uiState:   GameUiState,
    viewModel: GameViewModel,
    modifier:  Modifier = Modifier,
) {
    val (cols, rows) = gridConfig(players.size)
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                repeat(cols) { col ->
                    val index  = row * cols + col
                    val player = players.getOrNull(index)
                    if (player != null) {
                        // Rotate the top card 180° in 2-player layout
                        val rotated = players.size == 2 && row == 0
                        PlayerCard(
                            player      = player,
                            uiState     = uiState,
                            rotated     = rotated,
                            onLife      = { d -> viewModel.changeLife(player.id, d) },
                            onCounter   = { t, d -> viewModel.changeCounter(player.id, t, d) },
                            onRoll      = { viewModel.rollDice(player.id) },
                            onFlip      = { viewModel.flipCoin(player.id) },
                            onCmdPanel  = { viewModel.showCmdPanel(player.id) },
                            onCtrPanel  = { viewModel.showCounterPanel(player.id) },
                            onEditName  = { viewModel.showEditName(player.id) },
                            modifier    = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerCard(
    player:     Player,
    uiState:    GameUiState,
    rotated:    Boolean,
    onLife:     (Int) -> Unit,
    onCounter:  (CounterType, Int) -> Unit,
    onRoll:     () -> Unit,
    onFlip:     () -> Unit,
    onCmdPanel: () -> Unit,
    onCtrPanel: () -> Unit,
    onEditName: () -> Unit,
    modifier:   Modifier = Modifier,
) {
    val mc       = MaterialTheme.magicColors
    val theme    = mc.playerColors.getOrNull(player.themeIndex % 10) ?: mc.playerColors[0]
    val delta    = uiState.lifeDeltas[player.id]
    val dice     = uiState.diceResults[player.id]
    val coin     = uiState.coinResults[player.id]
    val isActive = player.id == uiState.activePlayerId

    Box(
        modifier = modifier
            .graphicsLayer { if (rotated) rotationZ = 180f }
            .coloredShadow(
                color        = theme.accent.copy(alpha = 0.25f),
                blurRadius   = 20.dp,
            )
            .background(theme.background)
            .border(width = 0.5.dp, color = theme.accent.copy(alpha = 0.40f))
    ) {
        // 1. Hex grid background
        HexGridBackground(
            modifier = Modifier.matchParentSize(),
            color    = theme.accent.copy(alpha = 0.04f),
            hexSize  = 18f,
        )
        // Active turn indicator
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(theme.accent.copy(alpha = 0.70f))
            )
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Player name ───────────────────────────────────────────
            Text(
                text     = player.name,
                style    = MaterialTheme.magicTypography.labelSmall,
                color    = theme.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onEditName),
            )

            // ── Life counter ──────────────────────────────────────────
            val animatedLife by animateIntAsState(
                targetValue   = player.life,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
                label = "life_${player.id}",
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LifeButton(
                    label        = "−",
                    theme        = theme,
                    direction    = -1,
                    onLifeChange = onLife,
                    modifier     = Modifier.size(48.dp),
                )

                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text      = animatedLife.toString(),
                        style     = MaterialTheme.magicTypography.lifeNumber,
                        color     = mc.textPrimary,
                        maxLines  = 1,
                        textAlign = TextAlign.Center,
                    )
                    FloatingDelta(
                        delta         = delta,
                        positiveColor = mc.lifePositive,
                        negativeColor = mc.lifeNegative,
                        modifier      = Modifier.align(Alignment.TopCenter),
                    )
                }

                LifeButton(
                    label        = "+",
                    theme        = theme,
                    direction    = +1,
                    onLifeChange = onLife,
                    modifier     = Modifier.size(48.dp),
                )
            }

            // ── Bottom row ────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PoisonPips(
                    count    = player.poison,
                    theme    = theme,
                    onSet    = { newVal ->
                        val d = newVal - player.poison
                        onCounter(CounterType.POISON, d)
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (uiState.mode == GameMode.COMMANDER) {
                        CardActionButton(
                            label   = "⚔",
                            theme   = theme,
                            onClick = onCmdPanel,
                            danger  = player.commanderDamage.values.any { it >= 21 },
                        )
                    }
                    CardActionButton(label = "◆", theme = theme, onClick = onCtrPanel)
                    DiceCoinsRow(dice = dice, coin = coin, theme = theme, onRoll = onRoll, onFlip = onFlip)
                }
            }
        }

        // ── Eliminated overlay (animated) ────────────────────────────────
        AnimatedVisibility(
            visible = player.eliminated,
            enter   = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.85f),
        ) {
            EliminatedOverlay(player = player, mode = uiState.mode)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Life button (tap = ±1, long-press = repeat)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LifeButton(
    label:        String,
    theme:        PlayerThemeColors,
    direction:    Int,
    onLifeChange: (Int) -> Unit,
    modifier:     Modifier = Modifier,
) {
    val mc         = MaterialTheme.magicColors
    val scope      = rememberCoroutineScope()
    var isPressed  by remember { mutableStateOf(false) }
    var pressStart by remember { mutableStateOf(0L) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.accent.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = theme.accent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(10.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed  = true
                        pressStart = System.currentTimeMillis()
                        onLifeChange(direction)   // tap inmediato

                        val job = scope.launch {
                            kotlinx.coroutines.delay(500L)
                            while (isPressed) {
                                val elapsed = System.currentTimeMillis() - pressStart
                                val (intervalMs, absDelta) = when {
                                    elapsed > 3000L -> 60L  to 10
                                    elapsed > 2000L -> 80L  to 5
                                    elapsed > 1000L -> 100L to 1
                                    else            -> 150L to 1
                                }
                                onLifeChange(direction * absDelta)
                                kotlinx.coroutines.delay(intervalMs)
                            }
                        }

                        tryAwaitRelease()
                        isPressed = false
                        job.cancel()
                    }
                )
            },
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.magicTypography.titleLarge,
            color     = mc.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Poison pips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoisonPips(
    count: Int,
    theme: PlayerThemeColors,
    onSet: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..10).forEach { i ->
            val filled = i <= count
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (filled) mc.poisonColor else theme.accent.copy(alpha = 0.22f))
                    .clickable { onSet(if (filled) i - 1 else i) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Small action buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardActionButton(
    label:   String,
    theme:   PlayerThemeColors,
    onClick: () -> Unit,
    danger:  Boolean = false,
) {
    val mc          = MaterialTheme.magicColors
    val borderColor = if (danger) mc.lifeNegative else theme.accent.copy(alpha = 0.40f)
    val bgColor     = if (danger) mc.lifeNegative.copy(alpha = 0.15f) else theme.accent.copy(alpha = 0.10f)
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textPrimary)
    }
}

@Composable
private fun DiceCoinsRow(
    dice:   Int?,
    coin:   Boolean?,
    theme:  PlayerThemeColors,
    onRoll: () -> Unit,
    onFlip: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // d20
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(theme.accent.copy(alpha = 0.08f))
                .clickable(onClick = onRoll)
                .padding(4.dp),
        ) {
            Text(
                text  = dice?.toString() ?: "d20",
                style = MaterialTheme.magicTypography.labelSmall,
                color = if (dice == 20) mc.goldMtg else if (dice == 1) mc.lifeNegative else mc.textSecondary,
            )
        }
        // Coin
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(theme.accent.copy(alpha = 0.08f))
                .clickable(onClick = onFlip)
                .padding(4.dp),
        ) {
            Text(
                text  = when (coin) { true -> "H"; false -> "T"; null -> "¢" },
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Eliminated overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EliminatedOverlay(player: Player, mode: GameMode) {
    val reason = when {
        player.life <= 0    -> "Ran out of life"
        player.poison >= 10 -> "Poison"
        mode == GameMode.COMMANDER && player.commanderDamage.values.any { it >= 21 } -> "Commander damage"
        else                -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "💀", fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                text          = "FALLEN",
                color         = Color(0xFFE63946),
                fontSize      = 24.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 6.sp,
                fontFamily    = CinzelFontFamily,
                textAlign     = TextAlign.Center,
            )
            if (reason != null) {
                Text(
                    text      = reason,
                    color     = Color.White.copy(alpha = 0.70f),
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Winner overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WinnerOverlay(
    winner:        Player,
    onViewResults: () -> Unit,
    onPlayAgain:   () -> Unit,
) {
    val mc    = MaterialTheme.magicColors
    val theme = mc.playerColors.getOrNull(winner.themeIndex % 10) ?: mc.playerColors[0]
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text(text = "👑", fontSize = 48.sp, textAlign = TextAlign.Center)
            Text(
                text          = winner.name,
                color         = theme.accent,
                fontSize      = 32.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily    = CinzelFontFamily,
                textAlign     = TextAlign.Center,
            )
            Text(
                text          = "WINS!",
                color         = mc.goldMtg,
                fontSize      = 20.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 6.sp,
                fontFamily    = CinzelFontFamily,
                textAlign     = TextAlign.Center,
            )
            Text(
                text  = "${winner.life} life remaining",
                color = mc.textSecondary,
                style = MaterialTheme.magicTypography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = onViewResults,
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View Results", color = mc.background)
            }
            TextButton(onClick = onPlayAgain) {
                Text("Play Again", color = mc.textSecondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Commander damage panel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CmdDamagePanel(
    target:     Player,
    allPlayers: List<Player>,
    onDamage:   (sourceId: Int, Int) -> Unit,
    onDismiss:  () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Commander Damage → ${target.name}",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            allPlayers
                .filter { it.id != target.id && !it.eliminated }
                .forEach { source ->
                    val damage = target.commanderDamage[source.id] ?: 0
                    val srcTheme = mc.playerColors.getOrNull(source.themeIndex % 10) ?: mc.playerColors[0]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(srcTheme.accent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            source.name,
                            style    = MaterialTheme.magicTypography.bodyMedium,
                            color    = mc.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (damage >= 21) {
                            Text("⚠ ", style = MaterialTheme.magicTypography.bodyMedium, color = mc.lifeNegative)
                        }
                        CounterRow(
                            value       = damage,
                            theme       = srcTheme,
                            onDecrement = { onDamage(source.id, -1) },
                            onIncrement = { onDamage(source.id, +1) },
                        )
                    }
                }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Counters panel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountersPanel(
    player:         Player,
    mode:           GameMode,
    onCounter:      (CounterType, Int) -> Unit,
    onCustomChange: (Long, Int) -> Unit,
    onCustomRemove: (Long) -> Unit,
    onAddCustom:    (String) -> Unit,
    onDismiss:      () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val theme = mc.playerColors.getOrNull(player.themeIndex % 10) ?: mc.playerColors[0]
    var newCounterName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${player.name} — Counters",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )

            // Standard counters
            CounterRowItem("☠ Poison",     player.poison,     theme) { onCounter(CounterType.POISON,     it) }
            if (mode == GameMode.COMMANDER) {
                CounterRowItem("✦ Experience", player.experience, theme) { onCounter(CounterType.EXPERIENCE, it) }
                CounterRowItem("⚡ Energy",    player.energy,     theme) { onCounter(CounterType.ENERGY,     it) }
            }

            // Custom counters
            player.customCounters.forEach { counter ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        counter.name,
                        style    = MaterialTheme.magicTypography.bodyMedium,
                        color    = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick  = { onCustomRemove(counter.id) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint     = mc.textDisabled,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    CounterRow(
                        value       = counter.value,
                        theme       = theme,
                        onDecrement = { onCustomChange(counter.id, -1) },
                        onIncrement = { onCustomChange(counter.id, +1) },
                    )
                }
            }

            // Add custom counter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value         = newCounterName,
                    onValueChange = { newCounterName = it },
                    placeholder   = { Text("Counter name…", color = mc.textDisabled) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick  = {
                        if (newCounterName.isNotBlank()) {
                            onAddCustom(newCounterName)
                            newCounterName = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = mc.primaryAccent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CounterRowItem(
    label:   String,
    value:   Int,
    theme:   PlayerThemeColors,
    onDelta: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
        CounterRow(value = value, theme = theme, onDecrement = { onDelta(-1) }, onIncrement = { onDelta(+1) })
    }
}

@Composable
private fun CounterRow(
    value:       Int,
    theme:       PlayerThemeColors,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.accent.copy(alpha = 0.15f))
                .clickable(onClick = onDecrement),
        ) {
            Text("−", style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        }
        Text(
            value.toString(),
            style    = MaterialTheme.magicTypography.titleMedium,
            color    = mc.textPrimary,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.accent.copy(alpha = 0.15f))
                .clickable(onClick = onIncrement),
        ) {
            Text("+", style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rename dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(
    current:   String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Rename Player") },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    focusedTextColor   = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK", color = mc.primaryAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Layout editor bottom sheet (FIX 5)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutEditorSheet(
    players:         List<Player>,
    onSwapPositions: (Int, Int) -> Unit,
    onDismiss:       () -> Unit,
) {
    val mc           = MaterialTheme.magicColors
    val playerColors = mc.playerColors
    // Convert Player list to PlayerConfig-like objects for MiniGridPreview
    val playerConfigs = players.mapIndexed { index, p ->
        PlayerConfig(
            id           = index,
            name         = p.name,
            theme        = playerColors.getOrNull(p.themeIndex % 10) ?: playerColors.first(),
            gridPosition = index,
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Edit player layout",
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                "Tap a player to swap with the next position",
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
            )
            MiniGridPreview(
                playerConfigs   = playerConfigs,
                onSwapPositions = onSwapPositions,
            )
        }
    }
}


