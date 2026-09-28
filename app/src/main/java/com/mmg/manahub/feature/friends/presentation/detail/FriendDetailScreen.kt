package com.mmg.manahub.feature.friends.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.onOverlayScrim
import com.mmg.manahub.core.ui.theme.overlayScrimSoft
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.friends.presentation.avatarInitial
import org.koin.androidx.compose.koinViewModel

private const val HEADER_AVATAR_SIZE = 44

/**
 * Friend detail: a header with the friend's avatar, nickname and game tag, then the Folder / Stats /
 * History tabs. The overflow menu removes the friend after a confirmation.
 *
 * @param onNavigateBack Pops this screen; every path goes through [dropUnlessResumed] so it runs once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    onNavigateBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onNavigateToTradeDetail: (proposalId: String, rootProposalId: String) -> Unit = { _, _ -> },
    viewModel: FriendDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    val navigateBack = dropUnlessResumed { onNavigateBack() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    val removeErrorMsg = stringResource(R.string.friends_detail_remove_error)
    val folderActions = remember(viewModel, onCardClick) {
        FriendFolderActions(
            onSubTabSelected = viewModel::selectFolderSubTab,
            onQueryChange = viewModel::onSearchQueryChange,
            onSearchSubmit = viewModel::onSearchSubmit,
            onClearText = viewModel::clearSearchText,
            onApplyAdvancedSearch = viewModel::applyAdvancedSearch,
            onRemoveCriterion = viewModel::removeCriterion,
            onClearNameExact = viewModel::clearNameExact,
            onClearSearch = viewModel::clearSearch,
            onLoadMore = viewModel::loadMoreCards,
            onRetryLoadMore = viewModel::retryLoadMore,
            onRetry = viewModel::retryCards,
            onCardClick = onCardClick,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendDetailViewModel.UiEvent.NavigateBack ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentOnNavigateBack()
            }
        }
    }

    LaunchedEffect(uiState.message) {
        when (uiState.message ?: return@LaunchedEffect) {
            FriendDetailMessage.REMOVE_FAILED -> toastState.show(removeErrorMsg, MagicToastType.ERROR)
        }
        viewModel.clearMessage()
    }

    if (showRemoveConfirm) {
        val friendName = uiState.friend?.nickname ?: ""
        MagicAlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = stringResource(R.string.friends_detail_remove_confirm_title),
            text = stringResource(R.string.friends_detail_remove_confirm_body, friendName),
            confirmLabel = stringResource(R.string.friends_detail_remove_confirm_ok),
            onConfirm = {
                showRemoveConfirm = false
                viewModel.removeFriend()
            },
            confirmColor = MagicCtaColor.Error,
            dismissLabel = stringResource(R.string.friends_remove_confirm_cancel),
            onDismiss = { showRemoveConfirm = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            val friend = uiState.friend
            when {
                uiState.isLoadingFriend -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    MagicLoadingSpinner()
                }

                friend == null -> FriendMissingState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onNavigateBack = navigateBack,
                )

                else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    FriendDetailHeader(
                        friend = friend,
                        onNavigateBack = navigateBack,
                        showMenu = showMenu,
                        menuEnabled = !uiState.isRemoving,
                        onShowMenuChange = { showMenu = it },
                        onRemoveFriendClick = { showRemoveConfirm = true },
                    )

                    ManaTabRow(
                        items = FriendTab.entries.map { tab ->
                            ManaTabItem(
                                label = tabLabel(tab).uppercase(),
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                            )
                        },
                    )

                    when (uiState.selectedTab) {
                        FriendTab.FOLDER -> FriendFolderTab(
                            uiState = uiState,
                            friendNickname = friend.nickname,
                            actions = folderActions,
                        )
                        FriendTab.STATS -> FriendStatsTab(uiState = uiState, onRetry = viewModel::retryStats)
                        FriendTab.HISTORY -> FriendHistoryTab(
                            friend = friend,
                            tradeHistory = uiState.tradeHistory,
                            onTradeClick = onNavigateToTradeDetail,
                        )
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FriendMissingState(modifier: Modifier, onNavigateBack: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.statusBarsPadding()) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = mc.textPrimary,
            )
        }
        EmptyState(
            title = stringResource(R.string.friends_detail_not_found_title),
            subtitle = stringResource(R.string.friends_detail_not_found_body),
            icon = Icons.Default.PersonOff,
            actionLabel = stringResource(R.string.action_back),
            onAction = onNavigateBack,
        )
    }
}

/** Hero header: the avatar (blurred) or an accent gradient under a fixed dark scrim with light ink. */
@Composable
private fun FriendDetailHeader(
    friend: Friend,
    onNavigateBack: () -> Unit,
    showMenu: Boolean,
    menuEnabled: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onRemoveFriendClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    // The scrim is palette-independent, so the ink is too: readable on all 12 themes incl. HallowedPrint.
    val ink = mc.onOverlayScrim

    Box(modifier = Modifier.fillMaxWidth()) {
        if (friend.avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(friend.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize().blur(spacing.lg),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.radialGradient(listOf(mc.primaryAccent.copy(alpha = 0.3f), mc.background))),
            )
        }
        Box(modifier = Modifier.matchParentSize().background(mc.overlayScrimSoft))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.xs, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = ink,
                )
            }
            Spacer(Modifier.width(spacing.xs))
            AvatarImage(
                avatarUrl = friend.avatarUrl,
                initials = avatarInitial(friend.nickname),
                size = HEADER_AVATAR_SIZE,
                // The surface disc keeps the accent initial readable over the dark scrim on light palettes.
                modifier = Modifier.border(spacing.xxs, ink, CircleShape).background(mc.surface, CircleShape),
            )
            Spacer(Modifier.width(spacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = friend.nickname,
                    style = ty.titleLarge,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend.gameTag.isNotBlank()) {
                    Spacer(Modifier.height(spacing.xxs))
                    Text(
                        text = friend.gameTag,
                        color = ink,
                        style = ty.labelSmall,
                        modifier = Modifier
                            .background(color = ink.copy(alpha = 0.15f), shape = ChipShape)
                            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
            }
            Box {
                IconButton(onClick = { onShowMenuChange(true) }, enabled = menuEnabled) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.friends_detail_more_options),
                        tint = ink,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onShowMenuChange(false) }) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.friends_remove_friend), color = mc.lifeNegative, style = ty.bodyMedium)
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

@Composable
private fun tabLabel(tab: FriendTab): String = when (tab) {
    FriendTab.FOLDER -> stringResource(R.string.friend_detail_tab_folder)
    FriendTab.STATS -> stringResource(R.string.friend_detail_tab_stats)
    FriendTab.HISTORY -> stringResource(R.string.friend_detail_tab_history)
}
