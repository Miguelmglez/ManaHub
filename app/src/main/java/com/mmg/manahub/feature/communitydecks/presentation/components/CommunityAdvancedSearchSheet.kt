package com.mmg.manahub.feature.communitydecks.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardPickerField
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.search.SearchSection
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDecksSearchUiState
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDeckFormatFilter
import kotlinx.coroutines.launch

/** One in-progress card inspection triggered from either the Commander or Card picker. */
private data class InspectingSession(
    val items: List<Card>,
    val initialIndex: Int,
    val initialRect: Rect,
    val onSelect: (Card) -> Unit,
)

/**
 * Archidekt-style advanced search sheet for the Community Hub (Discover/Search overhaul, Phase 2).
 *
 * Modeled on the skeleton/visual language of [com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet]
 * (header with Close + Clear all, scrollable [SearchSection]s, big Search button) but exposes ONLY
 * the filters Archidekt's search API actually supports (see
 * `docs/adr/ADR-004-community-api-contracts.md` §1b) — deliberately no deck-tag filter (always
 * statement-timeouts) and colors are EXACT-match only (no include/at-most mode exists).
 *
 * Fully driven by the parent [CommunityDecksSearchUiState] + callbacks — this composable holds no
 * business state of its own beyond the ephemeral card-inspection overlay (pure UI, not business
 * logic).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommunityAdvancedSearchSheet(
    state: CommunityDecksSearchUiState,
    onDismiss: () -> Unit,
    onFormatSelected: (CommunityDeckFormatFilter) -> Unit,
    onColorToggled: (String) -> Unit,
    onBracketSelected: (Int?) -> Unit,
    onCommanderQueryChange: (String) -> Unit,
    onCommanderSelected: (Card) -> Unit,
    onCommanderCleared: () -> Unit,
    onCardQueryChange: (String) -> Unit,
    onCardSelected: (Card) -> Unit,
    onCardCleared: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onDeckSizeChanged: (String) -> Unit,
    onPrimersOnlyToggled: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    onSearch: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val scope = rememberCoroutineScope()
    val filters = state.advancedFilters

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inspecting by remember { mutableStateOf<InspectingSession?>(null) }
    var isDismissingInspection by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    fun handleDismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .onGloballyPositioned { rootCoordinates = it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // ── Header ──────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::handleDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.community_advsearch_close),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        stringResource(R.string.community_advsearch_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearAll) {
                        Text(
                            stringResource(R.string.community_advsearch_clear_all),
                            color = mc.lifeNegative,
                            style = ty.titleMedium,
                        )
                    }
                }

                // ── Scrollable form ─────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .nestedScroll(nestedScrollConnection),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    // ── Colors ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_colors),
                            icon = Icons.Default.Palette
                        ) {
                            ManaColorPicker(
                                selectedColors = filters.colors,
                                onToggleColor = onColorToggled,
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf("W", "U", "B", "R", "G", "C"),
                            )
                            Text(
                                stringResource(R.string.community_advsearch_colors_caption),
                                style = ty.labelSmall,
                                color = mc.textDisabled,
                            )
                        }
                    }

                    // ── Deck format ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_format),
                            icon = Icons.Default.Gavel
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CommunityDeckFormatFilter.entries.forEach { format ->
                                    FilterChip(
                                        selected = filters.format == format,
                                        onClick = { onFormatSelected(format) },
                                        label = { Text(format.label, style = ty.labelMedium) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Commander bracket ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_bracket),
                            icon = Icons.Default.BarChart
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = filters.edhBracket == null,
                                    onClick = { onBracketSelected(null) },
                                    label = { Text(stringResource(R.string.community_advsearch_bracket_any), style = ty.labelMedium) },
                                )
                                (1..5).forEach { bracket ->
                                    FilterChip(
                                        selected = filters.edhBracket == bracket,
                                        onClick = { onBracketSelected(bracket) },
                                        label = { Text("$bracket", style = ty.labelMedium) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Commander ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_commander),
                            icon = Icons.Default.AccountCircle
                        ) {
                            CardPickerField(
                                query = state.commanderQuery,
                                onQueryChange = onCommanderQueryChange,
                                results = state.commanderResults,
                                isSearching = state.isCommanderSearching,
                                selectedCard = filters.commander,
                                onClearSelection = onCommanderCleared,
                                onCardTapped = { index, rect ->
                                    isDismissingInspection = false
                                    inspecting = InspectingSession(
                                        items = state.commanderResults,
                                        initialIndex = index,
                                        initialRect = rect,
                                        onSelect = onCommanderSelected,
                                    )
                                },
                                rootCoordinates = rootCoordinates,
                                modifier = Modifier.fillMaxWidth(),
                                searchHint = stringResource(R.string.community_advsearch_commander_hint),
                                clearContentDescription = stringResource(R.string.community_advsearch_clear_selection),
                            )
                        }
                    }

                    // ── Card ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_card),
                            icon = Icons.Default.Style
                        ) {
                            CardPickerField(
                                query = state.cardQuery,
                                onQueryChange = onCardQueryChange,
                                results = state.cardResults,
                                isSearching = state.isCardSearching,
                                selectedCard = filters.card,
                                onClearSelection = onCardCleared,
                                onCardTapped = { index, rect ->
                                    isDismissingInspection = false
                                    inspecting = InspectingSession(
                                        items = state.cardResults,
                                        initialIndex = index,
                                        initialRect = rect,
                                        onSelect = onCardSelected,
                                    )
                                },
                                rootCoordinates = rootCoordinates,
                                modifier = Modifier.fillMaxWidth(),
                                searchHint = stringResource(R.string.community_advsearch_card_hint),
                                clearContentDescription = stringResource(R.string.community_advsearch_clear_selection),
                            )
                        }
                    }

                    // ── Owner username ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_username),
                            icon = Icons.Default.Person
                        ) {
                            OutlinedTextField(
                                value = filters.ownerUsername,
                                onValueChange = onUsernameChanged,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.community_advsearch_username_hint),
                                        color = mc.textDisabled,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = magicOutlinedTextFieldColors(),
                            )
                        }
                    }

                    // ── Deck size ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_section_size),
                            icon = Icons.Default.Straighten
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.community_advsearch_size_equals),
                                    style = ty.bodyMedium,
                                    color = mc.textSecondary,
                                )
                                OutlinedTextField(
                                    value = filters.deckSize,
                                    onValueChange = onDeckSizeChanged,
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.community_advsearch_size_hint),
                                            color = mc.textDisabled,
                                        )
                                    },
                                    modifier = Modifier.width(100.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = magicOutlinedTextFieldColors(),
                                )
                            }
                        }
                    }

                    // ── Primers ──
                    item {
                        SearchSection(
                            title = stringResource(R.string.community_advsearch_primers_only),
                            icon = Icons.Default.Description
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.community_advsearch_primers_only),
                                    style = ty.bodyMedium,
                                    color = mc.textPrimary,
                                )
                                Switch(
                                    checked = filters.primersOnly,
                                    onCheckedChange = onPrimersOnlyToggled,
                                    colors = SwitchDefaults.colors(checkedTrackColor = mc.primaryAccent),
                                )
                            }
                        }
                    }
                }

                // ── Search button ────────────────────────────────────────────────
                Button(
                    onClick = {
                        onSearch()
                        handleDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    Text(
                        stringResource(R.string.community_advsearch_search_button),
                        style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = mc.onAccent,
                    )
                }
            }

            // ── Card inspection overlay (shared by the Commander and Card pickers) ──────────
            inspecting?.let { session ->
                MagicCardInspectionOverlay(
                    items = session.items,
                    initialIndex = session.initialIndex,
                    initialRect = session.initialRect,
                    isVisible = true,
                    isDismissing = isDismissingInspection,
                    onDismissRequest = { isDismissingInspection = true },
                    onDismiss = { inspecting = null; isDismissingInspection = false },
                    cardExtractor = { it },
                    actions = { card ->
                        Button(
                            onClick = {
                                session.onSelect(card)
                                isDismissingInspection = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.community_advsearch_select),
                                color = mc.onAccent,
                                style = ty.labelLarge,
                            )
                        }
                        Spacer(Modifier.height(sp.xs))
                        OutlinedButton(
                            onClick = { isDismissingInspection = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.community_advsearch_cancel),
                                style = ty.labelLarge,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun magicOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.magicColors.primaryAccent,
    unfocusedBorderColor = MaterialTheme.magicColors.surfaceVariant,
    cursorColor = MaterialTheme.magicColors.primaryAccent,
    focusedTextColor = MaterialTheme.magicColors.textPrimary,
    unfocusedTextColor = MaterialTheme.magicColors.textPrimary,
)
