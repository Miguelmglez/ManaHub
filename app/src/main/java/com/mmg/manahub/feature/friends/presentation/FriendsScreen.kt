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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.friends.domain.model.Friend
import com.mmg.manahub.feature.friends.domain.model.FriendRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    var selectedFriend by remember { mutableStateOf<Friend?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val errorMsg = stringResource(R.string.friends_error_generic)
    val sendErrorMsg = stringResource(R.string.friends_error_send)

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage ?: return@LaunchedEffect
        toastState.show(msg, MagicToastType.ERROR)
        viewModel.clearToast()
    }

    selectedFriend?.let { friend ->
        FriendDetailSheet(
            friend = friend,
            onDismiss = { selectedFriend = null },
            onRemoveFriend = { showRemoveConfirm = true },
        )
    }

    if (showRemoveConfirm) {
        val friendName = selectedFriend?.nickname ?: ""
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text(
                    stringResource(R.string.friends_remove_confirm_title),
                    color = mc.textPrimary,
                    style = MaterialTheme.magicTypography.titleMedium,
                )
            },
            text = {
                Text(
                    stringResource(R.string.friends_remove_confirm_body, friendName),
                    color = mc.textSecondary,
                    style = MaterialTheme.magicTypography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedFriend?.let { f ->
                        viewModel.removeFriend(f.id, errorMsg)
                    }
                    showRemoveConfirm = false
                    selectedFriend = null
                }) {
                    Text(
                        stringResource(R.string.friends_remove_confirm_ok),
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
                // Pending requests section
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
                            onAccept = { viewModel.acceptRequest(request.id, errorMsg) },
                            onReject = { viewModel.rejectRequest(request.id, errorMsg) },
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
                            onSendInvitation = { viewModel.sendFriendRequest(result.userId, sendErrorMsg) },
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
                        FriendRow(friend = friend, onClick = { selectedFriend = friend })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendDetailSheet(
    friend: Friend,
    onDismiss: () -> Unit,
    onRemoveFriend: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Spacer(Modifier.height(8.dp)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarImage(
                avatarUrl = friend.avatarUrl,
                initials = friend.nickname.take(1).uppercase(),
                size = 72,
            )
            Text(
                friend.nickname,
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
            Text(
                friend.gameTag,
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRemoveFriend,
                colors = ButtonDefaults.buttonColors(containerColor = mc.lifeNegative),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.friends_remove_friend), color = Color.White)
            }
            Spacer(Modifier.navigationBarsPadding())
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
