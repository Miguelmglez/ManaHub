package com.mmg.manahub.web.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendRequest
import com.mmg.manahub.core.model.OutgoingFriendRequest
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Friends -- web scope expansion, Friends slice (approved 2026-08-04, second wave after Settings/
 * Profile/Add Card). A destination reachable from [com.mmg.manahub.web.auth.AuthScreen]'s "Friends"
 * row, same "hangs off the account surface, not a top-level tab" precedent as
 * [com.mmg.manahub.web.settings.SettingsScreen]/[com.mmg.manahub.web.profile.ProfileScreen] -- the
 * nav rail/bottom bar is already at 6 top-level tabs; a 9th account-adjacent surface would crowd
 * [com.mmg.manahub.core.ui.layout.ManaWindowSizeClass.COMPACT]'s bottom bar further.
 *
 * Core flow only, per the task brief's scope guidance:
 * - Friends list ([FriendsUiState.Content.friends]) with a "Remove" action per row.
 * - Pending (incoming) requests with Accept/Reject.
 * - Outgoing (sent) requests with Cancel.
 * - Add-friend via exact game-tag search -> "Send request".
 *
 * Deliberately DEFERRED (documented, not half-built): a friend-detail view surfacing
 * [com.mmg.manahub.core.domain.repository.FriendRepository.getFriendCollection]/`getFriendStats`/
 * `getFriendMatchHistory` (a genuinely separate, bigger screen -- viewing another user's
 * server-RLS-gated data), and the referral-invite flow (`acceptInvite`/`getMyShareUrl`) -- the
 * latter's most natural home is [com.mmg.manahub.web.profile.ProfileScreen] (which already
 * surfaces the user's own `gameTag`), not a new control on this screen; not built this slice
 * either since neither Profile nor Friends needed it to ship the core loop.
 *
 * A single [LazyColumn] mixing `item {}` section headers with `items()` per list -- never nested
 * `LazyColumn`s (CLAUDE.md) -- since friend/request lists are typically small (a handful of rows),
 * this stays performant while giving every row a stable `key` (friendship id).
 */
@Composable
fun FriendsScreen() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<FriendsViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is FriendsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primaryAccent)
        }

        is FriendsUiState.SignedOut -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = "Friends", style = typography.titleLarge, color = colors.textPrimary)
            Text(
                text = "Sign in (guest sign-in counts) via the Account tab to add and manage friends.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        is FriendsUiState.Content -> FriendsContent(state = state, viewModel = viewModel)
    }
}

@Composable
private fun FriendsContent(state: FriendsUiState.Content, viewModel: FriendsViewModel) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val gameTagInput by viewModel.gameTagInput.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val searchMessage by viewModel.searchMessage.collectAsState()
    val isSendingRequest by viewModel.isSendingRequest.collectAsState()

    val isEmpty = state.friends.isEmpty() && state.pendingRequests.isEmpty() && state.outgoingRequests.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(text = "Friends", style = typography.titleLarge, color = colors.textPrimary)

                if (state.error != null) {
                    Text(text = state.error, style = typography.bodyMedium, color = colors.lifeNegative)
                }

                AddFriendSection(
                    gameTagInput = gameTagInput,
                    isSearching = isSearching,
                    searchResult = searchResult,
                    searchMessage = searchMessage,
                    isSendingRequest = isSendingRequest,
                    onInputChanged = viewModel::onGameTagInputChanged,
                    onSearch = viewModel::searchByGameTag,
                    onSendRequest = viewModel::sendFriendRequest,
                )

                HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
            }
        }

        if (state.pendingRequests.isNotEmpty()) {
            item { SectionHeader(title = "Pending requests (${state.pendingRequests.size})") }
            items(state.pendingRequests, key = { "pending-${it.id}" }) { request ->
                PendingRequestRow(
                    request = request,
                    isBusy = request.id in state.actionInFlightIds,
                    onAccept = { viewModel.acceptRequest(request.id) },
                    onReject = { viewModel.rejectRequest(request.id) },
                )
            }
        }

        if (state.outgoingRequests.isNotEmpty()) {
            item { SectionHeader(title = "Sent requests (${state.outgoingRequests.size})") }
            items(state.outgoingRequests, key = { "outgoing-${it.id}" }) { request ->
                OutgoingRequestRow(
                    request = request,
                    isBusy = request.id in state.actionInFlightIds,
                    onCancel = { viewModel.cancelOutgoingRequest(request.id) },
                )
            }
        }

        if (isEmpty) {
            item {
                EmptyState(
                    title = "No friends yet",
                    subtitle = "Search for a friend's game tag above to send your first request.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                )
            }
        } else {
            item { SectionHeader(title = "Friends (${state.friends.size})") }
            items(state.friends, key = { "friend-${it.id}" }) { friend ->
                FriendRow(
                    friend = friend,
                    isBusy = friend.id in state.actionInFlightIds,
                    onRemove = { viewModel.removeFriend(friend.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    Text(
        text = title,
        style = typography.titleMedium,
        color = colors.textPrimary,
        modifier = Modifier.padding(top = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.xs),
    )
}

@Composable
private fun AddFriendSection(
    gameTagInput: String,
    isSearching: Boolean,
    searchResult: Friend?,
    searchMessage: String?,
    isSendingRequest: Boolean,
    onInputChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSendRequest: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Add a friend", style = typography.titleMedium, color = colors.textPrimary)
        // NOTE: OutlinedTextField's `label` slot renders as a single-letter-per-line vertical
        // stack on this CMP/wasmJs version (found live during Friends slice verification --
        // nobody else in :webApp uses `label`; Profile's NicknameEditor uses this exact
        // caption-above-plain-field pattern instead, which IS proven working on web). Follow
        // that precedent for any future OutlinedTextField on web -- do not reach for `label`.
        Text(text = "Game tag", style = typography.bodySmall, color = colors.textSecondary)
        // MagicCtaButton has an internal `fillMaxWidth()` on its text content (CenteredButtonContent)
        // that hogs the WHOLE row width when placed unweighted next to a weight(1f) sibling (same
        // bug documented in project_w4d_home_screen.md) -- both children need an explicit weight so
        // Compose bounds the button's share BEFORE its internal fillMaxWidth() applies. 2f/1f keeps
        // the field dominant while still giving Search enough room at COMPACT widths.
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = gameTagInput,
                onValueChange = onInputChanged,
                singleLine = true,
                enabled = !isSearching,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.primaryAccent,
                ),
                modifier = Modifier.weight(2f),
            )
            MagicCtaButton(
                onClick = onSearch,
                text = "Search",
                enabled = !isSearching && gameTagInput.isNotBlank(),
                isLoading = isSearching,
                style = MagicCtaStyle.Outlined,
                modifier = Modifier.weight(1f),
            )
        }

        if (searchMessage != null) {
            Text(
                text = searchMessage,
                style = typography.bodySmall,
                color = if (searchResult == null) colors.lifeNegative else colors.textSecondary,
            )
        }

        if (searchResult != null) {
            // Vertical stack (identity, then a full-width action button) -- NEVER a horizontal
            // Row pairing raw identity content with an unweighted MagicCtaButton (see the
            // fillMaxWidth() note above; a SpaceBetween Row here reproduces the exact same bug
            // since MagicCtaButton would still request the Row's full incoming width even
            // alongside a non-weighted sibling). Matches PendingRequestRow's proven layout.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundSecondary)
                    .padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FriendIdentity(
                    avatarUrl = searchResult.avatarUrl,
                    nickname = searchResult.nickname,
                    gameTag = searchResult.gameTag,
                )
                MagicCtaButton(
                    onClick = onSendRequest,
                    text = "Send request",
                    enabled = !isSendingRequest,
                    isLoading = isSendingRequest,
                    color = MagicCtaColor.Primary,
                )
            }
        }
    }
}

@Composable
private fun PendingRequestRow(request: FriendRequest, isBusy: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        FriendIdentity(
            avatarUrl = request.fromAvatarUrl,
            nickname = request.fromNickname,
            gameTag = request.fromGameTag,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MagicCtaButton(
                onClick = onAccept,
                text = "Accept",
                enabled = !isBusy,
                isLoading = isBusy,
                color = MagicCtaColor.Success,
                modifier = Modifier.weight(1f),
            )
            MagicCtaButton(
                onClick = onReject,
                text = "Reject",
                enabled = !isBusy,
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OutgoingRequestRow(request: OutgoingFriendRequest, isBusy: Boolean, onCancel: () -> Unit) {
    val spacing = MaterialTheme.spacing
    // Vertical stack, not a horizontal SpaceBetween Row -- see AddFriendSection's fillMaxWidth() note.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        FriendIdentity(avatarUrl = request.toAvatarUrl, nickname = request.toNickname, gameTag = request.toGameTag)
        MagicCtaButton(
            onClick = onCancel,
            text = "Cancel",
            enabled = !isBusy,
            isLoading = isBusy,
            style = MagicCtaStyle.Outlined,
            color = MagicCtaColor.Neutral,
        )
    }
}

@Composable
private fun FriendRow(friend: Friend, isBusy: Boolean, onRemove: () -> Unit) {
    val spacing = MaterialTheme.spacing
    // Vertical stack, not a horizontal SpaceBetween Row -- see AddFriendSection's fillMaxWidth() note.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        FriendIdentity(avatarUrl = friend.avatarUrl, nickname = friend.nickname, gameTag = friend.gameTag)
        MagicCtaButton(
            onClick = onRemove,
            text = "Remove",
            enabled = !isBusy,
            isLoading = isBusy,
            style = MagicCtaStyle.Outlined,
            color = MagicCtaColor.Error,
        )
    }
}

@Composable
private fun FriendIdentity(avatarUrl: String?, nickname: String, gameTag: String) {
    val spacing = MaterialTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        FriendAvatar(avatarUrl = avatarUrl)
        Column {
            val colors = MaterialTheme.magicColors
            val typography = MaterialTheme.magicTypography
            Text(text = nickname, style = typography.titleMedium, color = colors.textPrimary)
            if (gameTag.isNotBlank()) {
                // gameTag already carries its own "#" prefix as stored (found live during
                // verification -- a stored value of "#20FERA" rendered as "##20FERA" before this
                // fix). Android's FriendsScreen.kt renders `friend.gameTag` verbatim with no
                // prepended "#" for the same reason -- match that convention here.
                Text(text = gameTag, style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FriendAvatar(avatarUrl: String?, size: Dp = 40.dp) {
    val colors = MaterialTheme.magicColors
    val modifier = Modifier.size(size).clip(CircleShape).background(colors.surfaceVariant)
    if (avatarUrl.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
