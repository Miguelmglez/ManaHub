package com.mmg.magicfolder.feature.game

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.components.ManaSymbolImage
import com.mmg.magicfolder.core.ui.theme.MarcellusFontFamily
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.core.ui.theme.ThemeBackground
import com.mmg.magicfolder.core.ui.theme.coloredShadow
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.CounterIconKey
import com.mmg.magicfolder.feature.game.model.CounterType
import com.mmg.magicfolder.feature.game.model.GameMode
import com.mmg.magicfolder.feature.game.model.GridSlotPosition
import com.mmg.magicfolder.feature.game.model.LayoutTemplate
import com.mmg.magicfolder.feature.game.model.LayoutTemplates
import com.mmg.magicfolder.feature.game.model.Player
import com.mmg.magicfolder.feature.game.model.ScreenedGridSlotPosition
import com.mmg.magicfolder.feature.game.model.toDefaultDegrees

// ─────────────────────────────────────────────────────────────────────────────
//  Entry point (matches nav graph signature)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamePlayScreen(
    onNewGame: () -> Unit,
    onBackHome: () -> Unit,
    onAbandonGame: () -> Unit = onBackHome,
    onExitGame: () -> Unit = {},
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
        onAbandonGame = onAbandonGame,
        onExitGame = onExitGame,
        onLifeChange = viewModel::changeLife,
        onCmdPanel = viewModel::showCmdPanel,
        onCtrPanel = viewModel::showCounterPanel,
        onConfirmDefeat = viewModel::confirmDefeat,
        onRevokeDefeat = viewModel::revokeDefeat,
        onToggleTools = viewModel::toggleTools,
        onRollDice = viewModel::rollDice,
        onFlipCoin = viewModel::flipCoin,
        onSelectLayout = viewModel::selectLayout,
        onRenamePlayer = viewModel::renamePlayer,
        onCmdDamage = viewModel::changeCommanderDamage,
        onCounter = viewModel::changeCounter,
        onCustomChange = viewModel::changeCustomCounter,
        onCustomRemove = viewModel::removeCustomCounter,
        onAddCustom = { pid, name, iconKey -> viewModel.addCustomCounter(pid, name, iconKey) },
        onUpdateTheme = viewModel::updatePlayerTheme,
        onSwapGridSlots = viewModel::swapGridSlots,
        onReorderTurnOrder = viewModel::reorderTurnOrder,
        onEndTurn = viewModel::nextTurn,
        onToggleLand = viewModel::toggleLandPlayed,
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
    onAbandonGame: () -> Unit,
    onExitGame: () -> Unit,
    onLifeChange: (playerId: Int, delta: Int) -> Unit,
    onCmdPanel: (playerId: Int?) -> Unit,
    onCtrPanel: (playerId: Int?) -> Unit,
    onConfirmDefeat: (playerId: Int) -> Unit,
    onRevokeDefeat: (playerId: Int) -> Unit,
    onToggleTools: () -> Unit,
    onRollDice: () -> Unit,
    onFlipCoin: () -> Unit,
    onSelectLayout: (LayoutTemplate) -> Unit,
    onRenamePlayer: (playerId: Int, name: String) -> Unit,
    onCmdDamage: (targetId: Int, sourceId: Int, delta: Int) -> Unit,
    onCounter: (playerId: Int, type: CounterType, delta: Int) -> Unit,
    onCustomChange: (playerId: Int, counterId: Long, delta: Int) -> Unit,
    onCustomRemove: (playerId: Int, counterId: Long) -> Unit,
    onAddCustom: (playerId: Int, name: String, iconKey: String) -> Unit,
    onUpdateTheme: (playerId: Int, theme: PlayerThemeColors) -> Unit,
    onSwapGridSlots: (slotIdA: Int, slotIdB: Int) -> Unit,
    onReorderTurnOrder: (orderedPlayerIds: List<Int>) -> Unit,
    onEndTurn: () -> Unit,
    onToggleLand: (playerId: Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var showGameResult by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showManagePlayersSheet by remember { mutableStateOf(false) }
    var managePlayersInitialTab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = uiState.winner == null) {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                GamePlayerGrid(
                    players = uiState.players,
                    gameMode = uiState.mode,
                    activeLayout = uiState.activeLayout,
                    gridAssignment = uiState.gridAssignment,
                    playerRotations = uiState.playerRotations,
                    activePlayerId = uiState.activePlayerId,
                    turnNumber = uiState.turnNumber,
                    hasPlayedLand = uiState.hasPlayedLand,
                    onLifeChange = onLifeChange,
                    onCmdPanel = onCmdPanel,
                    onCtrPanel = onCtrPanel,
                    onConfirmDefeat = onConfirmDefeat,
                    onRevokeDefeat = onRevokeDefeat,
                    onEndTurn = onEndTurn,
                    onToggleLand = onToggleLand,
                    toolsState = toolsState,
                    onToggleTools = onToggleTools,
                    onRollDice = onRollDice,
                    onFlipCoin = onFlipCoin,
                    onReset = { showResetDialog = true },
                    onAbandonGame = onAbandonGame,
                    onExitGame = onExitGame,
                    onManagePlayers = {
                        managePlayersInitialTab = 0; showManagePlayersSheet = true
                    },
                    onPlayerNameClick = {
                        managePlayersInitialTab = 1; showManagePlayersSheet = true
                    },
                    onTournament = onTournamentClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        uiState.showCmdPanelForPlayerId?.let { targetId ->
            val target = uiState.players.find { it.id == targetId } ?: return@let
            CmdDamagePanel(
                target = target,
                allPlayers = uiState.players,
                onDamage = { srcId, d -> onCmdDamage(targetId, srcId, d) },
                onDismiss = { onCmdPanel(null) },
            )
        }

        uiState.showCounterPanelForPlayerId?.let { pid ->
            val player = uiState.players.find { it.id == pid } ?: return@let
            CountersPanel(
                player = player,
                mode = uiState.mode,
                onCounter = { type, d -> onCounter(pid, type, d) },
                onCustomChange = { cid, d -> onCustomChange(pid, cid, d) },
                onCustomRemove = { cid -> onCustomRemove(pid, cid) },
                onAddCustom = { name, iconKey -> onAddCustom(pid, name, iconKey) },
                onDismiss = { onCtrPanel(null) },
            )
        }

        if (showManagePlayersSheet) {
            ManagePlayersSheet(
                players = uiState.players,
                activeLayout = uiState.activeLayout,
                gridAssignment = uiState.gridAssignment,
                playerCount = uiState.players.size,
                initialTab = managePlayersInitialTab,
                onDismiss = { showManagePlayersSheet = false },
                onSelectLayout = onSelectLayout,
                onSwapGridSlots = onSwapGridSlots,
                onRenamePlayer = onRenamePlayer,
                onUpdateTheme = onUpdateTheme,
                onReorderTurnOrder = onReorderTurnOrder,
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.game_exit_title), color = mc.textPrimary) },
                text = {
                    Text(
                        stringResource(R.string.game_exit_message),
                        color = mc.textSecondary
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

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.game_reset_title), color = mc.textPrimary) },
                text = {
                    Text(
                        stringResource(R.string.game_reset_message),
                        color = mc.textSecondary
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
//  Player grid  (template-driven layout)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ResourceAsColor")
@Composable
private fun GamePlayerGrid(
    players: List<Player>,
    gameMode: GameMode,
    activeLayout: LayoutTemplate,
    gridAssignment: Map<Int, Int>,
    playerRotations: Map<Int, Int>,
    activePlayerId: Int,
    turnNumber: Int,
    hasPlayedLand: Set<Int>,
    onLifeChange: (playerId: Int, delta: Int) -> Unit,
    onCmdPanel: (playerId: Int) -> Unit,
    onCtrPanel: (playerId: Int) -> Unit,
    onConfirmDefeat: (playerId: Int) -> Unit,
    onRevokeDefeat: (playerId: Int) -> Unit,
    onEndTurn: () -> Unit,
    onToggleLand: (playerId: Int) -> Unit,
    toolsState: GlobalToolsState,
    onToggleTools: () -> Unit,
    onRollDice: () -> Unit,
    onFlipCoin: () -> Unit,
    onReset: () -> Unit,
    onAbandonGame: () -> Unit,
    onExitGame: () -> Unit,
    onManagePlayers: () -> Unit,
    onPlayerNameClick: () -> Unit,
    onTournament: (() -> Unit)?,
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
                            // Extra bottom padding for TWO_TOP_BOTTOM to clear the center overlay
                            val extraPad = if (activeLayout.id == "2_top_bottom") 26.dp else 0.dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(sizeLayout.first)
                                    .padding(bottom = extraPad)
                            ) {
                                val slot = slots[0]
                                val actualPlayerId =
                                    gridAssignment.getOrDefault(slot.playerId, slot.playerId)
                                val player = players.find { it.id == actualPlayerId }
                                if (player != null) {
                                    val rotation = playerRotations[player.id]
                                        ?: slot.position.toDefaultDegrees()
                                    PlayerCard(
                                        player = player,
                                        gameMode = gameMode,
                                        rotation = rotation,
                                        isActive = player.id == activePlayerId,
                                        turnNumber = turnNumber,
                                        landPlayed = player.id in hasPlayedLand,
                                        onLife = { d -> onLifeChange(player.id, d) },
                                        onCmdPanel = { onCmdPanel(player.id) },
                                        onCtrPanel = { onCtrPanel(player.id) },
                                        onConfirmDefeat = { onConfirmDefeat(player.id) },
                                        onRevokeDefeat = { onRevokeDefeat(player.id) },
                                        onEndTurn = onEndTurn,
                                        onLandToggle = { onToggleLand(player.id) },
                                        onPlayerNameClick = onPlayerNameClick,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        ScreenedGridSlotPosition.BOTTOM -> {
                            // Extra top padding for TWO_TOP_BOTTOM to clear the center overlay
                            val extraPad = if (activeLayout.id == "2_top_bottom") 26.dp else 0.dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(sizeLayout.first)
                                    .padding(top = extraPad)
                            ) {
                                val slot = slots[0]
                                val actualPlayerId =
                                    gridAssignment.getOrDefault(slot.playerId, slot.playerId)
                                val player = players.find { it.id == actualPlayerId }
                                if (player != null) {
                                    val rotation = playerRotations[player.id]
                                        ?: slot.position.toDefaultDegrees()
                                    PlayerCard(
                                        player = player,
                                        gameMode = gameMode,
                                        rotation = rotation,
                                        isActive = player.id == activePlayerId,
                                        turnNumber = turnNumber,
                                        landPlayed = player.id in hasPlayedLand,
                                        onLife = { d -> onLifeChange(player.id, d) },
                                        onCmdPanel = { onCmdPanel(player.id) },
                                        onCtrPanel = { onCtrPanel(player.id) },
                                        onConfirmDefeat = { onConfirmDefeat(player.id) },
                                        onRevokeDefeat = { onRevokeDefeat(player.id) },
                                        onEndTurn = onEndTurn,
                                        onLandToggle = { onToggleLand(player.id) },
                                        onPlayerNameClick = onPlayerNameClick,
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
                                            val slotPlayerId = leftPlayers[index].playerId
                                            val actualPlayerId = gridAssignment.getOrDefault(
                                                slotPlayerId,
                                                slotPlayerId
                                            )
                                            val player = players.find { it.id == actualPlayerId }
                                            if (player != null) {
                                                val rotation = playerRotations[player.id]
                                                    ?: slot.position.toDefaultDegrees()
                                                PlayerCard(
                                                    player = player,
                                                    gameMode = gameMode,
                                                    rotation = rotation,
                                                    isActive = player.id == activePlayerId,
                                                    turnNumber = turnNumber,
                                                    landPlayed = player.id in hasPlayedLand,
                                                    onLife = { d -> onLifeChange(player.id, d) },
                                                    onCmdPanel = { onCmdPanel(player.id) },
                                                    onCtrPanel = { onCtrPanel(player.id) },
                                                    onConfirmDefeat = { onConfirmDefeat(player.id) },
                                                    onRevokeDefeat = { onRevokeDefeat(player.id) },
                                                    onEndTurn = onEndTurn,
                                                    onLandToggle = { onToggleLand(player.id) },
                                                    onPlayerNameClick = onPlayerNameClick,
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
                                            val slotPlayerId = rightPlayers[index].playerId
                                            val actualPlayerId = gridAssignment.getOrDefault(
                                                slotPlayerId,
                                                slotPlayerId
                                            )
                                            val player = players.find { it.id == actualPlayerId }
                                            if (player != null) {
                                                val rotation = playerRotations[player.id]
                                                    ?: slot.position.toDefaultDegrees()
                                                PlayerCard(
                                                    player = player,
                                                    gameMode = gameMode,
                                                    rotation = rotation,
                                                    isActive = player.id == activePlayerId,
                                                    turnNumber = turnNumber,
                                                    landPlayed = player.id in hasPlayedLand,
                                                    onLife = { d -> onLifeChange(player.id, d) },
                                                    onCmdPanel = { onCmdPanel(player.id) },
                                                    onCtrPanel = { onCtrPanel(player.id) },
                                                    onConfirmDefeat = { onConfirmDefeat(player.id) },
                                                    onRevokeDefeat = { onRevokeDefeat(player.id) },
                                                    onEndTurn = onEndTurn,
                                                    onLandToggle = { onToggleLand(player.id) },
                                                    onPlayerNameClick = onPlayerNameClick,
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
            onReset = onReset,
            onAbandonGame = onAbandonGame,
            onExitGame = onExitGame,
            onManagePlayers = onManagePlayers,
            onTournament = onTournament,
            turnNumber = turnNumber,
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize(),
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Player card (adaptive — 3 size tiers)
// ─────────────────────────────────────────────────────────────────────────────

/** Size tier for adaptive PlayerCard rendering. */
private enum class CardTier { LARGE, SMALL, TINY }

@Composable
private fun PlayerCard(
    player: Player,
    gameMode: GameMode,
    rotation: Int,
    isActive: Boolean,
    turnNumber: Int,
    landPlayed: Boolean,
    onLife: (Int) -> Unit,
    onCmdPanel: () -> Unit,
    onCtrPanel: () -> Unit,
    onEndTurn: () -> Unit,
    onLandToggle: () -> Unit,
    onConfirmDefeat: () -> Unit,
    onRevokeDefeat: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayerNameClick: () -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    val startingLife = gameMode.startingLife

    val lifeColor = when {
        player.life <= 0 -> mc.lifeNegative
        player.life > startingLife -> mc.lifePositive
        else -> mc.textPrimary
    }

    BoxWithConstraints(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val isRotated = rotation % 180 != 0
        val cardWidth = if (isRotated) maxHeight else maxWidth
        val cardHeight = if (isRotated) maxWidth else maxHeight

        val minDim = minOf(cardWidth, cardHeight)
        val tier = when {
            minDim > 150.dp -> CardTier.LARGE
            minDim >= 80.dp -> CardTier.SMALL
            else -> CardTier.TINY
        }

        val hPad = when (tier) {
            CardTier.LARGE -> 12.dp; CardTier.SMALL -> 8.dp; CardTier.TINY -> 4.dp
        }
        val vPad = when (tier) {
            CardTier.LARGE -> 6.dp; CardTier.SMALL -> 4.dp; CardTier.TINY -> 2.dp
        }

        PlayerCardSurface(
            isActive = isActive,
            theme = theme,
            rotation = rotation,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
        ) {
            ThemeBackground(modifier = Modifier.matchParentSize())

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

            // --- Main Card Layout: Using a Box to ensure absolute centering ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = vPad),
            ) {

                // ── Header: Name + Turn indicator (Top) ────────────────────────
                if (tier != CardTier.TINY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = player.name,
                            style = if (tier == CardTier.LARGE)
                                MaterialTheme.magicTypography.titleLarge
                            else
                                MaterialTheme.magicTypography.labelMedium,
                            color = theme.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onPlayerNameClick,
                                ),
                        )
                        if (isActive) {
                            EndTurnButton(tier = tier, theme = theme, onClick = onEndTurn)
                        }
                    }
                }

                // ── Life area: Absolutely Centered ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconSize = when (tier) {
                        CardTier.LARGE -> 32.dp; CardTier.SMALL -> 24.dp; CardTier.TINY -> 18.dp
                    }

                    // 2. Life group: Using weights to ensure the Text is ALWAYS at the absolute center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left container: occupies 1/2 of remaining space, icon aligned to end (near text)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (isActive && tier != CardTier.TINY) {
                                val alpha by animateFloatAsState(
                                    targetValue = if (landPlayed) 0.25f else 1.0f,
                                    animationSpec = tween(200),
                                    label = "landAlpha",
                                )
                                Icon(
                                    imageVector = Icons.Default.Landscape,
                                    contentDescription = stringResource(R.string.game_counters_button),
                                    tint = theme.accent.copy(alpha = alpha),
                                    modifier = Modifier
                                        .size(iconSize)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onLandToggle,
                                        ),
                                )
                            }
                        }

                        Text(
                            text = player.life.toString(),
                            style = (if (tier == CardTier.LARGE)
                                MaterialTheme.magicTypography.lifeNumber
                            else
                                MaterialTheme.magicTypography.lifeNumberMd).copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            ),
                            color = lifeColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 24.dp) // Gap between text and icons
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onLife(-1) },
                        )

                        // Right container: occupies 1/2 of remaining space, icon aligned to start (near text)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_heart),
                                contentDescription = stringResource(R.string.game_life_icon_desc),
                                tint = theme.accent,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onLife(1) },
                            )
                        }
                    }
                }
                // ── Footer: actions + active-player controls (Bottom) ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionSize = when (tier) {
                        CardTier.LARGE -> 32.dp; CardTier.SMALL -> 24.dp; CardTier.TINY -> 18.dp
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (gameMode == GameMode.COMMANDER && tier != CardTier.TINY) {
                            Icon(
                                painter = painterResource(R.drawable.ic_battle),
                                contentDescription = stringResource(R.string.game_commander_damage_desc),
                                tint = theme.accent,
                                modifier = Modifier
                                    .size(actionSize)
                                    .clickable { onCmdPanel() },
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_counter),
                            contentDescription = stringResource(R.string.game_counters_desc),
                            tint = theme.accent,
                            modifier = Modifier
                                .size(actionSize)
                                .clickable { onCtrPanel() },
                        )
                    }
                }
                if (tier != CardTier.TINY) {
                    PlayerCounterChips(
                        player = player,
                        theme = theme,
                        tier = tier,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp),
                        onCtl = { onCtrPanel() }
                    )
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

            // ── Defeated overlay ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = player.defeated,
                enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.85f),
            ) {
                EliminatedOverlay(player = player, mode = gameMode)
            }
        }
    }
}

/**
 * Optimized surface for PlayerCard to isolate glow animations and avoid
 * unnecessary recompositions of the entire card content.
 */
@Composable
private fun PlayerCardSurface(
    isActive: Boolean,
    theme: PlayerThemeColors,
    rotation: Int,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    content: @Composable BoxScope.() -> Unit
) {
    // Active glow pulse — only computed for the active player
    val glowAlpha: Float = if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "activeGlow")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.40f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glowAlpha",
        )
        anim
    } else 0.25f

    Box(
        modifier = Modifier
            .requiredSize(width = cardWidth, height = cardHeight)
            .graphicsLayer { rotationZ = rotation.toFloat() }
            .coloredShadow(
                color = theme.accent.copy(alpha = glowAlpha),
                blurRadius = if (isActive) 28.dp else 20.dp,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(theme.background)
            .border(
                width = if (isActive) 2.5.dp else 0.5.dp,
                color = theme.accent.copy(alpha = if (isActive) 1.0f else 0.40f),
                shape = RoundedCornerShape(16.dp),
            ),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  End turn button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EndTurnButton(
    tier: CardTier,
    theme: PlayerThemeColors,
    onClick: () -> Unit,
) {
    if (tier == CardTier.LARGE) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(theme.accent.copy(alpha = 0.15f))
                .border(1.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.game_end_turn),
                style = MaterialTheme.magicTypography.labelSmall,
                color = theme.accent,
                fontSize = 14.sp,
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (tier == CardTier.SMALL) 22.dp else 18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(theme.accent.copy(alpha = 0.15f))
                .border(1.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.game_end_turn),
                tint = theme.accent,
                modifier = Modifier.size(if (tier == CardTier.SMALL) 14.dp else 11.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Counter chips row (compact display inside PlayerCard)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerCounterChips(
    player: Player,
    theme: PlayerThemeColors,
    tier: CardTier,
    modifier: Modifier = Modifier,
    onCtl: () -> Unit = {}
) {
    // Collect non-zero counters: (iconKey, value) pairs
    val activeCounters = buildList {
        if (player.poison > 0) add(Pair(CounterIconKey.POISON, player.poison))
        if (player.experience > 0) add(Pair(CounterIconKey.EXPERIENCE, player.experience))
        if (player.energy > 0) add(Pair(CounterIconKey.ENERGY, player.energy))
        player.customCounters.filter { it.value != 0 }.forEach { c ->
            add(Pair(c.iconKey, c.value))
        }
    }
    if (activeCounters.isEmpty()) return

    val mc = MaterialTheme.magicColors
    val chipHeight = if (tier == CardTier.LARGE) 24.dp else 16.dp
    val iconSize = if (tier == CardTier.LARGE) 16.dp else 14.dp
    val textSize = if (tier == CardTier.LARGE) 16.sp else 12.sp
    val hPad = if (tier == CardTier.LARGE) 4.dp else 4.dp

    // Grow horizontally up to 4 items before wrapping
    val maxItemsPerRow = if (tier == CardTier.LARGE) 4 else 3

    FlowRow(
        modifier = modifier.clickable {
            onCtl()
        },
        horizontalArrangement = Arrangement.spacedBy(
            if (tier == CardTier.LARGE) 4.dp else 3.dp,
            Alignment.End
        ),
        verticalArrangement = Arrangement.spacedBy(if (tier == CardTier.LARGE) 4.dp else 3.dp),
        maxItemsInEachRow = maxItemsPerRow,
    ) {
        activeCounters.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .height(chipHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.accent.copy(alpha = 0.10f))
                    .padding(horizontal = hPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CounterIconView(
                    iconKey = key,
                    tint = theme.accent,
                    modifier = Modifier
                        .size(iconSize)
                        .align(Alignment.CenterVertically) // Asegura el centro exacto
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "$value",
                    color = mc.textPrimary,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically), // Alineación explícita
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both // Cambia None por Both para eliminar espacios extra arriba/abajo
                        )
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Counter icon renderer — maps icon key to composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CounterIconView(
    iconKey: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Mana keys (mana_w → "W", mana_u → "U", …): use official Scryfall SVG, same as AvatarPickerSheet
    if (iconKey.startsWith("mana_")) {
        ManaSymbolImage(token = iconKey.removePrefix("mana_").uppercase(), modifier = modifier)
        return
    }
    when (iconKey) {
        CounterIconKey.POISON ->
            Icon(
                painter = painterResource(R.drawable.ic_poison),
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )

        CounterIconKey.EXPERIENCE ->
            Icon(
                painter = painterResource(R.drawable.ic_experience),
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )

        CounterIconKey.ENERGY ->
            Image(
                painter = painterResource(R.drawable.ic_energy),
                contentDescription = null,
                modifier = modifier
            )

        CounterIconKey.STAR ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )

        CounterIconKey.SWORD ->
            Icon(
                painter = painterResource(R.drawable.ic_battle),
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )

        CounterIconKey.SHIELD ->
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )

        else ->
            Icon(
                painter = painterResource(R.drawable.ic_counter),
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )
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
//  Counters panel — full-featured bottom sheet with icon support
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountersPanel(
    player: Player,
    mode: GameMode,
    onCounter: (CounterType, Int) -> Unit,
    onCustomChange: (Long, Int) -> Unit,
    onCustomRemove: (Long) -> Unit,
    onAddCustom: (name: String, iconKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    var newCounterName by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf(CounterIconKey.DEFAULT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
    ) {
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

            // ── Built-in counters with icons ──────────────────────────────────
            BuiltInCounterRow(
                iconKey = CounterIconKey.POISON,
                label = stringResource(R.string.game_poison_label),
                value = player.poison,
                theme = theme,
                onDelta = { onCounter(CounterType.POISON, it) },
            )
            BuiltInCounterRow(
                iconKey = CounterIconKey.EXPERIENCE,
                label = stringResource(R.string.game_experience_label),
                value = player.experience,
                theme = theme,
                onDelta = { onCounter(CounterType.EXPERIENCE, it) },
            )
            BuiltInCounterRow(
                iconKey = CounterIconKey.ENERGY,
                label = stringResource(R.string.game_energy_label),
                value = player.energy,
                theme = theme,
                onDelta = { onCounter(CounterType.ENERGY, it) },
            )


            // ── Custom counters ───────────────────────────────────────────────
            if (player.customCounters.isNotEmpty()) {
                HorizontalDivider(color = mc.surfaceVariant)
                player.customCounters.forEach { counter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CounterIconView(
                            iconKey = counter.iconKey,
                            tint = theme.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
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
            }

            // ── Add custom counter ────────────────────────────────────────────
            HorizontalDivider(color = mc.surfaceVariant)

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
                            onAddCustom(newCounterName, selectedIconKey)
                            newCounterName = ""
                            selectedIconKey = CounterIconKey.DEFAULT
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint = mc.primaryAccent
                    )
                }
            }

            // Icon picker
            Text(
                stringResource(R.string.game_counter_choose_icon),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CounterIconKey.ALL) { key ->
                    val isSelected = key == selectedIconKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) mc.primaryAccent.copy(alpha = 0.20f)
                                else mc.surface
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { selectedIconKey = key },
                    ) {
                        CounterIconView(
                            iconKey = key,
                            tint = if (isSelected) mc.primaryAccent else mc.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Built-in counter row (icon + label + counter controls)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuiltInCounterRow(
    iconKey: String,
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
        CounterIconView(
            iconKey = iconKey,
            tint = theme.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textPrimary,
            modifier = Modifier.weight(1f),
        )
        CounterRow(
            value = value,
            theme = theme,
            onDecrement = { onDelta(-1) },
            onIncrement = { onDelta(+1) },
        )
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
//  Manage players sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet with three tabs for managing layout, player properties, and turn order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePlayersSheet(
    players: List<Player>,
    activeLayout: LayoutTemplate,
    gridAssignment: Map<Int, Int>,
    playerCount: Int,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSelectLayout: (LayoutTemplate) -> Unit,
    onSwapGridSlots: (slotIdA: Int, slotIdB: Int) -> Unit,
    onRenamePlayer: (playerId: Int, name: String) -> Unit,
    onUpdateTheme: (playerId: Int, theme: PlayerThemeColors) -> Unit,
    onReorderTurnOrder: (orderedPlayerIds: List<Int>) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = mc.backgroundSecondary,
                contentColor = mc.primaryAccent,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.game_manage_tab_layout),
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.game_manage_tab_players),
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            stringResource(R.string.game_manage_tab_turn_order),
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    },
                )
            }

            when (selectedTab) {
                0 -> LayoutTab(
                    playerCount = playerCount,
                    activeLayout = activeLayout,
                    onSelectLayout = onSelectLayout,
                )

                1 -> PlayersTab(
                    players = players,
                    gridAssignment = gridAssignment,
                    activeLayout = activeLayout,
                    onSwapGridSlots = onSwapGridSlots,
                    onRenamePlayer = onRenamePlayer,
                    onUpdateTheme = onUpdateTheme,
                )

                2 -> TurnOrderTab(
                    players = players,
                    onReorderTurnOrder = onReorderTurnOrder,
                )
            }
        }
    }
}

// ── Tab 0: Layout ─────────────────────────────────────────────────────────────

@Composable
private fun LayoutTab(
    playerCount: Int,
    activeLayout: LayoutTemplate,
    onSelectLayout: (LayoutTemplate) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val availableLayouts = remember(playerCount) {
        LayoutTemplates.getLayoutsForCount(playerCount)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.game_manage_layout_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(availableLayouts) { layout ->
                val isSelected = layout == activeLayout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) mc.primaryAccent.copy(alpha = 0.20f) else mc.surface,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                    ),
                    modifier = Modifier
                        .size(width = 100.dp, height = 80.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelectLayout(layout) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = layout.name,
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = if (isSelected) mc.primaryAccent else mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Tab 1: Players (positions + properties) ───────────────────────────────────

@Composable
private fun PlayersTab(
    players: List<Player>,
    gridAssignment: Map<Int, Int>,
    activeLayout: LayoutTemplate,
    onSwapGridSlots: (slotIdA: Int, slotIdB: Int) -> Unit,
    onRenamePlayer: (playerId: Int, name: String) -> Unit,
    onUpdateTheme: (playerId: Int, theme: PlayerThemeColors) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    // First selected slot for swap (null = nothing selected yet)
    var selectedSlotId by remember { mutableStateOf<Int?>(null) }
    // All slot ids from the active layout
    val allSlots = remember(activeLayout) {
        activeLayout.gridRows.values.flatten()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Positions section ─────────────────────────────────────────────────
        Text(
            stringResource(R.string.game_manage_positions_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Text(
            stringResource(R.string.game_manage_positions_hint),
            style = MaterialTheme.magicTypography.labelMedium,
            color = mc.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allSlots.forEach { slot ->
                val actualPlayerId = gridAssignment.getOrDefault(slot.playerId, slot.playerId)
                val player = players.find { it.id == actualPlayerId }
                val isSelected = selectedSlotId == slot.playerId

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) mc.primaryAccent.copy(alpha = 0.25f)
                    else mc.surface,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) mc.primaryAccent
                        else player?.theme?.accent?.copy(alpha = 0.40f) ?: mc.surfaceVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            when {
                                selectedSlotId == null -> {
                                    selectedSlotId = slot.playerId
                                }

                                selectedSlotId == slot.playerId -> {
                                    selectedSlotId = null
                                }

                                else -> {
                                    onSwapGridSlots(selectedSlotId!!, slot.playerId)
                                    selectedSlotId = null
                                }
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (player != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(player.theme.accent)
                            )
                            Text(
                                player.name,
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // Clear button
            if (selectedSlotId != null) {
                TextButton(onClick = { selectedSlotId = null }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = mc.textSecondary,
                        style = MaterialTheme.magicTypography.labelMedium,
                    )
                }
            }
        }

        HorizontalDivider(color = mc.surfaceVariant)

        // ── Properties section ────────────────────────────────────────────────
        Text(
            stringResource(R.string.game_manage_properties_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        players.forEach { player ->
            PlayerPropertyRow(
                player = player,
                onRename = { name -> onRenamePlayer(player.id, name) },
                onUpdateTheme = { theme -> onUpdateTheme(player.id, theme) },
            )
        }
    }
}

@Composable
private fun PlayerPropertyRow(
    player: Player,
    onRename: (String) -> Unit,
    onUpdateTheme: (PlayerThemeColors) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var nameText by remember(player.id) { mutableStateOf(player.name) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(player.theme.accent)
            )
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                singleLine = true,
                label = { Text(player.name, color = mc.textDisabled) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                ),
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { if (nameText.isNotBlank()) onRename(nameText) }
                ),
            )
        }
        // Color swatches
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlayerTheme.ALL.forEach { themeOption ->
                val isCurrentTheme = themeOption == player.theme
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(themeOption.accent)
                        .border(
                            width = if (isCurrentTheme) 2.dp else 0.dp,
                            color = mc.textPrimary,
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onUpdateTheme(themeOption) },
                )
            }
        }
        HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.50f))
    }
}

// ── Tab 2: Turn Order ─────────────────────────────────────────────────────────

@Composable
private fun TurnOrderTab(
    players: List<Player>,
    onReorderTurnOrder: (orderedPlayerIds: List<Int>) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    // Local draft of the order — initialized from current players list
    var draftOrder by remember(players) { mutableStateOf(players.toList()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.game_manage_turn_order_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        draftOrder.forEachIndexed { index, player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(player.theme.accent)
                )
                Text(
                    player.name,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (index > 0) {
                            val mutable = draftOrder.toMutableList()
                            val temp = mutable[index - 1]
                            mutable[index - 1] = mutable[index]
                            mutable[index] = temp
                            draftOrder = mutable
                        }
                    },
                    enabled = index > 0,
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (index > 0) mc.textPrimary else mc.textDisabled,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        if (index < draftOrder.lastIndex) {
                            val mutable = draftOrder.toMutableList()
                            val temp = mutable[index + 1]
                            mutable[index + 1] = mutable[index]
                            mutable[index] = temp
                            draftOrder = mutable
                        }
                    },
                    enabled = index < draftOrder.lastIndex,
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (index < draftOrder.lastIndex) mc.textPrimary else mc.textDisabled,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onReorderTurnOrder(draftOrder.map { it.id }) },
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.game_manage_apply_order),
                color = mc.background,
            )
        }
    }
}