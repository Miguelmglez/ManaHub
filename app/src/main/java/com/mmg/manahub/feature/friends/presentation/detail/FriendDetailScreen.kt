package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import com.mmg.manahub.feature.friends.domain.model.Friend
import java.util.Locale

/**
 * Root composable for the friend detail screen.
 *
 * Displays the friend's avatar, nickname, and game tag in a header, followed by
 * tabbed content for Folder / Stats / History. The overflow menu in the top bar
 * provides a "Remove Friend" action with a confirmation dialog.
 *
 * @param onNavigateBack Callback invoked when the screen should close.
 * @param viewModel      Hilt-injected ViewModel; [FriendDetailViewModel] reads the
 *                       friend's user ID from [androidx.lifecycle.SavedStateHandle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    onNavigateBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onNavigateToTradeDetail: (proposalId: String, rootProposalId: String) -> Unit = { _, _ -> },
    viewModel: FriendDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val removeErrorMsg = stringResource(R.string.friends_detail_remove_error)

    // Observe one-shot events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendDetailViewModel.UiEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    // Show toast when a message appears in state.
    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage ?: return@LaunchedEffect
        toastState.show(msg, uiState.toastType)
        viewModel.clearToast()
    }

    // ── Remove friend confirmation dialog ──────────────────────────────────────
    if (showRemoveConfirm) {
        val friendName = uiState.friend?.nickname ?: ""
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text(
                    stringResource(R.string.friends_detail_remove_confirm_title),
                    color = mc.textPrimary,
                    style = MaterialTheme.magicTypography.titleMedium,
                )
            },
            text = {
                Text(
                    stringResource(R.string.friends_detail_remove_confirm_body, friendName),
                    color = mc.textSecondary,
                    style = MaterialTheme.magicTypography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    viewModel.removeFriend(removeErrorMsg)
                }) {
                    Text(
                        stringResource(R.string.friends_detail_remove_confirm_ok),
                        color = mc.lifeNegative,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(
                        stringResource(R.string.friends_remove_confirm_cancel),
                        color = mc.textSecondary,
                    )
                }
            },
            containerColor = mc.backgroundSecondary,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
        ) { padding ->

            if (uiState.isLoadingFriend) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
                return@Scaffold
            }

            val friend = uiState.friend ?: return@Scaffold

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ── Friend header ─────────────────────────────────────────────
                FriendDetailHeader(
                    friend = friend,
                    onNavigateBack = onNavigateBack,
                    showMenu = showMenu,
                    onShowMenuChange = { showMenu = it },
                    onRemoveFriendClick = { showRemoveConfirm = true }
                )

                // ── Tab row ───────────────────────────────────────────────────
                val tabs = FriendTab.entries
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = mc.backgroundSecondary.copy(alpha = 0.9f),
                    contentColor = mc.primaryAccent,
                    divider = {}
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = tabLabel(tab).uppercase(Locale.getDefault()),
                                    style = MaterialTheme.magicTypography.labelLarge,
                                )
                            },
                        )
                    }
                }

                // ── Tab content ───────────────────────────────────────────────
                when (uiState.selectedTab) {
                    FriendTab.FOLDER -> FriendFolderTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        friendNickname = friend.nickname,
                        onCardClick = onCardClick,
                    )
                    FriendTab.STATS -> FriendStatsTab(
                        uiState = uiState,
                        onRetry = viewModel::retryStats,
                    )
                    FriendTab.HISTORY -> FriendHistoryTab(
                        friend = friend,
                        tradeHistory = uiState.tradeHistory,
                        onTradeClick = onNavigateToTradeDetail,
                        gameHistory = uiState.gameHistory,
                        isLoadingGameHistory = uiState.isLoadingGameHistory,
                        gameHistoryError = uiState.gameHistoryError,
                        onRetryGameHistory = viewModel::retryGameHistory,
                    )
                }
            }
        }

        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private composables
// ─────────────────────────────────────────────────────────────────────────────

/** Hero-style header block showing avatar background and game tag. */
@Composable
private fun FriendDetailHeader(
    friend: Friend,
    onNavigateBack: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onRemoveFriendClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (friend.avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(friend.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .blur(16.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mc.primaryAccent.copy(alpha = 0.3f),
                                mc.background,
                            ),
                        ),
                    ),
            )
        }

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.7f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(78.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = mc.textPrimary
                )
            }
            
            Spacer(Modifier.width(4.dp))
            
            if (friend.avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(friend.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, mc.primaryAccent, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, mc.primaryAccent, CircleShape)
                        .background(mc.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.nickname.take(1).uppercase().ifEmpty { "✦" },
                        style = ty.labelLarge.copy(color = mc.textPrimary),
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = friend.nickname,
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Skip the game-tag chip (and its leading spacer) entirely when blank, so a
                // friend without a tag does not render an empty pill + dead vertical gap.
                if (friend.gameTag.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(
                            text = friend.gameTag,
                            color = mc.primaryAccent,
                            style = ty.labelSmall.copy(fontSize = 11.sp),
                            modifier = Modifier
                                .background(
                                    color = mc.primaryAccent.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Box {
                IconButton(onClick = { onShowMenuChange(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = mc.textPrimary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.friends_remove_friend),
                                color = mc.lifeNegative,
                                style = ty.bodyMedium,
                            )
                        },
                        onClick = {
                            onShowMenuChange(false)
                            onRemoveFriendClick()
                        },
                    )
                }
            }
        }
    }
}

/** Returns the display label for each top-level tab. */
@Composable
private fun tabLabel(tab: FriendTab): String = when (tab) {
    FriendTab.FOLDER -> stringResource(R.string.friend_detail_tab_folder)
    FriendTab.STATS -> stringResource(R.string.friend_detail_tab_stats)
    FriendTab.HISTORY -> stringResource(R.string.friend_detail_tab_history)
}
