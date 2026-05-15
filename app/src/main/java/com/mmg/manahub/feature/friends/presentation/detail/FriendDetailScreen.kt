package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend

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
            topBar = {
                Surface(
                    color = mc.backgroundSecondary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = mc.backgroundSecondary,
                            titleContentColor = mc.textPrimary,
                            navigationIconContentColor = mc.textPrimary,
                            actionIconContentColor = mc.textPrimary,
                        ),
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        },
                        title = {
                            Text(
                                text = uiState.friend?.nickname ?: "",
                                style = MaterialTheme.magicTypography.titleLarge,
                                maxLines = 1,
                            )
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = null,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.friends_remove_friend),
                                                color = mc.lifeNegative,
                                                style = MaterialTheme.magicTypography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showRemoveConfirm = true
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
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
                FriendDetailHeader(friend = friend)

                // ── Tab row ───────────────────────────────────────────────────
                val tabs = FriendTab.entries
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = mc.backgroundSecondary,
                    contentColor = mc.primaryAccent,
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = tabLabel(tab),
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
                    )
                    FriendTab.STATS -> FriendStatsTab(
                        uiState = uiState,
                        onRetry = viewModel::retryStats,
                    )
                    FriendTab.HISTORY -> FriendHistoryTab(
                        friend = friend,
                        tradeHistory = uiState.tradeHistory,
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

/** Centred header block showing avatar, nickname, and game tag. */
@Composable
private fun FriendDetailHeader(friend: Friend) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarImage(
            avatarUrl = friend.avatarUrl,
            initials = friend.nickname.take(1).uppercase(),
            size = 80,
        )
        Text(
            text = friend.nickname,
            style = MaterialTheme.magicTypography.titleLarge,
            color = mc.textPrimary,
        )
        // Game tag badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(mc.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = friend.gameTag,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        }
    }
}

/**
 * Circular avatar that shows the friend's image when available,
 * or a single-letter initials fallback otherwise.
 */
@Composable
private fun AvatarImage(avatarUrl: String?, initials: String, size: Int) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(mc.primaryAccent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Text(
                initials,
                color = mc.primaryAccent,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.5).sp,
            )
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
