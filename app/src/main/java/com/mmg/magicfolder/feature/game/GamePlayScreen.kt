package com.mmg.magicfolder.feature.game

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.MagicTheme
import com.mmg.magicfolder.core.ui.theme.MarcellusFontFamily
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.core.ui.theme.ThemeBackground
import com.mmg.magicfolder.core.ui.theme.coloredShadow
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.CounterType
import com.mmg.magicfolder.feature.game.model.CustomCounter
import com.mmg.magicfolder.feature.game.model.GameMode
import com.mmg.magicfolder.feature.game.model.GridSlotPosition
import com.mmg.magicfolder.feature.game.model.LayoutTemplate
import com.mmg.magicfolder.feature.game.model.LayoutTemplates
import com.mmg.magicfolder.feature.game.model.Player
import com.mmg.magicfolder.feature.game.model.ScreenedGridSlotPosition
import com.mmg.magicfolder.feature.game.model.toDefaultDegrees
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point (matches nav graph signature)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamePlayScreen(
    onNewGame: () -> Unit,
    onBackHome: () -> Unit,
    onSurvey: (sessionId: Long) -> Unit = {},
    onTournamentClick: (() -> Unit)? = null,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by viewModel.toolsState.collectAsStateWithLifecycle()

    GamePlayContent(
        uiState = uiState,
        toolsState = toolsState,
        onNewGame = onNewGame,
        onBackHome = onBackHome,
        onSurvey = onSurvey,
        onTournamentClick = onTournamentClick,
        onResetGame = viewModel::resetGame,
        onLifeChange = viewModel::changeLife,
        onPoison = viewModel::changePoison,
        onCmdPanel = viewModel::showCmdPanel,
        onCtrPanel = viewModel::showCounterPanel,
        onEditName = viewModel::showEditName,
        onConfirmDefeat = viewModel::confirmDefeat,
        onRevokeDefeat = viewModel::revokeDefeat,
        onToggleTools = viewModel::toggleTools,
        onRollDice = viewModel::rollDice,
        onFlipCoin = viewModel::flipCoin,
        onLayoutEdit = viewModel::showLayoutEditor,
        onSelectLayout = viewModel::selectLayout,
        onRotatePlayer = viewModel::rotatePlayerClockwise,
        onSwapPositions = viewModel::swapPlayerPositions,
        onRenamePlayer = viewModel::renamePlayer,
        onCmdDamage = viewModel::changeCommanderDamage,
        onCounter = viewModel::changeCounter,
        onCustomChange = viewModel::changeCustomCounter,
        onCustomRemove = viewModel::removeCustomCounter,
        onAddCustom = viewModel::addCustomCounter,
    )
}

/**
 * Stateless version of the GamePlayScreen for easier testing and previews.
 */
@Composable
private fun GamePlayContent(
    uiState: GameUiState,
    toolsState: GlobalToolsState,
    onNewGame: () -> Unit,
    onBackHome: () -> Unit,
    onSurvey: (sessionId: Long) -> Unit,
    onTournamentClick: (() -> Unit)?,
    onResetGame: () -> Unit,
    onLifeChange: (playerId: Int, delta: Int) -> Unit,
    onPoison: (playerId: Int, delta: Int) -> Unit,
    onCmdPanel: (playerId: Int?) -> Unit,
    onCtrPanel: (playerId: Int?) -> Unit,
    onEditName: (playerId: Int?) -> Unit,
    onConfirmDefeat: (playerId: Int) -> Unit,
    onRevokeDefeat: (playerId: Int) -> Unit,
    onToggleTools: () -> Unit,
    onRollDice: () -> Unit,
    onFlipCoin: () -> Unit,
    onLayoutEdit: (show: Boolean) -> Unit,
    onSelectLayout: (LayoutTemplate) -> Unit,
    onRotatePlayer: (playerId: Int) -> Unit,
    onSwapPositions: (indexA: Int, indexB: Int) -> Unit,
    onRenamePlayer: (playerId: Int, name: String) -> Unit,
    onCmdDamage: (targetId: Int, sourceId: Int, delta: Int) -> Unit,
    onCounter: (playerId: Int, type: CounterType, delta: Int) -> Unit,
    onCustomChange: (playerId: Int, counterId: Long, delta: Int) -> Unit,
    onCustomRemove: (playerId: Int, counterId: Long) -> Unit,
    onAddCustom: (playerId: Int, name: String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var showGameResult by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

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
            /*GameTopBar(
                mode = uiState.mode,
                turnNumber = uiState.turnNumber,
                activePlayer = uiState.players.find { it.id == uiState.activePlayerId },
                onReset = { showResetDialog = true },
                onExit = { showExitDialog = true },
                onLayoutEdit = { onLayoutEdit(true) },
                onTournamentClick = onTournamentClick,
            )*/
            Box(modifier = Modifier.weight(1f)) {
                GamePlayerGrid(
                    players = uiState.players,
                    gameMode = uiState.mode,
                    activeLayout = uiState.activeLayout,
                    playerRotations = uiState.playerRotations,
                    lifeDeltas = uiState.lifeDeltas,
                    onLifeChange = onLifeChange,
                    onPoison = onPoison,
                    onCmdPanel = onCmdPanel,
                    onCtrPanel = onCtrPanel,
                    onEditName = onEditName,
                    onConfirmDefeat = onConfirmDefeat,
                    onRevokeDefeat = onRevokeDefeat,
                    toolsState = toolsState,
                    onToggleTools = onToggleTools,
                    onRollDice = onRollDice,
                    onFlipCoin = onFlipCoin,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Commander damage panel ─────────────────────────────────────────────
        uiState.showCmdPanelForPlayerId?.let { targetId ->
            val target = uiState.players.find { it.id == targetId } ?: return@let
            CmdDamagePanel(
                target = target,
                allPlayers = uiState.players,
                onDamage = { srcId, d -> onCmdDamage(targetId, srcId, d) },
                onDismiss = { onCmdPanel(null) },
            )
        }

        // ── Counters panel ────────────────────────────────────────────────────
        uiState.showCounterPanelForPlayerId?.let { pid ->
            val player = uiState.players.find { it.id == pid } ?: return@let
            CountersPanel(
                player = player,
                mode = uiState.mode,
                onCounter = { type, d -> onCounter(pid, type, d) },
                onCustomChange = { cid, d -> onCustomChange(pid, cid, d) },
                onCustomRemove = { cid -> onCustomRemove(pid, cid) },
                onAddCustom = { name -> onAddCustom(pid, name) },
                onDismiss = { onCtrPanel(null) },
            )
        }

        // ── Name edit dialog ──────────────────────────────────────────────────
        uiState.editingNameForPlayerId?.let { pid ->
            RenameDialog(
                current = uiState.players.find { it.id == pid }?.name ?: "",
                onConfirm = { onRenamePlayer(pid, it); onEditName(null) },
                onDismiss = { onEditName(null) },
            )
        }

        // ── Layout editor sheet ───────────────────────────────────────────────
        if (uiState.showLayoutEditor) {
            LayoutEditorSheet(
                players = uiState.players,
                activeLayout = uiState.activeLayout,
                playerRotations = uiState.playerRotations,
                onSelectLayout = onSelectLayout,
                onRotatePlayer = onRotatePlayer,
                onSwapPositions = onSwapPositions,
                onDismiss = { onLayoutEdit(false) },
            )
        }

        // ── Exit confirmation dialog ──────────────────────────────────────────
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.game_exit_title), color = mc.textPrimary) },
                text = {
                    Text(
                        stringResource(R.string.game_exit_message), color = mc.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onResetGame(); onBackHome() }) {
                        Text(stringResource(R.string.game_exit_confirm), color = mc.lifeNegative)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.game_exit_cancel), color = mc.primaryAccent)
                    }
                },
                containerColor = mc.surface,
            )
        }

        // ── Reset confirmation dialog ─────────────────────────────────────────
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.game_reset_title), color = mc.textPrimary) },
                text = {
                    Text(
                        stringResource(R.string.game_reset_message), color = mc.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onResetGame(); showResetDialog = false }) {
                        Text(stringResource(R.string.game_reset_confirm), color = mc.lifeNegative)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringResource(R.string.action_cancel), color = mc.primaryAccent)
                    }
                },
                containerColor = mc.surface,
            )
        }

        // ── Winner overlay ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.winner != null && !showGameResult,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
        ) {
            uiState.winner?.let { winner ->
                WinnerOverlay(
                    winner = winner,
                    onViewResults = { showGameResult = true },
                    onPlayAgain = { onResetGame(); onNewGame() },
                )
            }
        }

        // ── Game result screen ────────────────────────────────────────────────
        if (showGameResult) {
            uiState.gameResult?.let { result ->
                GameResultScreen(
                    gameResult = result,
                    onNewGame = { onResetGame(); onNewGame() },
                    onBackHome = onBackHome,
                    onSurvey = { onSurvey(uiState.lastSessionId ?: 0L) },
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
    mode: GameMode,
    turnNumber: Int,
    activePlayer: Player?,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onLayoutEdit: () -> Unit = {},
    onTournamentClick: (() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    TopAppBar(
        modifier = Modifier.height(40.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.game_title),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.goldMtg,
                    maxLines = 1,
                )
                ModeBadge(mode)
                if (activePlayer != null) {
                    val theme = activePlayer.theme
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(theme.accent)
                    )
                    Text(
                        stringResource(R.string.game_turn_label, turnNumber),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
                if (onTournamentClick != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = stringResource(R.string.game_tournament_badge),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = stringResource(R.string.action_exit),
                    tint = mc.textSecondary
                )
            }
        },
        actions = {
            if (onTournamentClick != null) {
                IconButton(onClick = onTournamentClick) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = stringResource(R.string.game_tournament_standings_desc),
                        tint = mc.primaryAccent
                    )
                }
            }
            IconButton(onClick = onLayoutEdit) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = stringResource(R.string.game_layout_editor_title),
                    tint = mc.textSecondary
                )
            }
            IconButton(onClick = onReset) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = mc.textSecondary
                )
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
            text = mode.displayName.uppercase(),
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.goldMtg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player grid  (template-driven layout)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ResourceAsColor")
@Composable
private fun GamePlayerGrid(
    players: List<Player>,
    gameMode: GameMode,
    activeLayout: LayoutTemplate,
    playerRotations: Map<Int, Int>,
    lifeDeltas: Map<Int, Int>,
    onLifeChange: (playerId: Int, delta: Int) -> Unit,
    onPoison: (playerId: Int, delta: Int) -> Unit,
    onCmdPanel: (playerId: Int) -> Unit,
    onCtrPanel: (playerId: Int) -> Unit,
    onEditName: (playerId: Int) -> Unit,
    onConfirmDefeat: (playerId: Int) -> Unit,
    onRevokeDefeat: (playerId: Int) -> Unit,
    toolsState: GlobalToolsState,
    onToggleTools: () -> Unit,
    onRollDice: () -> Unit,
    onFlipCoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme.magicColors
    val sizeLayout = when (activeLayout.gridRows.keys.size) {
        2 -> {
            if (activeLayout.gridRows.keys.contains(ScreenedGridSlotPosition.MID)) {
                Pair(0.34f, 0.66f)
            } else {
                Pair(0.5f, 0f)
            }
        }

        3 -> {
            Pair(0.2f, 0.6f)
        }

        else -> {
            Pair(0f, 1f)
        }
    }
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                activeLayout.gridRows.forEach { (position, slots) ->
                    when (position) {
                        ScreenedGridSlotPosition.TOP -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(sizeLayout.first)
                            ) {
                                val slot = slots[0]
                                val player = players.find { it.id == slot.playerId }
                                if (player != null) {
                                    val rotation = playerRotations[player.id]
                                        ?: slot.position.toDefaultDegrees()
                                    PlayerCard(
                                        player = player,
                                        gameMode = gameMode,
                                        rotation = rotation,
                                        lifeDelta = lifeDeltas[player.id],
                                        onLife = { d -> onLifeChange(player.id, d) },
                                        onCmdPanel = { onCmdPanel(player.id) },
                                        onCtrPanel = { onCtrPanel(player.id) },
                                        onEditName = { onEditName(player.id) },
                                        onConfirmDefeat = { onConfirmDefeat(player.id) },
                                        onRevokeDefeat = { onRevokeDefeat(player.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        ScreenedGridSlotPosition.BOTTOM -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(sizeLayout.first)
                            ) {
                                val slot = slots[0]
                                val player = players.find { it.id == slot.playerId }
                                if (player != null) {
                                    val rotation = playerRotations[player.id]
                                        ?: slot.position.toDefaultDegrees()
                                    PlayerCard(
                                        player = player,
                                        gameMode = gameMode,
                                        rotation = rotation,
                                        lifeDelta = lifeDeltas[player.id],
                                        onLife = { d -> onLifeChange(player.id, d) },
                                        onCmdPanel = { onCmdPanel(player.id) },
                                        onCtrPanel = { onCtrPanel(player.id) },
                                        onEditName = { onEditName(player.id) },
                                        onConfirmDefeat = { onConfirmDefeat(player.id) },
                                        onRevokeDefeat = { onRevokeDefeat(player.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                            }
                        }

                        else -> {
                            val leftPlayers = slots.filter { it.position == GridSlotPosition.LEFT }
                            val rightPlayers =
                                slots.filter { it.position == GridSlotPosition.RIGHT }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(sizeLayout.second)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    leftPlayers.forEachIndexed { index, slot ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            val player =
                                                players.find { leftPlayers[index].playerId == it.id }
                                            if (player != null) {
                                                val rotation = playerRotations[player.id]
                                                    ?: slot.position.toDefaultDegrees()
                                                PlayerCard(
                                                    player = player,
                                                    gameMode = gameMode,
                                                    rotation = rotation,
                                                    lifeDelta = lifeDeltas[player.id],
                                                    onLife = { d -> onLifeChange(player.id, d) },
                                                    onCmdPanel = { onCmdPanel(player.id) },
                                                    onCtrPanel = { onCtrPanel(player.id) },
                                                    onEditName = { onEditName(player.id) },
                                                    onConfirmDefeat = { onConfirmDefeat(player.id) },
                                                    onRevokeDefeat = { onRevokeDefeat(player.id) },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }

                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    rightPlayers.forEachIndexed { index, slot ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            val player =
                                                players.find { rightPlayers[index].playerId == it.id }
                                            if (player != null) {
                                                val rotation = playerRotations[player.id]
                                                    ?: slot.position.toDefaultDegrees()
                                                PlayerCard(
                                                    player = player,
                                                    gameMode = gameMode,
                                                    rotation = rotation,
                                                    lifeDelta = lifeDeltas[player.id],
                                                    onLife = { d -> onLifeChange(player.id, d) },
                                                    onCmdPanel = { onCmdPanel(player.id) },
                                                    onCtrPanel = { onCtrPanel(player.id) },
                                                    onEditName = { onEditName(player.id) },
                                                    onConfirmDefeat = { onConfirmDefeat(player.id) },
                                                    onRevokeDefeat = { onRevokeDefeat(player.id) },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }

                            }

                        }
                    }
                }
            }
        }
        GlobalToolsOverlay(
            state = toolsState,
            onToggle = onToggleTools,
            onRollDice = onRollDice,
            onFlipCoin = onFlipCoin,
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize(),
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Player card
// ─────────────────────────────────────────────────────────────────────────────


@Composable
private fun PlayerCard(
    player: Player,
    gameMode: GameMode,
    rotation: Int,
    lifeDelta: Int?,
    onLife: (Int) -> Unit,
    onCmdPanel: () -> Unit,
    onCtrPanel: () -> Unit,
    onEditName: () -> Unit,
    onConfirmDefeat: () -> Unit,
    onRevokeDefeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    val startingLife = gameMode.startingLife

    // Life color coding: red when ≤ 0, green when above starting, neutral otherwise
    val lifeColor = when {
        player.life <= 0 -> mc.lifeNegative
        player.life > startingLife -> mc.lifePositive
        else -> mc.textPrimary
    }

    BoxWithConstraints(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val isRotated = rotation % 180 != 0
        val cardWidth = if (isRotated) maxHeight else maxWidth
        val cardHeight = if (isRotated) maxWidth else maxHeight

        Box(
            modifier = Modifier
                .requiredSize(width = cardWidth, height = cardHeight)
                .graphicsLayer { rotationZ = rotation.toFloat() }
                .coloredShadow(
                    color = theme.accent.copy(alpha = 0.25f),
                    blurRadius = 20.dp,
                )
                // 1. Clip the content to rounded corners
            .clip(RoundedCornerShape(16.dp))
                // 2. Apply background (it will now be clipped)
            .background(theme.background)
                // 3. Apply border using the SAME shape
            .border(
                width = 0.5.dp,
                color = theme.accent.copy(alpha = 0.40f),
                shape = RoundedCornerShape(16.dp)
            )) {
            // 1. Theme background pattern
            ThemeBackground(modifier = Modifier.matchParentSize())

            // 2. Radial glow from center
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                theme.glow.copy(alpha = 0.12f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // ── Top row: name ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = theme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onEditName)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 1. Ghost element: An invisible Spacer that mirrors the Icon's width + padding.
                    // This pushes the Text exactly to the center by offsetting the Icon on the right.
                    val iconSize = 28.dp
                    val iconPadding = 24.dp

                    Spacer(modifier = Modifier.size(iconSize + iconPadding))

                    // 2. The Main Text: Now perfectly centered because the Row is balanced.
                    Text(
                        text = player.life.toString(),
                        style = MaterialTheme.magicTypography.lifeNumber,
                        color = lifeColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onLife(-1)
                        })

                    // 3. The Real Icon: Attached to the right of the text.
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = stringResource(R.string.game_life_icon_desc),
                        tint = theme.accent,
                        modifier = Modifier
                            .padding(start = iconPadding)
                            .size(iconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onLife(1)
                            })
                }

                // ── Bottom row ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (gameMode == GameMode.COMMANDER) {
                            Icon(
                                painter = painterResource(R.drawable.ic_battle),
                                contentDescription = stringResource(R.string.game_commander_damage_desc),
                                tint = theme.accent,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        onCmdPanel()
                                    },
                            )

                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_counter),
                            contentDescription = stringResource(R.string.game_counters_desc),
                            tint = theme.accent,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onCtrPanel()
                                },
                        )
                    }
                }
            }

            // ── Pending defeat overlay ────────────────────────────────────────
            AnimatedVisibility(
                visible = player.pendingDefeat,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.80f)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.game_pending_defeat_title),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = onConfirmDefeat,
                            colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                stringResource(R.string.game_pending_defeat_confirm),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = onRevokeDefeat) {
                            Text(
                                stringResource(R.string.game_pending_defeat_revoke),
                                color = mc.lifePositive,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // ── Defeated overlay (animated) ───────────────────────────────────
            AnimatedVisibility(
                visible = player.defeated,
                enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.85f),
            ) {
                EliminatedOverlay(player = player, mode = gameMode)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Life button (tap = ±1, long-press = repeat)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LifeButton(
    label: String,
    theme: PlayerThemeColors,
    direction: Int,
    onLifeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
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
                        isPressed = true
                        pressStart = System.currentTimeMillis()
                        onLifeChange(direction)   // tap inmediato

                        val job = scope.launch {
                            delay(500L)
                            while (isPressed) {
                                val elapsed = System.currentTimeMillis() - pressStart
                                val (intervalMs, absDelta) = when {
                                    elapsed > 3000L -> 60L to 10
                                    elapsed > 2000L -> 80L to 5
                                    elapsed > 1000L -> 100L to 1
                                    else -> 150L to 1
                                }
                                onLifeChange(direction * absDelta)
                                delay(intervalMs)
                            }
                        }

                        tryAwaitRelease()
                        isPressed = false
                        job.cancel()
                    })
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.magicTypography.titleLarge,
            color = mc.textPrimary,
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
                    .clickable { onSet(if (filled) i - 1 else i) })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Small action buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardActionButton(
    label: String,
    theme: PlayerThemeColors,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val borderColor = if (danger) mc.lifeNegative else theme.accent.copy(alpha = 0.40f)
    val bgColor =
        if (danger) mc.lifeNegative.copy(alpha = 0.15f) else theme.accent.copy(alpha = 0.10f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textPrimary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Eliminated overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EliminatedOverlay(player: Player, mode: GameMode) {
    val reason = when {
        player.life <= 0 -> stringResource(R.string.game_fallen_life)
        player.poison >= 10 -> stringResource(R.string.game_fallen_poison)
        mode == GameMode.COMMANDER && player.commanderDamage.values.any { it >= 21 } -> stringResource(
            R.string.game_fallen_commander
        )

        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "💀", fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                text = stringResource(R.string.game_fallen_label),
                color = Color(0xFFE63946),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                fontFamily = MarcellusFontFamily,
                textAlign = TextAlign.Center,
            )
            if (reason != null) {
                Text(
                    text = reason,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp,
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
    winner: Player,
    onViewResults: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val theme = winner.theme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text = "👑", fontSize = 48.sp, textAlign = TextAlign.Center)
            Text(
                text = winner.name,
                color = theme.accent,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = MarcellusFontFamily,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.game_wins_label),
                color = mc.goldMtg,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                fontFamily = MarcellusFontFamily,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.game_victory_life_remaining, winner.life),
                color = mc.textSecondary,
                style = MaterialTheme.magicTypography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onViewResults,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.game_victory_view_results), color = mc.background)
            }
            TextButton(onClick = onPlayAgain) {
                Text(stringResource(R.string.action_play_again), color = mc.textSecondary)
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
    target: Player,
    allPlayers: List<Player>,
    onDamage: (sourceId: Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.game_cmd_damage_title, target.name),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            allPlayers.filter { it.id != target.id && !it.defeated }.forEach { source ->
                val damage = target.commanderDamage[source.id] ?: 0
                val srcTheme = source.theme
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
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
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (damage >= 21) {
                        Text(
                            "⚠ ",
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.lifeNegative
                        )
                    }
                    CounterRow(
                        value = damage,
                        theme = srcTheme,
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
    player: Player,
    mode: GameMode,
    onCounter: (CounterType, Int) -> Unit,
    onCustomChange: (Long, Int) -> Unit,
    onCustomRemove: (Long) -> Unit,
    onAddCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    var newCounterName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) }) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.game_counters_title, player.name),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )

            // Standard counters
            CounterRowItem(
                stringResource(R.string.game_poison_label), player.poison, theme
            ) { onCounter(CounterType.POISON, it) }
            if (mode == GameMode.COMMANDER) {
                CounterRowItem(
                    stringResource(R.string.game_experience_label), player.experience, theme
                ) { onCounter(CounterType.EXPERIENCE, it) }
                CounterRowItem(
                    stringResource(R.string.game_energy_label), player.energy, theme
                ) { onCounter(CounterType.ENERGY, it) }
            }

            // Custom counters
            player.customCounters.forEach { counter ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        counter.name,
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onCustomRemove(counter.id) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            tint = mc.textDisabled,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    CounterRow(
                        value = counter.value,
                        theme = theme,
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
                    value = newCounterName,
                    onValueChange = { newCounterName = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.game_custom_counter_hint),
                            color = mc.textDisabled
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (newCounterName.isNotBlank()) {
                            onAddCustom(newCounterName)
                            newCounterName = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add), tint = mc.primaryAccent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CounterRowItem(
    label: String,
    value: Int,
    theme: PlayerThemeColors,
    onDelta: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textPrimary,
            modifier = Modifier.weight(1f)
        )
        CounterRow(
            value = value,
            theme = theme,
            onDecrement = { onDelta(-1) },
            onIncrement = { onDelta(+1) })
    }
}

@Composable
private fun CounterRow(
    value: Int,
    theme: PlayerThemeColors,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.accent.copy(alpha = 0.15f))
                .clickable(onClick = onDecrement),
        ) {
            Text("−", style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        }
        Text(
            value.toString(),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
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
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_rename_player)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_confirm), color = mc.primaryAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Interactive Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun GamePlayInteractivePreview() {
    // Initial dummy state
    val initialPlayers = listOf(
        Player(id = 0, name = "Miguel", life = 40, theme = PlayerTheme.ALL[0], isAppUser = true),
        Player(id = 1, name = "Player 2", life = 38, theme = PlayerTheme.ALL[1]),

        )

    var uiState by remember {
        mutableStateOf(
            GameUiState(
                players = initialPlayers,
                mode = GameMode.COMMANDER,
                activeLayout = LayoutTemplates.TWO_SIDE_BY_SIDE
            )
        )
    }

    var toolsState by remember { mutableStateOf(GlobalToolsState()) }

    MagicTheme {
        GamePlayContent(
            uiState = uiState,
            toolsState = toolsState,
            onNewGame = {},
            onBackHome = {},
            onSurvey = {},
            onTournamentClick = {},
            onResetGame = {
                uiState = uiState.copy(
                    players = initialPlayers.map {
                        it.copy(
                            life = 40, poison = 0, defeated = false
                        )
                    }, winner = null
                )
            },
            onLifeChange = { pid, delta ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) p.copy(life = p.life + delta) else p
                    })
            },
            onPoison = { pid, delta ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) p.copy(poison = (p.poison + delta).coerceIn(0, 10)) else p
                    })
            },
            onCmdPanel = { pid -> uiState = uiState.copy(showCmdPanelForPlayerId = pid) },
            onCtrPanel = { pid -> uiState = uiState.copy(showCounterPanelForPlayerId = pid) },
            onEditName = { pid -> uiState = uiState.copy(editingNameForPlayerId = pid) },
            onConfirmDefeat = { pid ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) p.copy(defeated = true, pendingDefeat = false) else p
                    })
            },
            onRevokeDefeat = { pid ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) p.copy(pendingDefeat = false) else p
                    })
            },
            onToggleTools = { toolsState = toolsState.copy(isExpanded = !toolsState.isExpanded) },
            onRollDice = { toolsState = toolsState.copy(lastDiceResult = (1..20).random()) },
            onFlipCoin = {
                toolsState = toolsState.copy(lastCoinResult = listOf(true, false).random())
            },
            onLayoutEdit = { show -> uiState = uiState.copy(showLayoutEditor = show) },
            onSelectLayout = { layout -> uiState = uiState.copy(activeLayout = layout) },
            onRotatePlayer = { pid ->
                val current = uiState.playerRotations[pid] ?: 0
                uiState =
                    uiState.copy(playerRotations = uiState.playerRotations + (pid to (current + 90) % 360))
            },
            onSwapPositions = { _, _ -> },
            onRenamePlayer = { pid, name ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) p.copy(name = name) else p
                    })
            },
            onCmdDamage = { targetId, srcId, delta ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == targetId) {
                            val current = p.commanderDamage[srcId] ?: 0
                            p.copy(
                                commanderDamage = p.commanderDamage + (srcId to (current + delta).coerceAtLeast(
                                    0
                                ))
                            )
                        } else p
                    })
            },
            onCounter = { pid, type, delta ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) {
                            when (type) {
                                CounterType.POISON -> p.copy(
                                    poison = (p.poison + delta).coerceAtLeast(
                                        0
                                    )
                                )

                                CounterType.EXPERIENCE -> p.copy(
                                    experience = (p.experience + delta).coerceAtLeast(
                                        0
                                    )
                                )

                                CounterType.ENERGY -> p.copy(
                                    energy = (p.energy + delta).coerceAtLeast(
                                        0
                                    )
                                )
                            }
                        } else p
                    })
            },
            onCustomChange = { pid, cid, delta ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) {
                            p.copy(customCounters = p.customCounters.map { c ->
                                if (c.id == cid) c.copy(value = (c.value + delta).coerceAtLeast(0)) else c
                            })
                        } else p
                    })
            },
            onCustomRemove = { pid, cid ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) {
                            p.copy(customCounters = p.customCounters.filter { it.id != cid })
                        } else p
                    })
            },
            onAddCustom = { pid, name ->
                uiState = uiState.copy(
                    players = uiState.players.map { p ->
                        if (p.id == pid) {
                            p.copy(
                                customCounters = p.customCounters + CustomCounter(
                                    System.currentTimeMillis(), name, 0
                                )
                            )
                        } else p
                    })
            })
    }
}
