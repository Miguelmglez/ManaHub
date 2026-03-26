package com.mmg.magicfolder.feature.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameTopBar(
                mode         = uiState.mode,
                turnNumber   = uiState.turnNumber,
                activePlayer = uiState.players.find { it.id == uiState.activePlayerId },
                onPhases     = { viewModel.showPhasePanel(true) },
                onReset      = viewModel::resetGame,
                onExit       = onBackHome,
            )
            PlayerGrid(
                players    = uiState.players,
                uiState    = uiState,
                viewModel  = viewModel,
                modifier   = Modifier.weight(1f),
            )
        }

        // ── Phase panel (ModalBottomSheet) ────────────────────────────────────
        if (uiState.showPhasePanel) {
            PhasePanel(
                currentPhase   = uiState.currentPhase,
                activePlayerId = uiState.activePlayerId,
                players        = uiState.players,
                phaseStops     = uiState.phaseStops,
                turnNumber     = uiState.turnNumber,
                onAdvance      = viewModel::advancePhase,
                onNextTurn     = viewModel::nextTurn,
                onSetStop      = { phase -> viewModel.setPhaseStop(uiState.activePlayerId, phase, uiState.activePlayerId) },
                onRemoveStop   = { phase -> viewModel.removePhaseStop(uiState.activePlayerId, phase) },
                onDismiss      = { viewModel.showPhasePanel(false) },
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

        // ── Game result screen ────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(
    mode:         GameMode,
    turnNumber:   Int,
    activePlayer: Player?,
    onPhases:     () -> Unit,
    onReset:      () -> Unit,
    onExit:       () -> Unit,
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
            IconButton(onClick = onPhases) {
                Icon(Icons.Default.AccountTree, contentDescription = "Phases", tint = mc.textSecondary)
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
        // 2. Radial gradient overlay from top
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            theme.glow.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(0.5f, 0f),
                        radius = 400f,
                    )
                )
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

        if (player.eliminated) {
            EliminatedOverlay()
        } else {
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
                            text       = player.life.toString(),
                            color      = mc.textPrimary,
                            fontSize   = 80.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = CinzelFontFamily,
                            lineHeight = 80.sp,
                            maxLines   = 1,
                            textAlign  = TextAlign.Center,
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
            .clip(RoundedCornerShape(8.dp))
            .background(theme.accent.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = theme.accent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(8.dp),
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
private fun EliminatedOverlay() {
    val mc = MaterialTheme.magicColors
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
    ) {
        Text(
            text          = "FALLEN",
            color         = Color(0xFFE63946),
            fontSize      = 24.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 4.sp,
            fontFamily    = CinzelFontFamily,
            textAlign     = TextAlign.Center,
        )
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
//  Phase panel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhasePanel(
    currentPhase:   GamePhase,
    activePlayerId: Int,
    players:        List<Player>,
    phaseStops:     List<PhaseStop>,
    turnNumber:     Int,
    onAdvance:      () -> Unit,
    onNextTurn:     () -> Unit,
    onSetStop:      (GamePhase) -> Unit,
    onRemoveStop:   (GamePhase) -> Unit,
    onDismiss:      () -> Unit,
) {
    val mc     = MaterialTheme.magicColors
    val phases = GamePhase.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "Phase Tracker — Turn $turnNumber",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onNextTurn,
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = mc.goldMtg),
                        border  = BorderStroke(1.dp, mc.goldMtg),
                    ) {
                        Text("Next Turn", style = MaterialTheme.magicTypography.labelSmall)
                    }
                    Button(
                        onClick = onAdvance,
                        colors  = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    ) {
                        Text("Advance ▶", style = MaterialTheme.magicTypography.labelSmall, color = mc.background)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(phases) { phase ->
                    val isActive   = phase == currentPhase
                    val isPast     = phases.indexOf(phase) < phases.indexOf(currentPhase)
                    val stopActive = phaseStops.any { it.phase == phase && it.forTurnOf == activePlayerId }

                    val bgColor    = when {
                        isActive -> mc.primaryAccent.copy(alpha = 0.15f)
                        isPast   -> mc.surfaceVariant.copy(alpha = 0.40f)
                        else     -> mc.surface
                    }
                    val textColor  = when {
                        isActive -> mc.primaryAccent
                        isPast   -> mc.textDisabled
                        else     -> mc.textSecondary
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        // Status icon
                        Text(
                            when { isActive -> "▶"; isPast -> "✓"; else -> "○" },
                            style    = MaterialTheme.magicTypography.labelSmall,
                            color    = textColor,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            phase.displayName,
                            style    = MaterialTheme.magicTypography.bodyMedium,
                            color    = textColor,
                            modifier = Modifier.weight(1f),
                        )
                        if (stopActive) {
                            Text(
                                "⚑ STOP",
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.lifeNegative,
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(mc.lifeNegative.copy(alpha = 0.15f))
                                    .clickable { onRemoveStop(phase) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("×", style = MaterialTheme.magicTypography.labelSmall, color = mc.lifeNegative)
                            }
                        } else if (!isPast) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(mc.surfaceVariant)
                                    .clickable { onSetStop(phase) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "+ STOP",
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
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
//  Winner overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WinnerOverlay(
    winner:     Player,
    turnNumber: Int,
    onNewGame:  () -> Unit,
    onBackHome: () -> Unit,
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
            modifier            = Modifier.padding(32.dp),
        ) {
            Text(
                "VICTORY",
                color         = Color.White.copy(alpha = 0.4f),
                letterSpacing = 6.sp,
                fontSize      = 11.sp,
                fontFamily    = CinzelFontFamily,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                winner.name,
                color      = theme.accent,
                fontSize   = 48.sp,
                fontWeight = FontWeight.Black,
                fontFamily = CinzelFontFamily,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${winner.life} life remaining · Turn $turnNumber",
                color    = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onNewGame,
                    colors  = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    Text("Play Again", color = Color.Black)
                }
                OutlinedButton(
                    onClick = onBackHome,
                    shape   = RoundedCornerShape(12.dp),
                ) {
                    Text("Exit")
                }
            }
        }
    }
}

