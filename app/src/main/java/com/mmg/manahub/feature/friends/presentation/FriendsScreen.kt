package com.mmg.manahub.feature.friends.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.mmg.manahub.feature.friends.domain.model.FriendRequest
import com.mmg.manahub.feature.friends.domain.model.OutgoingFriendRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFriendDetail: (userId: String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()

    val errorMsg = stringResource(R.string.friends_error_generic)
    val sendErrorMsg = stringResource(R.string.friends_error_send)
    val sentMsg = stringResource(R.string.friends_invitation_sent)
    val acceptedMsg = stringResource(R.string.friends_invitation_accepted)
    val rejectedMsg = stringResource(R.string.friends_invitation_declined)
    val cancelledMsg = stringResource(R.string.friends_invitation_cancelled)
    val cancelErrorMsg = stringResource(R.string.friends_error_cancel_outgoing)

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage ?: return@LaunchedEffect
        toastState.show(msg, uiState.toastType)
        viewModel.clearToast()
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
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
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.friends_login_required),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                return@Scaffold
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Incoming pending requests section
                if (uiState.pendingRequests.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.friends_section_pending, uiState.pendingRequests.size),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(uiState.pendingRequests, key = { it.id }) { request ->
                        PendingRequestRow(
                            request = request,
                            onAccept = { viewModel.acceptRequest(request.id, errorMsg, acceptedMsg) },
                            onReject = { viewModel.rejectRequest(request.id, errorMsg, rejectedMsg) },
                        )
                    }
                }

                // Outgoing pending invitations section
                if (uiState.outgoingRequests.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.friends_section_outgoing, uiState.outgoingRequests.size),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(uiState.outgoingRequests, key = { it.id }) { request ->
                        OutgoingRequestRow(
                            request = request,
                            onCancel = {
                                viewModel.cancelOutgoingRequest(request.id, cancelErrorMsg, cancelledMsg)
                            },
                        )
                    }
                }

                // Search section
                item {
                    SectionLabel(
                        stringResource(R.string.friends_section_search),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "#",
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = {
                                Text(
                                    stringResource(R.string.friends_search_placeholder),
                                    color = mc.textDisabled,
                                    style = MaterialTheme.magicTypography.bodySmall,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mc.textPrimary,
                                unfocusedTextColor = mc.textPrimary,
                                focusedBorderColor = mc.primaryAccent,
                                unfocusedBorderColor = mc.surfaceVariant,
                                cursorColor = mc.primaryAccent,
                                focusedContainerColor = mc.surface,
                                unfocusedContainerColor = mc.surface,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::triggerSearch,
                            enabled = !uiState.isSearching,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.friends_search_btn_hint),
                                tint = if (uiState.isSearching) mc.textDisabled else mc.primaryAccent,
                            )
                        }
                    }
                    if (uiState.isSearching) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = mc.primaryAccent,
                            trackColor = mc.surfaceVariant,
                        )
                    }
                    uiState.searchResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        SearchResultCard(
                            friend = result,
                            onSendInvitation = {
                                viewModel.sendFriendRequest(result.userId, sendErrorMsg, sentMsg)
                            },
                        )
                    } ?: run {
                        if (uiState.searchPerformed && uiState.searchQuery.isNotBlank() && !uiState.isSearching) {
                            Text(
                                stringResource(R.string.friends_no_result),
                                style = MaterialTheme.magicTypography.bodySmall,
                                color = mc.textDisabled,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                // Share invite link section — only visible when the URL is ready
                if (uiState.shareUrl != null) {
                    item {
                        val context = LocalContext.current
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.onShareMyLinkClicked(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, mc.primaryAccent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.friends_share_my_link),
                                style = MaterialTheme.magicTypography.labelLarge,
                            )
                        }
                    }
                }

                // Friends list section
                item {
                    SectionLabel(
                        stringResource(R.string.friends_section_friends, uiState.friends.size),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }

                if (uiState.friends.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.friends_empty),
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textDisabled,
                        )
                    }
                } else {
                    items(uiState.friends, key = { it.id }) { friend ->
                        FriendRow(friend = friend, onClick = { onNavigateToFriendDetail(friend.userId) })
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun PendingRequestRow(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                avatarUrl = request.fromAvatarUrl,
                initials = request.fromNickname.take(1).uppercase(),
                size = 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.fromNickname,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    request.fromGameTag,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent)
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled)
            }
        }
    }
}

@Composable
private fun OutgoingRequestRow(
    request: OutgoingFriendRequest,
    onCancel: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                avatarUrl = request.toAvatarUrl,
                initials = request.toNickname.take(1).uppercase(),
                size = 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.toNickname,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
                Text(
                    request.toGameTag,
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.friends_outgoing_cancel_hint),
                    tint = mc.textDisabled,
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(friend: Friend, onSendInvitation: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                avatarUrl = friend.avatarUrl,
                initials = friend.nickname.take(1).uppercase(),
                size = 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.nickname, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
                Text(friend.gameTag, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }
            Button(
                onClick = onSendInvitation,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.friends_send_invitation),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                avatarUrl = friend.avatarUrl,
                initials = friend.nickname.take(1).uppercase(),
                size = 40,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.nickname, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
                Text(friend.gameTag, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }
        }
    }
}

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
                modifier = Modifier.fillMaxSize().clip(CircleShape),
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
