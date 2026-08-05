package com.mmg.manahub.web.trades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.FriendCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.web.friends.FriendAvatar
import com.mmg.manahub.web.friends.FriendIdentity
import org.koin.compose.viewmodel.koinViewModel

/**
 * New proposal -- the single heaviest piece deferred from the original Trades slice
 * (`project_trades_hub_negotiation.md`). Two-step flow driven by
 * [CreateProposalUiState.step]: pick a friend ([ProposalStep.SELECT_FRIEND]), then build both
 * sides of the trade ([ProposalStep.BUILD_ITEMS]) from the caller's own collection and the
 * friend's collection.
 *
 * Item pickers are [MagicAlertDialog]s (a search field + bounded-height [LazyColumn]), not a
 * `ModalBottomSheet` -- the same choice [com.mmg.manahub.web.carddetail.CardDetailScreen]'s
 * `CardVersionPickerDialog` documents (`ModalBottomSheet` is unverified on wasmJs).
 */
@Composable
fun CreateProposalScreen(onBack: () -> Unit, onProposalCreated: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<CreateProposalViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateProposalEvent.NavigateToThread -> onProposalCreated(event.proposalId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            IconButton(onClick = { if (uiState.step == ProposalStep.BUILD_ITEMS) viewModel.onBackToFriendSelect() else onBack() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(text = "New proposal", style = typography.titleLarge, color = colors.textPrimary)
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error.orEmpty(),
                style = typography.bodyMedium,
                color = colors.lifeNegative,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            )
        }

        when (uiState.step) {
            ProposalStep.SELECT_FRIEND -> FriendPicker(
                isLoading = uiState.isLoadingFriends,
                friends = uiState.friends,
                onFriendSelected = viewModel::onFriendSelected,
            )
            ProposalStep.BUILD_ITEMS -> BuildItemsContent(uiState = uiState, viewModel = viewModel)
        }
    }
}

@Composable
private fun FriendPicker(isLoading: Boolean, friends: List<Friend>, onFriendSelected: (Friend) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primaryAccent)
        }
        friends.isEmpty() -> EmptyState(
            title = "No friends yet",
            subtitle = "Add a friend from the Friends tab before proposing a trade.",
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(text = "Who do you want to trade with?", style = typography.titleMedium, color = colors.textPrimary)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                items(friends, key = { it.id }) { friend ->
                    FriendIdentity(
                        avatarUrl = friend.avatarUrl,
                        nickname = friend.nickname,
                        gameTag = friend.gameTag,
                        modifier = Modifier.fillMaxWidth().clickable { onFriendSelected(friend) }.padding(vertical = spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildItemsContent(uiState: CreateProposalUiState, viewModel: CreateProposalViewModel) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val friend = uiState.selectedFriend

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (friend != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FriendAvatar(avatarUrl = friend.avatarUrl, size = 32.dp)
                Text(text = "Trading with ${friend.nickname}", style = typography.titleMedium, color = colors.textPrimary)
            }
        }

        ItemSideSection(
            title = "You give",
            items = uiState.giveItems,
            onAddClick = viewModel::openGiveSheet,
            onRemove = viewModel::removeGiveItem,
        )

        ItemSideSection(
            title = "You receive",
            items = uiState.receiveItems,
            onAddClick = viewModel::openReceiveSheet,
            onRemove = viewModel::removeReceiveItem,
        )

        MagicCtaButton(
            onClick = viewModel::onSubmit,
            text = "Send proposal",
            style = MagicCtaStyle.Filled,
            color = MagicCtaColor.Primary,
            isLoading = uiState.isSubmitting,
            enabled = !uiState.isSubmitting && uiState.giveItems.isNotEmpty() && uiState.receiveItems.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (uiState.isGiveSheetOpen) {
        CollectionPickerDialog(
            title = "Add from your collection",
            query = uiState.giveQuery,
            onQueryChange = viewModel::onGiveQueryChanged,
            isLoading = false,
            cards = uiState.myCollection.filter { uiState.giveQuery.isBlank() || it.card.name.contains(uiState.giveQuery, ignoreCase = true) },
            rowContent = { userCard -> MyCollectionRow(userCard = userCard, onAdd = { viewModel.addGiveItem(userCard) }) },
            key = { it.userCard.id },
            onDismiss = viewModel::closeGiveSheet,
        )
    }

    if (uiState.isReceiveSheetOpen) {
        CollectionPickerDialog(
            title = "Add from ${friend?.nickname ?: "their"} collection",
            query = uiState.receiveQuery,
            onQueryChange = viewModel::onReceiveQueryChanged,
            isLoading = uiState.isLoadingFriendCollection,
            cards = uiState.friendCollection,
            rowContent = { friendCard -> FriendCollectionRow(friendCard = friendCard, onAdd = { viewModel.addReceiveItem(friendCard) }) },
            key = { "${it.scryfallId}_${it.isFoil}_${it.condition}_${it.language}" },
            onDismiss = viewModel::closeReceiveSheet,
        )
    }
}

@Composable
internal fun ItemSideSection(
    title: String,
    items: List<TradeItemDraft>,
    onAddClick: () -> Unit,
    onRemove: (TradeItemDraft) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$title (${items.size})", style = typography.titleMedium, color = colors.textPrimary)
        }
        MagicCtaButton(
            onClick = onAddClick,
            text = "Add card",
            style = MagicCtaStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
        if (items.isEmpty()) {
            Text(text = "No cards added yet.", style = typography.bodySmall, color = colors.textDisabled)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items.forEach { draft -> DraftItemRow(draft = draft, onRemove = { onRemove(draft) }) }
            }
        }
    }
}

@Composable
internal fun DraftItemRow(draft: TradeItemDraft, onRemove: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Row(
        modifier = Modifier.fillMaxWidth().background(colors.backgroundSecondary, RoundedCornerShape(spacing.xs)).padding(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        AsyncImage(
            model = draft.imageUrl,
            contentDescription = draft.cardName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 36.dp, height = 50.dp).clip(RoundedCornerShape(4.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.cardName,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "×${draft.quantity}${if (draft.isFoil) " · Foil" else ""} · ${draft.condition} · ${draft.language}",
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = colors.textSecondary)
        }
    }
}

@Composable
internal fun <T> CollectionPickerDialog(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean,
    cards: List<T>,
    rowContent: @Composable (T) -> Unit,
    key: (T) -> Any,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissLabel = "Done",
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(text = "Search by name", style = typography.bodySmall, color = colors.textSecondary)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primaryAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }
                    cards.isEmpty() -> Text(
                        text = "No cards found.",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = spacing.md),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(cards, key = key) { item -> rowContent(item) }
                    }
                }
            }
        },
    )
}

@Composable
internal fun MyCollectionRow(userCard: UserCardWithCard, onAdd: () -> Unit) {
    PickerRow(
        name = userCard.card.name,
        imageUrl = userCard.card.imageArtCrop ?: userCard.card.imageNormal,
        detail = "×${userCard.userCard.quantity}${if (userCard.userCard.isFoil) " · Foil" else ""} · ${userCard.userCard.condition}",
        onAdd = onAdd,
    )
}

@Composable
internal fun FriendCollectionRow(friendCard: FriendCard, onAdd: () -> Unit) {
    PickerRow(
        name = friendCard.name,
        imageUrl = friendCard.imageArtCrop ?: friendCard.imageNormal,
        detail = "×${friendCard.quantity}${if (friendCard.isFoil) " · Foil" else ""}",
        onAdd = onAdd,
    )
}

@Composable
internal fun PickerRow(name: String, imageUrl: String?, detail: String, onAdd: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 36.dp, height = 50.dp).clip(RoundedCornerShape(4.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = detail, style = typography.bodySmall, color = colors.textSecondary)
        }
        MagicCtaButton(onClick = onAdd, text = "Add", style = MagicCtaStyle.Outlined)
    }
}
