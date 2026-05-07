package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.AddCardSheet
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTradeProposalScreen(
    onBack: () -> Unit,
    onNavigateToThread: (proposalId: String, rootProposalId: String) -> Unit,
    viewModel: TradeProposalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mc = MaterialTheme.magicColors
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.onSnackbarDismissed()
    }

    LaunchedEffect(uiState.errorMessage) {
        val err = uiState.errorMessage ?: return@LaunchedEffect
        val message = when (err) {
            "INITIAL_ASYMMETRY"       -> "Both sides must have at least one item"
            "PROPOSAL_VERSION_MISMATCH" -> "Proposal was modified; please refresh"
            else                      -> err ?: "Unknown error"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    LaunchedEffect(uiState.navigateToThread) {
        val nav = uiState.navigateToThread ?: return@LaunchedEffect
        onNavigateToThread(nav.first, nav.second)
        viewModel.onNavigationConsumed()
    }

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            onBack()
            viewModel.onNavigationConsumed()
        }
    }

    var showAddItemSheet by remember { mutableStateOf<ItemSide?>(null) }
    var editingItem by remember { mutableStateOf<TradeItemDraft?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = mc.background,
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary
                        )
                    }
                    Text(
                        text = stringResource(
                            if (uiState.isCounterMode) R.string.trades_counter_proposal_title
                            else R.string.trades_create_proposal_title
                        ),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick  = viewModel::onSendProposal,
                    enabled  = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = mc.background, strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.trades_send_proposal),
                            color = mc.background,
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    }
                }
                if (!uiState.isCounterMode) {
                    TextButton(
                        onClick  = viewModel::onSaveDraft,
                        enabled  = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.trades_save_draft),
                            color = mc.textSecondary,
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── "You offer" section ───────────────────────────────────────────
            item(key = "you_offer_header") {
                SectionHeader(title = stringResource(R.string.trades_you_offer_section))
            }

            items(uiState.proposerItems, key = { "pi_${it.id}" }) { item ->
                TradeItemDraftRow(
                    item     = item,
                    onClick  = { 
                        editingItem = item
                        showEditSheet = true
                    },
                    onRemove = { viewModel.removeProposerItem(item.id) },
                )
            }

            item(key = "you_offer_review") {
                ReviewCollectionToggle(
                    label   = stringResource(R.string.trades_review_collection_proposer),
                    checked = uiState.includesReviewFromProposer,
                    onToggle = viewModel::toggleReviewCollectionProposer,
                )
            }

            item(key = "you_offer_add") {
                AddItemButton(onClick = { showAddItemSheet = ItemSide.PROPOSER })
            }

            item(key = "divider") { Spacer(Modifier.height(4.dp)) }

            // ── "They offer" section ──────────────────────────────────────────
            item(key = "they_offer_header") {
                SectionHeader(title = stringResource(R.string.trades_they_offer_section))
            }

            items(uiState.receiverItems, key = { "ri_${it.id}" }) { item ->
                TradeItemDraftRow(
                    item     = item,
                    onClick  = {
                        editingItem = item
                        showEditSheet = true
                    },
                    onRemove = { viewModel.removeReceiverItem(item.id) },
                )
            }

            item(key = "they_offer_review") {
                ReviewCollectionToggle(
                    label   = stringResource(R.string.trades_review_collection_receiver),
                    checked = uiState.includesReviewFromReceiver,
                    onToggle = viewModel::toggleReviewCollectionReceiver,
                )
            }

            item(key = "they_offer_add") {
                AddItemButton(onClick = { showAddItemSheet = ItemSide.RECEIVER })
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    showAddItemSheet?.let { side ->
        CardSearchSheet(
            query = uiState.addCardsQuery,
            addCardsResults = uiState.addCardsResults,
            scryfallResults = uiState.scryfallResults,
            isSearchingCards = uiState.isSearchingCards,
            isSearchingScryfall = uiState.isSearchingScryfall,
            onQueryChange = viewModel::onAddCardsQueryChange,
            onScryfallSearch = viewModel::searchScryfallDirect,
            onAdd = { id ->
                focusManager.clearFocus()
                val card = viewModel.getCardById(id)
                if (card != null) {
                    val draft = TradeItemDraft(
                        cardId = card.scryfallId,
                        cardName = card.name,
                        quantity = 1,
                        condition = "NM",
                        language = "en"
                    )
                    when (side) {
                        ItemSide.PROPOSER -> viewModel.addProposerItem(draft)
                        ItemSide.RECEIVER -> viewModel.addReceiverItem(draft)
                    }
                    showAddItemSheet = null
                    viewModel.clearAddCardsState()
                }
            },
            onRemove = { /* No-op here */ },
            onCardClick = { 
                focusManager.clearFocus()
                /* Maybe show detail later */ 
            },
            onDismiss = {
                focusManager.clearFocus()
                showAddItemSheet = null
                viewModel.clearAddCardsState()
            }
        )
    }

    if (showEditSheet) {
        editingItem?.let { item ->
            AddCardSheet(
                cardName = item.cardName,
                onConfirm = { isFoil, isAltArt, condition, language, qty ->
                    val updated = item.copy(
                        quantity = qty,
                        isFoil = isFoil,
                        condition = condition,
                        language = language,
                        isAltArt = isAltArt
                    )
                    if (uiState.proposerItems.any { it.id == item.id }) {
                        viewModel.updateProposerItem(updated)
                    } else {
                        viewModel.updateReceiverItem(updated)
                    }
                    showEditSheet = false
                    editingItem = null
                },
                onDismiss = {
                    showEditSheet = false
                    editingItem = null
                },
                manaCost = null,
                cardImage = null,
                confirmButtonText = stringResource(R.string.scanner_edit_save)
            )
        }
    }
}

private enum class ItemSide { PROPOSER, RECEIVER }

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
    )
}

@Composable
private fun TradeItemDraftRow(
    item:     TradeItemDraft,
    onClick:  () -> Unit,
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = item.cardName,
                    style    = ty.bodyMedium,
                    color    = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badges = buildList {
                    add("x${item.quantity}")
                    if (item.isFoil) add("Foil")
                    if (item.condition.isNotBlank()) add(item.condition)
                    if (item.language.isNotBlank()) add(item.language)
                    if (item.isAltArt) add("Alt art")
                }
                Text(
                    text  = badges.joinToString(" · "),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint               = mc.textDisabled,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewCollectionToggle(
    label:    String,
    checked:  Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = CheckboxDefaults.colors(
                checkedColor   = mc.primaryAccent,
                uncheckedColor = mc.textSecondary,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = label,
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textPrimary,
        )
    }
}

@Composable
private fun AddItemButton(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.trades_add_item),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.primaryAccent,
        )
    }
}
