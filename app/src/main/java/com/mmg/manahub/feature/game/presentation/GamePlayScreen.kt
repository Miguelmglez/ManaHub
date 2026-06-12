package com.mmg.manahub.feature.game.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.tooling.preview.Preview
import com.mmg.manahub.core.ui.theme.MagicTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.voice.domain.VoiceCommand
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.CounterIconKey
import com.mmg.manahub.feature.game.domain.model.CounterType
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GridSlotPosition
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import com.mmg.manahub.feature.game.domain.model.LayoutTemplates
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.ScreenedGridSlotPosition
import com.mmg.manahub.feature.game.domain.model.toDefaultDegrees
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAnyVoiceEnabled = uiState.gameSettings.voiceLandReminderEnabled || uiState.gameSettings.voiceEndTurnEnabled
    DisposableEffect(lifecycleOwner, isAnyVoiceEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (isAnyVoiceEnabled) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.startVoiceListening()
                    Lifecycle.Event.ON_PAUSE  -> viewModel.stopVoiceListening()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopVoiceListening()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showManagePlayersSheet by remember { mutableStateOf(false) }
    var managePlayersInitialTab by remember { mutableIntStateOf(0) }
    var confirmDefeatPlayerId by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = uiState.winner == null) {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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
                    gameSettings = uiState.gameSettings,
                    isOnlineSession = uiState.isOnlineSession,
                    onLifeChange = onLifeChange,
                    onCmdPanel = onCmdPanel,
                    onCtrPanel = onCtrPanel,
                    onConfirmDefeat = { confirmDefeatPlayerId = it },
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

        confirmDefeatPlayerId?.let { playerId ->
            val player = uiState.players.find { it.id == playerId } ?: return@let
            ConfirmDefeatSheet(
                player = player,
                onConfirm = {
                    onConfirmDefeat(playerId)
                    confirmDefeatPlayerId = null
                },
                onDismiss = { confirmDefeatPlayerId = null }
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



        }

    uiState.winner?.let { winner ->
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
        ) {
            uiState.gameResult?.let { result ->
                GameResultScreen(
                    gameResult = result,
                    onNewGame = { onResetGame(); onNewGame() },
                    onBackHome = onBackHome,
                    onSurvey = { onSurvey(uiState.lastSessionId ?: 0L) },
                    sessionId = uiState.lastSessionId,
                )
            }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Player grid  (template-driven layout)
// ─────────────────────────────────────────────────────────────────────────────

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
    gameSettings: GameSettings,
    isOnlineSession: Boolean,
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
    val (targetFirst, targetSecond) = when (activeLayout.gridRows.keys.size) {
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

    val firstWeight by animateFloatAsState(
        targetValue = targetFirst,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "firstWeight"
    )
    val secondWeight by animateFloatAsState(
        targetValue = targetSecond,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "secondWeight"
    )

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
                                    .weight(firstWeight.coerceAtLeast(0.01f))
                                    .padding(bottom = extraPad)
                            ) {
                                val slot = slots[0]
                                val actualPlayerId =
                                    gridAssignment.getOrDefault(slot.playerId, slot.playerId)
                                val player = players.find { it.id == actualPlayerId }
                                if (player != null) {
                                    key(player.id) {
                                        val rotation = playerRotations[player.id]
                                            ?: slot.position.toDefaultDegrees()
                                        val isInteractable = !isOnlineSession || player.isAppUser
                                        PlayerCard(
                                            player = player,
                                            gameMode = gameMode,
                                            rotation = rotation,
                                            isActive = player.id == activePlayerId,
                                            turnNumber = turnNumber,
                                            landPlayed = player.id in hasPlayedLand,
                                            gameSettings = gameSettings,
                                            onLife = if (isInteractable) { d -> onLifeChange(player.id, d) } else { _ -> },
                                            onCmdPanel = if (isInteractable) { { onCmdPanel(player.id) } } else { {} },
                                            onCtrPanel = if (isInteractable) { { onCtrPanel(player.id) } } else { {} },
                                            onConfirmDefeat = if (isInteractable) { { onConfirmDefeat(player.id) } } else { {} },
                                            onRevokeDefeat = if (isInteractable) { { onRevokeDefeat(player.id) } } else { {} },
                                            onEndTurn = if (isInteractable) onEndTurn else { {} },
                                            onLandToggle = if (isInteractable) { { onToggleLand(player.id) } } else { {} },
                                            onPlayerNameClick = onPlayerNameClick,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }

                        ScreenedGridSlotPosition.BOTTOM -> {
                            // Extra top padding for TWO_TOP_BOTTOM to clear the center overlay
                            val extraPad = if (activeLayout.id == "2_top_bottom") 26.dp else 0.dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(firstWeight.coerceAtLeast(0.01f))
                                    .padding(top = extraPad)
                            ) {
                                val slot = slots[0]
                                val actualPlayerId =
                                    gridAssignment.getOrDefault(slot.playerId, slot.playerId)
                                val player = players.find { it.id == actualPlayerId }
                                if (player != null) {
                                    key(player.id) {
                                        val rotation = playerRotations[player.id]
                                            ?: slot.position.toDefaultDegrees()
                                        val isInteractable = !isOnlineSession || player.isAppUser
                                        PlayerCard(
                                            player = player,
                                            gameMode = gameMode,
                                            rotation = rotation,
                                            isActive = player.id == activePlayerId,
                                            turnNumber = turnNumber,
                                            landPlayed = player.id in hasPlayedLand,
                                            gameSettings = gameSettings,
                                            onLife = if (isInteractable) { d -> onLifeChange(player.id, d) } else { _ -> },
                                            onCmdPanel = if (isInteractable) { { onCmdPanel(player.id) } } else { {} },
                                            onCtrPanel = if (isInteractable) { { onCtrPanel(player.id) } } else { {} },
                                            onConfirmDefeat = if (isInteractable) { { onConfirmDefeat(player.id) } } else { {} },
                                            onRevokeDefeat = if (isInteractable) { { onRevokeDefeat(player.id) } } else { {} },
                                            onEndTurn = if (isInteractable) onEndTurn else { {} },
                                            onLandToggle = if (isInteractable) { { onToggleLand(player.id) } } else { {} },
                                            onPlayerNameClick = onPlayerNameClick,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
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
                                    .weight(secondWeight.coerceAtLeast(0.01f))
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
                                                key(player.id) {
                                                    val rotation = playerRotations[player.id]
                                                        ?: slot.position.toDefaultDegrees()
                                                    val isInteractable = !isOnlineSession || player.isAppUser
                                                    PlayerCard(
                                                        player = player,
                                                        gameMode = gameMode,
                                                        rotation = rotation,
                                                        isActive = player.id == activePlayerId,
                                                        turnNumber = turnNumber,
                                                        landPlayed = player.id in hasPlayedLand,
                                                        gameSettings = gameSettings,
                                                        onLife = if (isInteractable) { d -> onLifeChange(player.id, d) } else { _ -> },
                                                        onCmdPanel = if (isInteractable) { { onCmdPanel(player.id) } } else { {} },
                                                        onCtrPanel = if (isInteractable) { { onCtrPanel(player.id) } } else { {} },
                                                        onConfirmDefeat = if (isInteractable) { { onConfirmDefeat(player.id) } } else { {} },
                                                        onRevokeDefeat = if (isInteractable) { { onRevokeDefeat(player.id) } } else { {} },
                                                        onEndTurn = if (isInteractable) onEndTurn else { {} },
                                                        onLandToggle = if (isInteractable) { { onToggleLand(player.id) } } else { {} },
                                                        onPlayerNameClick = onPlayerNameClick,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
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
                                                key(player.id) {
                                                    val rotation = playerRotations[player.id]
                                                        ?: slot.position.toDefaultDegrees()
                                                    val isInteractable = !isOnlineSession || player.isAppUser
                                                    PlayerCard(
                                                        player = player,
                                                        gameMode = gameMode,
                                                        rotation = rotation,
                                                        isActive = player.id == activePlayerId,
                                                        turnNumber = turnNumber,
                                                        landPlayed = player.id in hasPlayedLand,
                                                        gameSettings = gameSettings,
                                                        onLife = if (isInteractable) { d -> onLifeChange(player.id, d) } else { _ -> },
                                                        onCmdPanel = if (isInteractable) { { onCmdPanel(player.id) } } else { {} },
                                                        onCtrPanel = if (isInteractable) { { onCtrPanel(player.id) } } else { {} },
                                                        onConfirmDefeat = if (isInteractable) { { onConfirmDefeat(player.id) } } else { {} },
                                                        onRevokeDefeat = if (isInteractable) { { onRevokeDefeat(player.id) } } else { {} },
                                                        onEndTurn = if (isInteractable) onEndTurn else { {} },
                                                        onLandToggle = if (isInteractable) { { onToggleLand(player.id) } } else { {} },
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
    gameSettings: GameSettings,
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
    val mt = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    val startingLife = gameMode.startingLife

    val lifeColor = when {
        player.life <= 0 -> mc.lifeNegative
        player.life > startingLife -> player.theme.accent
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

        var dragAccumulator by remember(player.id) { mutableFloatStateOf(0f) }

        // Land played animation — triggers on false→true transition of [landPlayed].
        var showLandAnimation by remember(player.id) { mutableStateOf(false) }
        val landIconScale = remember(player.id) { Animatable(1f) }
        val landRingScale = remember(player.id) { Animatable(0f) }
        val landRingAlpha = remember(player.id) { Animatable(0f) }

        LaunchedEffect(landPlayed) {
            if (landPlayed) {
                showLandAnimation = true
                launch {
                    landIconScale.snapTo(0.8f)
                    landIconScale.animateTo(1.3f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    landIconScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
                }
                launch {
                    landRingScale.snapTo(1f)
                    landRingAlpha.snapTo(0.7f)
                    landRingScale.animateTo(2.5f, tween(400, easing = FastOutSlowInEasing))
                }
                launch {
                    landRingAlpha.animateTo(0f, tween(400))
                }
                delay(500)
                showLandAnimation = false
            }
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
                        Column {
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
                                    .clickable(
                                        interactionSource = remember(player.id) { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onPlayerNameClick,
                                    ),
                            )
                            Text(
                                text = stringResource(R.string.game_turn_indicator, turnNumber),
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = MaterialTheme.magicColors.goldMtg,
                            )
                        }
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
                            if (gameSettings.landReminderEnabled && isActive && tier != CardTier.TINY) {
                                val landIconAlpha by animateFloatAsState(
                                    targetValue = if (landPlayed) 0.25f else 1.0f,
                                    animationSpec = tween(200),
                                    label = "landAlpha",
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    if (showLandAnimation) {
                                        Box(
                                            modifier = Modifier
                                                .size(iconSize)
                                                .graphicsLayer {
                                                    scaleX = landRingScale.value
                                                    scaleY = landRingScale.value
                                                    this.alpha = landRingAlpha.value
                                                }
                                                .border(1.5.dp, theme.accent, CircleShape)
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(R.drawable.ic_land),
                                        contentDescription = stringResource(R.string.gamesetup_land_reminder_title),
                                        tint = theme.accent.copy(alpha = landIconAlpha),
                                        modifier = Modifier
                                            .size(iconSize)
                                            .graphicsLayer {
                                                scaleX = landIconScale.value
                                                scaleY = landIconScale.value
                                            }
                                            .clickable(
                                                interactionSource = remember(player.id) { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onLandToggle,
                                            ),
                                    )
                                }
                            }
                        }

                        if (gameSettings.lifeControlMode == LifeControlMode.SCROLL) {
                            Box(
                                modifier = Modifier
                                    .pointerInput(player.id) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulator += dragAmount
                                                val threshold = 80f
                                                if (dragAccumulator > threshold) {
                                                    onLife(1)
                                                    dragAccumulator = 0f
                                                } else if (dragAccumulator < -threshold) {
                                                    onLife(-1)
                                                    dragAccumulator = 0f
                                                }
                                            },
                                            onDragEnd = { dragAccumulator = 0f },
                                            onDragCancel = { dragAccumulator = 0f }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = player.life,
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                                    (slideOutVertically { height -> height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> height } + fadeIn()) togetherWith
                                                    (slideOutVertically { height -> -height } + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    },
                                    label = "lifeAnimation"
                                ) { targetLife ->
                                    Text(
                                        text = targetLife.toString(),
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
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {}
                        } else {
                            val numberScale = remember(player.id) { Animatable(1f) }
                            val heartScale  = remember(player.id) { Animatable(1f) }
                            val floatY      = remember(player.id) { Animatable(0f) }
                            val floatAlpha  = remember(player.id) { Animatable(0f) }
                            var lastDelta by remember(player.id) { mutableIntStateOf(0) }
                            val prevLife = remember(player.id) { androidx.compose.runtime.mutableIntStateOf(player.life) }

                            LaunchedEffect(player.life) {
                                val delta = player.life - prevLife.intValue
                                if (delta != 0) {
                                    lastDelta = delta
                                    numberScale.snapTo(0.82f)
                                    floatY.snapTo(0f)
                                    floatAlpha.snapTo(1f)
                                    launch {
                                        numberScale.animateTo(1.18f, spring(dampingRatio = 0.35f, stiffness = 700f))
                                        numberScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                                    }
                                    if (delta > 0) {
                                        launch {
                                            heartScale.snapTo(1f)
                                            heartScale.animateTo(1.55f, spring(dampingRatio = 0.3f, stiffness = 700f))
                                            heartScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
                                        }
                                    }
                                    launch { floatY.animateTo(-40f, tween(800)) }
                                    floatAlpha.animateTo(0f, tween(800, delayMillis = 300))
                                }
                                prevLife.intValue = player.life
                            }

                            Box(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember(player.id) { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onLife(-1) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
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
                                        .padding(horizontal = 24.dp)
                                        .graphicsLayer {
                                            scaleX = numberScale.value
                                            scaleY = numberScale.value
                                        },
                                )
                            }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier
                                        .size(iconSize)
                                        .graphicsLayer {
                                            scaleX = heartScale.value
                                            scaleY = heartScale.value
                                        }
                                        .clickable(
                                            interactionSource = remember(player.id) { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onLife(+1) },
                                        ),
                                )
                            }
                        }
                    }

                    // ── Confirm Defeat Button: Only if isSurviving and meeting defeat condition ────
                    val meetsDefeat = player.life <= 0 || player.poison >= 10 ||
                            (gameMode == GameMode.COMMANDER && player.commanderDamage.values.any { it >= 21 })

                    if (player.isSurviving && meetsDefeat) {
                        OutlinedButton(
                            onClick  = onConfirmDefeat,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = if (tier == CardTier.LARGE) 48.dp else 36.dp),
                            border   = BorderStroke(1.dp, theme.accent),
                        ) {
                            Text(
                                stringResource(R.string.game_pending_defeat_confirm),
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = theme.accent
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
                        CardTier.LARGE -> 28.dp; CardTier.SMALL -> 24.dp; CardTier.TINY -> 18.dp
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (gameMode == GameMode.COMMANDER) {
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

            // ── Land played animation overlay ─────────────────────────────────
            // REMOVED invasive overlay

            // ── Pending defeat overlay ────────────────────────────────────────
            AnimatedVisibility(
                visible = player.pendingDefeat && !player.isSurviving,
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
                        Text(text = stringResource(R.string.game_skull_symbol), style = mt.titleMedium, textAlign = TextAlign.Center)

                        Button(
                            onClick = onConfirmDefeat,
                            colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.game_pending_defeat_confirm),
                                style = mt.labelMedium
                            )
                        }
                        OutlinedButton(
                            onClick  = onRevokeDefeat,
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = mc.lifePositive,
                            ),
                            border   = BorderStroke(1.dp, mc.lifePositive),
                        ) {
                            Text(
                                stringResource(R.string.game_pending_defeat_revoke),
                                style = MaterialTheme.magicTypography.labelMedium,
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

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "cardScale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.40f,
        animationSpec = tween(400),
        label = "borderAlpha"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.5.dp else 0.5.dp,
        animationSpec = tween(400),
        label = "borderWidth"
    )

    Box(
        modifier = Modifier
            .requiredSize(width = cardWidth, height = cardHeight)
            .graphicsLayer {
                rotationZ = rotation.toFloat()
                scaleX = scale
                scaleY = scale
            }
            .coloredShadow(
                color = theme.accent.copy(alpha = glowAlpha),
                blurRadius = if (isActive) 28.dp else 20.dp,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(theme.background)
            .border(
                width = borderWidth,
                color = theme.accent.copy(alpha = borderAlpha),
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
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "endTurnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = theme.accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = if (tier == CardTier.TINY) 10.dp else 16.dp,
                    vertical = if (tier == CardTier.TINY) 6.dp else 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (tier == CardTier.TINY) "END" else stringResource(R.string.game_end_turn),
                style = when(tier) {
                    CardTier.LARGE -> MaterialTheme.magicTypography.labelLarge
                    CardTier.SMALL -> MaterialTheme.magicTypography.labelMedium
                    CardTier.TINY -> MaterialTheme.magicTypography.labelSmall.copy(fontSize = 9.sp)
                },
                color = theme.accent,
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
    val mt = MaterialTheme.magicTypography
    val chipHeight = if (tier == CardTier.LARGE) 24.dp else 16.dp
    val iconSize = if (tier == CardTier.LARGE) 16.dp else 14.dp
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
                    style = (if (tier == CardTier.LARGE) mt.labelLarge else mt.labelSmall).copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically),
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
    val mt = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
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
            Text(text = stringResource(R.string.game_skull_symbol), fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                text = stringResource(R.string.game_fallen_label),
                color = mc.lifeNegative,
                style = mt.displayMedium.copy(letterSpacing = 6.sp),
                textAlign = TextAlign.Center,
            )
            if (reason != null) {
                Text(
                    text = reason,
                    color = Color.White.copy(alpha = 0.70f),
                    style = mt.labelSmall,
                    textAlign = TextAlign.Center,
                )
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = mc.textSecondary
                    )
                }
            }
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
                            stringResource(R.string.game_caution_symbol),
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
            Spacer(Modifier.height(16.dp))
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
    val mt = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    val theme = player.theme
    var newCounterName by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf(CounterIconKey.DEFAULT) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = mc.textSecondary
                    )
                }
            }
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
                modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
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
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            if (it.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
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
                items(CounterIconKey.ALL, key = { it }) { key ->
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

            Spacer(Modifier.height(16.dp))
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
            Text(stringResource(R.string.action_remove_symbol), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
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
            Text(stringResource(R.string.action_add_symbol), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Manage players sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet to confirm player elimination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDefeatSheet(
    player: Player,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 8.dp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.game_skull_symbol),
                    style = ty.lifeNumberMd,
                    color = mc.lifeNegative
                )
                Text(
                    text = stringResource(R.string.game_pending_defeat_message, player.name),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = mc.lifePositive
                        ),
                        border = BorderStroke(1.dp, mc.lifePositive.copy(alpha = 0.5f))
                    ) {
                        Text(
                            stringResource(R.string.game_confirm_defeat_alive),
                            style = ty.labelLarge
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.lifeNegative
                        )
                    ) {
                        Text(
                            stringResource(R.string.game_confirm_defeat_defeat),
                            style = ty.labelLarge
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Bottom sheet with two tabs for managing layout/swaps and turn order.
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
    // Map initial tab: 0->0 (Manage), 1->0 (Manage), 2->1 (Turn Order)
    var selectedTab by remember { mutableIntStateOf(if (initialTab == 2) 1 else 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
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
                            stringResource(R.string.game_manage_tab_turn_order),
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    },
                )
            }

            when (selectedTab) {
                0 -> ManageTab(
                    players = players,
                    playerCount = playerCount,
                    activeLayout = activeLayout,
                    gridAssignment = gridAssignment,
                    onSelectLayout = onSelectLayout,
                    onSwapGridSlots = onSwapGridSlots,
                    onRenamePlayer = onRenamePlayer,
                    onUpdateTheme = onUpdateTheme,
                )

                1 -> TurnOrderTab(
                    players = players,
                    onReorderTurnOrder = onReorderTurnOrder,
                )
            }
        }
    }
}

// ── Tab 0: Manage (Layout + Swaps + Contextual Properties) ─────────────────

@Composable
private fun ManageTab(
    players: List<Player>,
    playerCount: Int,
    activeLayout: LayoutTemplate,
    gridAssignment: Map<Int, Int>,
    onSelectLayout: (LayoutTemplate) -> Unit,
    onSwapGridSlots: (slotIdA: Int, slotIdB: Int) -> Unit,
    onRenamePlayer: (playerId: Int, name: String) -> Unit,
    onUpdateTheme: (playerId: Int, theme: PlayerThemeColors) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var selectedSlotId by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 1. Layout Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.game_manage_layout_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            val availableLayouts = remember(playerCount) {
                LayoutTemplates.getLayoutsForCount(playerCount)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                availableLayouts.forEach { layout ->
                    val isSelected = layout == activeLayout
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.20f) else mc.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
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

        // 2. Interactive Mini-Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.game_manage_positions_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                stringResource(R.string.game_manage_positions_hint),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(mc.surface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                MiniGamePlayerGrid(
                    players = players,
                    activeLayout = activeLayout,
                    gridAssignment = gridAssignment,
                    selectedSlotId = selectedSlotId,
                    onSlotClick = { slotId ->
                        when {
                            selectedSlotId == null -> selectedSlotId = slotId
                            selectedSlotId == slotId -> selectedSlotId = null
                            else -> {
                                onSwapGridSlots(selectedSlotId!!, slotId)
                                selectedSlotId = slotId // Keep focus on the one we just moved? Or null? Let's null it for swap confirmation
                                selectedSlotId = null
                            }
                        }
                    }
                )
            }
        }

        // 3. Contextual Player Properties
        val actualSelectedPlayerId = selectedSlotId?.let { gridAssignment.getOrDefault(it, it) }
        val selectedPlayer = players.find { it.id == actualSelectedPlayerId }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .heightIn(min = 140.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = selectedPlayer,
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith
                            (fadeOut() + shrinkVertically())
                },
                label = "propertyTransition"
            ) { targetPlayer ->
                if (targetPlayer != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.game_manage_properties_title),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = mc.textPrimary,
                            )
                            TextButton(onClick = { selectedSlotId = null }) {
                                Text(stringResource(R.string.action_cancel), color = mc.primaryAccent)
                            }
                        }
                        PlayerPropertyRow(
                            player = targetPlayer,
                            onRename = { name -> onRenamePlayer(targetPlayer.id, name) },
                            onUpdateTheme = { theme -> onUpdateTheme(targetPlayer.id, theme) },
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.game_manage_positions_hint),
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.textDisabled,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniGamePlayerGrid(
    players: List<Player>,
    activeLayout: LayoutTemplate,
    gridAssignment: Map<Int, Int>,
    selectedSlotId: Int?,
    onSlotClick: (Int) -> Unit,
) {
    AnimatedContent(
        targetState = gridAssignment,
        transitionSpec = {
            (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f)) togetherWith
                    fadeOut(tween(400))
        },
        label = "gridSwap"
    ) { currentAssignment ->
        Column(modifier = Modifier.fillMaxSize()) {
            activeLayout.gridRows.forEach { (position, slots) ->
                val rowWeight = when (position) {
                    ScreenedGridSlotPosition.TOP, ScreenedGridSlotPosition.BOTTOM -> 0.25f
                    else -> 0.5f
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(rowWeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (position) {
                        ScreenedGridSlotPosition.TOP, ScreenedGridSlotPosition.BOTTOM -> {
                            val slot = slots[0]
                            MiniPlayerSlot(
                                slot = slot,
                                players = players,
                                gridAssignment = currentAssignment,
                                isSelected = selectedSlotId == slot.playerId,
                                onClick = { onSlotClick(slot.playerId) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            val leftPlayers = slots.filter { it.position == GridSlotPosition.LEFT }
                            val rightPlayers = slots.filter { it.position == GridSlotPosition.RIGHT }
                            
                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                leftPlayers.forEach { slot ->
                                    MiniPlayerSlot(
                                        slot = slot,
                                        players = players,
                                        gridAssignment = currentAssignment,
                                        isSelected = selectedSlotId == slot.playerId,
                                        onClick = { onSlotClick(slot.playerId) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rightPlayers.forEach { slot ->
                                    MiniPlayerSlot(
                                        slot = slot,
                                        players = players,
                                        gridAssignment = currentAssignment,
                                        isSelected = selectedSlotId == slot.playerId,
                                        onClick = { onSlotClick(slot.playerId) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (position != activeLayout.gridRows.keys.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerSlot(
    slot: com.mmg.manahub.feature.game.domain.model.PlayerSlot,
    players: List<Player>,
    gridAssignment: Map<Int, Int>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val actualPlayerId = gridAssignment.getOrDefault(slot.playerId, slot.playerId)
    val player = players.find { it.id == actualPlayerId }
    val rotation = slot.position.toDefaultDegrees()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.3f) else mc.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) mc.primaryAccent else (player?.theme?.accent?.copy(alpha = 0.5f) ?: mc.surfaceVariant)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
            if (player != null) {
                Text(
                    text = player.name,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = player.theme.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.rotateLayout(rotation)
                )
            }
        }
    }
}

/**
 * Utility to rotate layout by swapping constraints and adjusting placement.
 */
private fun Modifier.rotateLayout(degrees: Int): Modifier = layout { measurable, constraints ->
    val isRotated = degrees % 180 != 0
    val placeable = measurable.measure(
        if (isRotated) constraints.copy(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth
        ) else constraints
    )
    layout(
        if (isRotated) placeable.height else placeable.width,
        if (isRotated) placeable.width else placeable.height
    ) {
        placeable.placeWithLayer(
            x = if (isRotated) (placeable.height - placeable.width) / 2 else 0,
            y = if (isRotated) (placeable.width - placeable.height) / 2 else 0
        ) {
            rotationZ = degrees.toFloat()
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
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
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
                onValueChange = {
                    nameText = it
                    if (it.isNotBlank() && !player.isAppUser) {
                        onRename(it)
                    }
                },
                singleLine = true,
                readOnly = player.isAppUser,
                label = { Text(player.name, color = mc.textDisabled) },
                trailingIcon = if (player.isAppUser) {
                    {
                        Surface(
                            modifier = Modifier.padding(end = 8.dp),
                            color = mc.primaryAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = stringResource(R.string.game_setup_you_badge),
                                style = MaterialTheme.magicTypography.labelMedium.copy(fontSize = 10.sp),
                                color = mc.primaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (player.isAppUser) mc.surfaceVariant else mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        if (it.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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

// ─────────────────────────────────────────────────────────────────────────────
//  Mic listening indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MicListeningIndicator() {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micAlpha",
    )
    Surface(
        shape = CircleShape,
        color = mc.primaryAccent.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = alpha)),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.game_voice_listening_desc),
                tint = mc.primaryAccent.copy(alpha = alpha),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
