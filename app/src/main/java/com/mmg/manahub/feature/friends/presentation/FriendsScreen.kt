package com.mmg.manahub.feature.friends.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicProgressBar
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ShareProfileSheet
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import androidx.compose.foundation.clickable
import org.koin.androidx.compose.koinViewModel

private const val AVATAR_SIZE = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFriendDetail: (userId: String) -> Unit,
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val toastState = rememberMagicToastState()
    var showShareSheet by rememberSaveable { mutableStateOf(false) }

    if (showShareSheet) {
        ShareProfileSheet(
            gameTag = uiState.gameTag,
            onFetchShareLink = viewModel::fetchShareLink,
            onDismiss = { showShareSheet = false },
        )
    }

    val message = uiState.message
    val messageText = message?.let { friendsMessageText(it) }
    LaunchedEffect(message) {
        if (message == null || messageText == null) return@LaunchedEffect
        toastState.show(messageText, if (message.isError) MagicToastType.ERROR else MagicToastType.SUCCESS)
        viewModel.clearMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Surface(color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = spacing.xs, vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                        Text(
                            text = stringResource(R.string.friends_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                        )
                    }
                }
            },
        ) { padding ->
            if (!uiState.isLoggedIn) {
                EmptyState(
                    title = stringResource(R.string.friends_login_required),
                    icon = Icons.Default.Lock,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                return@Scaffold
            }

            FriendsList(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
                viewModel = viewModel,
                onShare = { showShareSheet = true },
                onNavigateToFriendDetail = onNavigateToFriendDetail,
            )
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FriendsList(
    uiState: FriendsViewModel.UiState,
    modifier: Modifier,
    viewModel: FriendsViewModel,
    onShare: () -> Unit,
    onNavigateToFriendDetail: (userId: String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (uiState.isRefreshing) {
            item(key = "refresh_progress", contentType = "progress") {
                MagicProgressBar(modifier = Modifier.fillMaxWidth())
            }
        }
        if (uiState.refreshFailed && !uiState.isRefreshing) {
            item(key = "refresh_error", contentType = "error") {
                InlineErrorState(
                    message = stringResource(R.string.friends_refresh_failed),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::retryRefresh,
                )
            }
        }

        // Keys are section-scoped: an accepted request id can briefly exist in two lists.
        if (uiState.pendingRequests.isNotEmpty()) {
            item(key = "incoming_header", contentType = "header") {
                SectionLabel(stringResource(R.string.friends_section_pending, uiState.pendingRequests.size))
            }
            items(uiState.pendingRequests, key = { FriendsListKeys.incoming(it.id) }, contentType = { "incoming" }) { request ->
                PendingRequestRow(
                    request = request,
                    inFlight = request.id in uiState.inFlightRequestIds,
                    onAccept = { viewModel.acceptRequest(request.id) },
                    onReject = { viewModel.rejectRequest(request.id) },
                )
            }
        }

        if (uiState.outgoingRequests.isNotEmpty()) {
            item(key = "outgoing_header", contentType = "header") {
                SectionLabel(stringResource(R.string.friends_section_outgoing, uiState.outgoingRequests.size))
            }
            items(uiState.outgoingRequests, key = { FriendsListKeys.outgoing(it.id) }, contentType = { "outgoing" }) { request ->
                OutgoingRequestRow(
                    request = request,
                    inFlight = request.id in uiState.inFlightRequestIds,
                    onCancel = { viewModel.cancelOutgoingRequest(request.id) },
                )
            }
        }

        item(key = "search", contentType = "search") {
            GameTagSearchSection(uiState = uiState, viewModel = viewModel)
        }

        if (uiState.gameTag != null) {
            item(key = "share", contentType = "share") {
                MagicCtaButton(
                    onClick = onShare,
                    text = stringResource(R.string.friends_share_my_link),
                    style = MagicCtaStyle.Outlined,
                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(spacing.lg)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "friends_header", contentType = "header") {
            SectionLabel(
                stringResource(R.string.friends_section_friends, uiState.friends.size),
                modifier = Modifier.padding(top = spacing.sm),
            )
        }

        when {
            uiState.friends.isNotEmpty() ->
                items(uiState.friends, key = { FriendsListKeys.friend(it.id) }, contentType = { "friend" }) { friend ->
                    FriendRow(friend = friend, onClick = { onNavigateToFriendDetail(friend.userId) })
                }
            // Cache-first: an empty cache only reads as "no friends" once the refresh has answered.
            !uiState.hasRefreshed -> item(key = "friends_loading", contentType = "progress") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MagicLoadingSpinner(size = MagicLoadingSize.Small)
                }
            }
            else -> item(key = "friends_empty", contentType = "empty") {
                EmptyState(
                    title = stringResource(R.string.friends_empty),
                    icon = Icons.Default.Group,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun GameTagSearchSection(uiState: FriendsViewModel.UiState, viewModel: FriendsViewModel) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.triggerSearch()
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionLabel(stringResource(R.string.friends_section_search), modifier = Modifier.padding(top = spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = {
                    Text(
                        stringResource(R.string.friends_search_placeholder),
                        color = mc.textSecondary,
                        style = MaterialTheme.magicTypography.bodySmall,
                    )
                },
                prefix = { Text("#", color = mc.textSecondary) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                shape = CardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    focusedBorderColor = mc.primaryAccent,
                    // surfaceVariant is ~1.1:1 on HallowedPrint; textDisabled keeps the outline visible.
                    unfocusedBorderColor = mc.textDisabled,
                    cursorColor = mc.primaryAccent,
                    focusedContainerColor = mc.surface,
                    unfocusedContainerColor = mc.surface,
                ),
            )
            Spacer(Modifier.width(spacing.sm))
            IconButton(onClick = submit, enabled = !uiState.isSearching) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.friends_search_btn_hint),
                    tint = if (uiState.isSearching) mc.textDisabled else mc.primaryAccent,
                )
            }
        }
        if (uiState.isSearching) MagicProgressBar(modifier = Modifier.fillMaxWidth())

        val result = uiState.searchResult
        when {
            result != null -> SearchResultCard(uiState = uiState, friend = result, viewModel = viewModel)
            uiState.searchStatus == GameTagSearchStatus.NOT_FOUND -> SearchHint(stringResource(R.string.friends_no_result))
            uiState.searchStatus == GameTagSearchStatus.INVALID_INPUT -> SearchHint(stringResource(R.string.friends_search_invalid))
            uiState.searchStatus == GameTagSearchStatus.FAILED -> InlineErrorState(
                message = stringResource(R.string.friends_search_failed),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = viewModel::triggerSearch,
            )
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Text(text, style = MaterialTheme.magicTypography.bodySmall, color = MaterialTheme.magicColors.textSecondary)
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier.padding(top = MaterialTheme.spacing.sm),
    )
}

@Composable
private fun PersonRowContent(avatarUrl: String?, nickname: String, gameTag: String, trailing: @Composable () -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(avatarUrl = avatarUrl, initials = avatarInitial(nickname), size = AVATAR_SIZE)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(nickname, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            if (gameTag.isNotBlank()) {
                Text(gameTag, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }
        }
        trailing()
    }
}

@Composable
private fun PendingRequestRow(
    request: FriendRequest,
    inFlight: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        PersonRowContent(request.fromAvatarUrl, request.fromNickname, request.fromGameTag) {
            if (inFlight) {
                MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
            }
            IconButton(onClick = onAccept, enabled = !inFlight) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.friends_accept_request_hint, request.fromNickname),
                    tint = if (inFlight) mc.textDisabled else mc.primaryAccent,
                )
            }
            IconButton(onClick = onReject, enabled = !inFlight) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.friends_decline_request_hint, request.fromNickname),
                    tint = mc.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun OutgoingRequestRow(
    request: OutgoingFriendRequest,
    inFlight: Boolean,
    onCancel: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        PersonRowContent(request.toAvatarUrl, request.toNickname, request.toGameTag) {
            if (inFlight) {
                MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
            }
            IconButton(onClick = onCancel, enabled = !inFlight) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.friends_outgoing_cancel_hint),
                    tint = mc.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    uiState: FriendsViewModel.UiState,
    friend: Friend,
    viewModel: FriendsViewModel,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        PersonRowContent(friend.avatarUrl, friend.nickname, friend.gameTag) {
            when (uiState.searchRelation) {
                SearchResultRelation.NONE -> MagicCtaButton(
                    onClick = viewModel::sendFriendRequest,
                    text = stringResource(R.string.friends_send_invitation),
                    isLoading = uiState.isSendingRequest,
                    enabled = !uiState.isSendingRequest,
                )
                SearchResultRelation.INCOMING_PENDING -> {
                    val incoming = uiState.searchResultIncomingRequest
                    MagicCtaButton(
                        onClick = { incoming?.let { viewModel.acceptRequest(it.id) } },
                        text = stringResource(R.string.friends_result_accept_request),
                        enabled = incoming != null && incoming.id !in uiState.inFlightRequestIds,
                    )
                }
                SearchResultRelation.FRIEND -> RelationLabel(stringResource(R.string.friends_result_already_friends))
                SearchResultRelation.OUTGOING_PENDING -> RelationLabel(stringResource(R.string.friends_result_request_pending))
                SearchResultRelation.SELF -> RelationLabel(stringResource(R.string.friends_result_self))
            }
        }
    }
}

@Composable
private fun RelationLabel(text: String) {
    Text(text, style = MaterialTheme.magicTypography.labelMedium, color = MaterialTheme.magicColors.textSecondary)
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val openLabel = stringResource(R.string.friends_open_friend_action)
    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick),
    ) {
        PersonRowContent(friend.avatarUrl, friend.nickname, friend.gameTag) {}
    }
}

@Composable
private fun friendsMessageText(message: FriendsMessage): String = stringResource(
    when (message) {
        FriendsMessage.REQUEST_SENT -> R.string.friends_invitation_sent
        FriendsMessage.REQUEST_ACCEPTED -> R.string.friends_invitation_accepted
        FriendsMessage.REQUEST_DECLINED -> R.string.friends_invitation_declined
        FriendsMessage.REQUEST_CANCELLED -> R.string.friends_invitation_cancelled
        FriendsMessage.SEND_FAILED -> R.string.friends_error_send
        FriendsMessage.SEND_ALREADY_LINKED -> R.string.friends_error_already_linked
        FriendsMessage.SEND_SELF -> R.string.friends_error_self_request
        FriendsMessage.SEND_NOT_PERMITTED -> R.string.friends_error_not_permitted
        FriendsMessage.ACCEPT_FAILED, FriendsMessage.DECLINE_FAILED -> R.string.friends_error_generic
        FriendsMessage.REQUEST_GONE -> R.string.friends_error_request_gone
        FriendsMessage.CANCEL_FAILED -> R.string.friends_error_cancel_outgoing
    }
)
