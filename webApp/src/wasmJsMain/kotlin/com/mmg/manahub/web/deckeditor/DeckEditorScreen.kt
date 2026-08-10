package com.mmg.manahub.web.deckeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.CardListItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Deck Editor -- the SIXTH real `:webApp` MVP screen (web roadmap W4c). A destination you navigate
 * INTO from [com.mmg.manahub.web.decks.DeckListScreen]'s deck rows, not a bottom-nav tab.
 *
 * DELIBERATELY scoped WAY down from Android's Deck Studio (2320-line screen + 1774-line
 * ViewModel -- see CLAUDE.md's "Deck Studio" section for the full scope this does NOT port):
 *  - Mainboard/sideboard card lists, grouped, with quantities.
 *  - Add a card (compact screen-local search, see [DeckEditorViewModel]'s KDoc for why this
 *    doesn't reuse [com.mmg.manahub.web.search.CardSearchScreen]).
 *  - Remove/decrement a card.
 *  - Rename the deck, change its format (curated 3-format subset, [EDITOR_FORMATS]).
 *
 * Explicitly OUT of scope, not even a stub: Deck Doctor suggestions/analysis, the wizard/seed-build
 * flow, playtest, import/export, warning overlays (legality/color-identity/commander), and
 * land-suggestion auto-fill. This is an MVP boundary, not incomplete work -- do not add any of the
 * above "since it's easy."
 *
 * Responsive per web roadmap plan §1: mainboard and sideboard stack vertically at
 * [ManaWindowSizeClass.COMPACT]; at MEDIUM/EXPANDED/LARGE they render side-by-side in a two-column
 * row (mirrors [com.mmg.manahub.web.carddetail.CardDetailScreen]'s stacked-vs-side-by-side split).
 */
@Composable
fun DeckEditorScreen(deckId: String, windowSizeClass: ManaWindowSizeClass, onBack: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    // Keyed on deckId per the CLAUDE.md koinViewModel-overlay convention -- this call site is a
    // real nav destination (fresh NavBackStackEntry per navigate()), so the key is cheap insurance
    // rather than a strict requirement, matching CardDetailScreen's own precedent.
    val viewModel = koinViewModel<DeckEditorViewModel>(key = deckId) { parametersOf(deckId) }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = uiState.deck?.name ?: "Deck Editor",
                style = typography.titleLarge,
                color = colors.textPrimary,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingState(colors.primaryAccent)
                uiState.deck == null -> ErrorState(colors.lifeNegative)
                else -> DeckEditorContent(
                    uiState = uiState,
                    windowSizeClass = windowSizeClass,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(indicatorColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = indicatorColor)
    }
}

@Composable
private fun ErrorState(errorColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "This deck couldn't be loaded. It may not exist, or you may not have access to it.",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = errorColor,
        )
    }
}

@Composable
private fun DeckEditorContent(
    uiState: DeckEditorUiState,
    windowSizeClass: ManaWindowSizeClass,
    viewModel: DeckEditorViewModel,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        DeckMetadataSection(uiState = uiState, viewModel = viewModel)
        AddCardSection(uiState = uiState, viewModel = viewModel)

        uiState.actionMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.magicTypography.bodySmall,
                color = MaterialTheme.magicColors.textSecondary,
            )
        }

        if (windowSizeClass == ManaWindowSizeClass.COMPACT) {
            BoardSection(
                title = "Mainboard",
                rows = uiState.mainboard,
                isSideboard = false,
                emptyMessage = "No cards yet -- search above to add some.",
                viewModel = viewModel,
            )
            BoardSection(
                title = "Sideboard",
                rows = uiState.sideboard,
                isSideboard = true,
                emptyMessage = "No sideboard cards.",
                viewModel = viewModel,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    BoardSection(
                        title = "Mainboard",
                        rows = uiState.mainboard,
                        isSideboard = false,
                        emptyMessage = "No cards yet -- search above to add some.",
                        viewModel = viewModel,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    BoardSection(
                        title = "Sideboard",
                        rows = uiState.sideboard,
                        isSideboard = true,
                        emptyMessage = "No sideboard cards.",
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckMetadataSection(uiState: DeckEditorUiState, viewModel: DeckEditorViewModel) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val deck = uiState.deck

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = uiState.nameDraft,
                onValueChange = viewModel::onNameDraftChange,
                modifier = Modifier.weight(1f),
                label = { Text("Deck name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.commitRename() }),
            )
            IconButton(
                onClick = viewModel::commitRename,
                enabled = deck != null && uiState.nameDraft.isNotBlank() && uiState.nameDraft != deck.name,
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Save deck name", tint = colors.primaryAccent)
            }
        }

        Text(text = "Format", style = typography.labelLarge, color = colors.textPrimary)
        // W5b regression sweep (2026-08-04): this Row is NOT wrapped in horizontalScroll by
        // accident -- at 320px COMPACT, 3 FilterChips + 2 `spacing.sm` gaps exceed the available
        // ~288.dp (320.dp minus AdaptiveScaffold's spacing.lg gutters). Without a scroll container,
        // Row compresses the last chip's measured width down to whatever remains, and FilterChip's
        // label Text (no maxLines/overflow of its own) responds by wrapping "Draft" one CHARACTER
        // per line ("D"/"r"/"a"/"f"/"t" stacked vertically) -- verified live via screenshot, a
        // genuinely broken render, not a cosmetic nit. horizontalScroll lets every chip keep its
        // natural size; a real device at this width scrolls the row instead of rendering broken
        // text. Fixed-width EDITOR_FORMATS (currently 3) means normal-width viewports never notice
        // the scroll at all.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val selectedFormat = deck?.format?.let { raw -> DeckFormat.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            EDITOR_FORMATS.forEach { format ->
                FilterChip(
                    selected = format == selectedFormat,
                    onClick = { viewModel.changeFormat(format) },
                    label = { Text(format.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primaryAccent.copy(alpha = 0.2f),
                        selectedLabelColor = colors.primaryAccent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AddCardSection(uiState: DeckEditorUiState, viewModel: DeckEditorViewModel) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Add a card", style = typography.labelLarge, color = colors.textPrimary)
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a card to add") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchCards() }),
            trailingIcon = {
                IconButton(onClick = viewModel::searchCards) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }
            },
        )

        if (uiState.isSearching) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }
        }

        uiState.searchError?.let { message ->
            Text(text = "Search failed: $message", style = typography.bodySmall, color = colors.lifeNegative)
        }

        uiState.searchResults.forEach { card ->
            SearchResultRow(card = card, onAdd = { viewModel.addCardToMainboard(card) })
        }
    }
}

@Composable
private fun SearchResultRow(card: Card, onAdd: () -> Unit) {
    CardListItem(
        name = card.name,
        imageUrl = card.imageNormal,
        priceUsd = card.priceUsd,
        priceEur = card.priceEur,
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BoardSection(
    title: String,
    rows: List<DeckEditorCardRow>,
    isSideboard: Boolean,
    emptyMessage: String,
    viewModel: DeckEditorViewModel,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val totalQuantity = rows.sumOf { it.quantity }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "$title ($totalQuantity)",
            style = typography.titleMedium,
            color = colors.textPrimary,
        )
        if (rows.isEmpty()) {
            EmptyState(title = emptyMessage)
        } else {
            rows.forEach { row ->
                DeckCardRow(row = row, isSideboard = isSideboard, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DeckCardRow(row: DeckEditorCardRow, isSideboard: Boolean, viewModel: DeckEditorViewModel) {
    val colors = MaterialTheme.magicColors
    val displayName = row.card?.name ?: row.scryfallId

    // W5b regression sweep (2026-08-04): this Row is horizontally scrollable, and CardListItem
    // gets a fixed min width instead of `weight(1f)`, for the same reason as the format-chip Row
    // fix above -- at 320px COMPACT, CardListItem (56.dp leading image + text) squeezed against 3
    // full 48.dp-touch-target IconButtons left almost no room, and CardListItem's price/quantity
    // Text elements (no maxLines/overflow of their own) responded by wrapping MID-NUMBER onto a
    // second line ("$1.4"/"4", "×"/"1") -- verified live via screenshot, a genuinely broken/glitchy
    // render, not a cosmetic nit. `weight(1f)` cannot be combined with `horizontalScroll` (Compose
    // throws -- a weighted child needs a bounded max width, which a scrollable Row's infinite-width
    // constraint doesn't provide), hence the fixed 220.dp width instead (matches HomeScreen's
    // RecentDeckCard width for consistency). The three IconButtons keep their full 48.dp touch
    // targets unconditionally -- CLAUDE.md's accessibility floor is never traded away for layout
    // fit; a narrow viewport scrolls the row instead.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardListItem(
            name = displayName,
            imageUrl = row.card?.imageNormal,
            priceUsd = row.card?.priceUsd,
            priceEur = row.card?.priceEur,
            quantityText = "×${row.quantity}",
            onClick = {},
            modifier = Modifier.width(220.dp),
        )
        IconButton(onClick = { viewModel.decrementQuantity(row, isSideboard) }) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease $displayName quantity", tint = colors.textSecondary)
        }
        IconButton(onClick = { viewModel.incrementQuantity(row, isSideboard) }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase $displayName quantity", tint = colors.textSecondary)
        }
        IconButton(onClick = { viewModel.removeCard(row, isSideboard) }) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove $displayName", tint = colors.lifeNegative)
        }
    }
}
