package com.mmg.manahub.feature.trades.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
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

    var showAddItemDialog by remember { mutableStateOf<ItemSide?>(null) }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = stringResource(
                            if (uiState.isCounterMode) R.string.trades_counter_proposal_title
                            else R.string.trades_create_proposal_title
                        ),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
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
                AddItemButton(onClick = { showAddItemDialog = ItemSide.PROPOSER })
            }

            item(key = "divider") { Spacer(Modifier.height(4.dp)) }

            // ── "They offer" section ──────────────────────────────────────────
            item(key = "they_offer_header") {
                SectionHeader(title = stringResource(R.string.trades_they_offer_section))
            }

            items(uiState.receiverItems, key = { "ri_${it.id}" }) { item ->
                TradeItemDraftRow(
                    item     = item,
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
                AddItemButton(onClick = { showAddItemDialog = ItemSide.RECEIVER })
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    showAddItemDialog?.let { side ->
        AddTradeItemDialog(
            onConfirm = { cardId, quantity, isFoil, condition, language, isAltArt ->
                val draft = TradeItemDraft(
                    cardId    = cardId,
                    quantity  = quantity,
                    isFoil    = isFoil,
                    condition = condition,
                    language  = language,
                    isAltArt  = isAltArt,
                )
                when (side) {
                    ItemSide.PROPOSER -> viewModel.addProposerItem(draft)
                    ItemSide.RECEIVER -> viewModel.addReceiverItem(draft)
                }
                showAddItemDialog = null
            },
            onDismiss = { showAddItemDialog = null },
        )
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
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = item.cardId,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
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
                    style = MaterialTheme.magicTypography.labelSmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTradeItemDialog(
    onConfirm: (cardId: String, quantity: Int, isFoil: Boolean, condition: String, language: String, isAltArt: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var cardId    by remember { mutableStateOf("") }
    var quantity  by remember { mutableStateOf("1") }
    var isFoil    by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language  by remember { mutableStateOf("en") }
    var isAltArt  by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = mc.primaryAccent,
        unfocusedBorderColor = mc.surfaceVariant,
        focusedTextColor     = mc.textPrimary,
        unfocusedTextColor   = mc.textPrimary,
        cursorColor          = mc.primaryAccent,
        focusedLabelColor    = mc.primaryAccent,
        unfocusedLabelColor  = mc.textSecondary,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
        title = {
            Text(
                stringResource(R.string.trades_add_item),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = cardId,
                    onValueChange = { cardId = it },
                    label         = { Text(stringResource(R.string.trades_select_card_id_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = textFieldColors,
                )
                OutlinedTextField(
                    value         = quantity,
                    onValueChange = { if (it.all(Char::isDigit)) quantity = it },
                    label         = { Text(stringResource(R.string.trades_quantity)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = textFieldColors,
                )
                OutlinedTextField(
                    value         = condition,
                    onValueChange = { condition = it },
                    label         = { Text(stringResource(R.string.trades_condition)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = textFieldColors,
                )
                OutlinedTextField(
                    value         = language,
                    onValueChange = { language = it },
                    label         = { Text(stringResource(R.string.trades_language)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = textFieldColors,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.clickable { isFoil = !isFoil },
                ) {
                    Checkbox(
                        checked         = isFoil,
                        onCheckedChange = { isFoil = it },
                        colors          = CheckboxDefaults.colors(
                            checkedColor   = mc.primaryAccent,
                            uncheckedColor = mc.textSecondary,
                        ),
                    )
                    Text(stringResource(R.string.trades_foil), style = MaterialTheme.magicTypography.bodySmall, color = mc.textPrimary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.clickable { isAltArt = !isAltArt },
                ) {
                    Checkbox(
                        checked         = isAltArt,
                        onCheckedChange = { isAltArt = it },
                        colors          = CheckboxDefaults.colors(
                            checkedColor   = mc.primaryAccent,
                            uncheckedColor = mc.textSecondary,
                        ),
                    )
                    Text(stringResource(R.string.trades_alt_art), style = MaterialTheme.magicTypography.bodySmall, color = mc.textPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (cardId.isNotBlank()) {
                        onConfirm(
                            cardId,
                            quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            isFoil,
                            condition,
                            language,
                            isAltArt,
                        )
                    }
                },
                enabled = cardId.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_confirm), color = mc.primaryAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
            }
        },
    )
}
