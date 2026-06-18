package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.domain.model.GroupingMode
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

// ─────────────────────────────────────────────────────────────────────────────
//  Shared deck-editor composables
//
//  Extracted from DeckBuilderScreen.kt (P1-T2) so both DeckMagicDetailScreen and
//  the new DeckStudioScreen render identical rows/sheets/headers without
//  duplicating UI. These are stateless and VM-agnostic by design: every callback
//  is hoisted so each host wires its own ViewModel.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single card row inside a deck list (mainboard or sideboard).
 *
 * @param entry the deck slot to render.
 * @param isInCollection whether the user owns this card (shows a star).
 * @param onClick invoked when the row body is tapped (opens detail).
 * @param onRemove invoked when the close button is tapped (removes the slot).
 */
@Composable
internal fun CardRow(
    entry: DeckSlotEntry,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = mc.surface,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            AsyncImage(
                model = entry.card?.imageNormal,
                contentDescription = null,
                modifier = Modifier.size(width = 44.dp, height = 60.dp).clip(ChipShape),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                CardName(
                    name          = entry.card?.name ?: stringResource(R.string.deck_default_name),
                    showFrontOnly = true,
                    style         = ty.bodyMedium,
                    color         = mc.textPrimary,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis
                )
                entry.card?.typeLine?.let {
                    Text(it, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                entry.card?.manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 14.dp)
                }
            }
            if (entry.quantity > 1) {
                Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.2f)) {
                    Text("×${entry.quantity}", style = ty.labelMedium, color = mc.primaryAccent, modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs))
                }
            }
            if (isInCollection) {
                Icon(Icons.Default.Star, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * A section/group header for a card list (e.g. "Creatures (12)", a color, or a CMC bucket).
 *
 * @param label the raw group key; localized to a display name internally.
 * @param count the number of cards in the group.
 * @param showSuggestionToggle whether to show the land-suggestion toggle (lands group only).
 */
@Composable
internal fun GroupHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    showSuggestionToggle: Boolean = false,
    isSuggestionEnabled: Boolean = true,
    onToggleSuggestion: () -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colorName = when {
            label == "W" -> stringResource(R.string.collection_filter_white)
            label == "U" -> stringResource(R.string.collection_filter_blue)
            label == "B" -> stringResource(R.string.collection_filter_black)
            label == "R" -> stringResource(R.string.collection_filter_red)
            label == "G" -> stringResource(R.string.collection_filter_green)
            label == "Multicolor" -> stringResource(R.string.collection_filter_multicolor)
            label == "Colorless" -> stringResource(R.string.stats_color_colorless)
            label == "Land" || label == "Lands" -> stringResource(R.string.deckbuilder_lands)
            label == "Creatures" -> stringResource(R.string.deckdetail_group_creatures)
            label == "Instants" -> stringResource(R.string.deckdetail_group_instants)
            label == "Sorceries" -> stringResource(R.string.deckdetail_group_sorceries)
            label == "Enchantments" -> stringResource(R.string.deckdetail_group_enchantments)
            label == "Artifacts" -> stringResource(R.string.deckdetail_group_artifacts)
            label == "Planeswalkers" -> stringResource(R.string.deckdetail_group_planeswalkers)
            label == "Other" -> stringResource(R.string.deckdetail_group_other)
            label == "Untagged" -> stringResource(R.string.deckbuilder_group_untagged)
            label.all { it.isDigit() } -> {
                val cmc = label.toInt()
                if (cmc == 7) stringResource(R.string.deckbuilder_cost_7_plus)
                else stringResource(R.string.deckbuilder_cost_value, cmc)
            }
            else -> label
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (label.length == 1 && label[0] in "WUBRG") {
                ManaSymbolImage(token = label, size = 20.dp)
            }
            Text(colorName, style = ty.titleMedium, color = mc.goldMtg)
            Text("($count)", style = ty.bodyMedium, color = mc.textSecondary)
        }

        if (showSuggestionToggle) {
            TextButton(
                onClick = onToggleSuggestion,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.sm, vertical = spacing.xxs)
            ) {
                val text = if (isSuggestionEnabled) stringResource(R.string.deckbuilder_land_suggestions_disable) else stringResource(R.string.deckbuilder_land_suggestions_enable)
                Text(text = text, style = ty.labelSmall, color = mc.primaryAccent)
            }
        }
    }
}

/**
 * The "move to / move from" action row shown below a [CardRow], used to shuffle a
 * card between the mainboard and sideboard.
 */
@Composable
internal fun MovementRow(
    labelTo: String,
    onMoveTo: () -> Unit,
    labelFrom: String? = null,
    onMoveFrom: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.md, end = spacing.md, top = spacing.xxs, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Action: Move To [Other]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(ChipShape)
                .clickable(onClick = onMoveTo)
                .padding(vertical = spacing.xs, horizontal = spacing.xs)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(spacing.xs))
            Text(labelTo, style = ty.labelMedium, color = mc.primaryAccent)
        }

        // Right Action: Move From [Other] (if applicable)
        if (labelFrom != null && onMoveFrom != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(ChipShape)
                    .clickable(onClick = onMoveFrom)
                    .padding(vertical = spacing.xs, horizontal = spacing.xs)
            ) {
                Text(labelFrom, style = ty.labelMedium, color = mc.secondaryAccent)
                Spacer(Modifier.width(spacing.xs))
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationY = 180f }
                )
            }
        }
    }
}

/**
 * Bottom sheet that lets the user add/remove the five basic lands.
 *
 * VM-agnostic: pass the current basic-land counts and resolve a mana symbol per
 * land name via [manaCodeFor].
 *
 * @param basicLandCounts current count of each basic land in the mainboard.
 * @param onAddBasicLand invoked with a basic-land name to add one copy.
 * @param onRemoveBasicLand invoked with a basic-land name to remove one copy.
 * @param manaCodeFor resolves the WUBRG mana symbol for a basic-land name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BasicLandsSheet(
    basicLandCounts: Map<String, Int>,
    onAddBasicLand: (String) -> Unit,
    onRemoveBasicLand: (String) -> Unit,
    manaCodeFor: (String) -> String?,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = mc.backgroundSecondary) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg).padding(bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.deckdetail_basic_lands_sheet_title), style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(vertical = spacing.sm))
            BASIC_LAND_NAMES.forEach { landName ->
                BasicLandRow(
                    name = landName,
                    quantityInDeck = basicLandCounts[landName] ?: 0,
                    onAdd = { onAddBasicLand(landName) },
                    onRemove = { onRemoveBasicLand(landName) },
                    manaCode = { manaCodeFor(landName) },
                )
            }
        }
    }
}

@Composable
internal fun BasicLandRow(name: String, quantityInDeck: Int, onAdd: () -> Unit, onRemove: () -> Unit, manaCode: () -> String?) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(shape = ChipShape, color = mc.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            manaCode()?.let { ManaSymbolImage(token = it, size = 24.dp) }
            Text(name, style = ty.bodyMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (quantityInDeck > 0) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Remove, null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Text("$quantityInDeck", style = ty.labelMedium, color = mc.primaryAccent)
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddBasicLandsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        shape = ChipShape,
        color = mc.surface,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Icon(Icons.Default.Park, null, tint = mc.primaryAccent, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.deckdetail_add_basic_lands), style = ty.bodyMedium, color = mc.primaryAccent, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Bottom sheet to edit a deck's name and cover art.
 *
 * @param deck the deck being edited (provides the current name + cover + format).
 * @param cards mainboard/sideboard slots, used to populate the cover-art picker.
 * @param onSave invoked with the new name and/or cover id (either may be null).
 * @param onDismiss invoked when the sheet is dismissed.
 * @param onFormatChange optional — when non-null, a format selector chip row is shown and
 *        each pick is forwarded immediately (writes through the VM, independent of [onSave]).
 *        Null callers (e.g. legacy Deck Magic) keep the previous name/cover-only sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditDeckSheet(
    deck: Deck?,
    cards: List<DeckSlotEntry>,
    onSave: (newName: String?, newCoverId: String?) -> Unit,
    onDismiss: () -> Unit,
    onFormatChange: ((DeckFormat) -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var nameText by remember(deck?.name) { mutableStateOf(deck?.name ?: "") }
    var selectedCoverId by remember(deck?.coverCardId) { mutableStateOf(deck?.coverCardId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth().padding(spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.deck_edit_title), style = ty.titleLarge, color = mc.textPrimary)
                Button(onClick = { onSave(nameText, selectedCoverId) }, colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent), shape = ButtonShape) {
                    Text(stringResource(R.string.action_save), style = ty.labelLarge, fontWeight = FontWeight.SemiBold, color = mc.background)
                }
            }
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                label = { Text(stringResource(R.string.deckbuilder_setup_name_label), style = ty.labelLarge) },
                singleLine = true,
                textStyle = ty.bodyLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mc.primaryAccent, cursorColor = mc.primaryAccent)
            )

            val coverCards = remember(cards) {
                cards.filter { it.card?.imageArtCrop != null }.distinctBy { it.scryfallId }
            }
            if (coverCards.isNotEmpty()) {
                Text(stringResource(R.string.deck_edit_cover_section), style = ty.labelLarge, modifier = Modifier.padding(spacing.lg))
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md), horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(coverCards, key = { "cover_" + it.scryfallId }) { dc ->
                        val isSelected = dc.scryfallId == selectedCoverId
                        Box(
                            modifier = Modifier.aspectRatio(1.37f).clip(ChipShape)
                                .clickable { selectedCoverId = dc.scryfallId }
                                .then(if (isSelected) Modifier.border(2.dp, mc.primaryAccent, ChipShape) else Modifier)
                        ) {
                            AsyncImage(
                                model = dc.card?.imageArtCrop,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The deck formats offered by the studio's format selector (Group B / B1), in display order.
 *
 * A curated subset of [DeckFormat] — the formats a user actively builds toward. Other
 * [DeckFormat] entries (e.g. the engine-internal ones) are intentionally not offered here.
 */
internal val STUDIO_FORMATS: List<DeckFormat> = listOf(
    DeckFormat.COMMANDER,
    /*DeckFormat.STANDARD,

    DeckFormat.PIONEER,
    DeckFormat.MODERN,
    DeckFormat.PAUPER,*/
    DeckFormat.CASUAL,
    DeckFormat.DRAFT,
)

/**
 * A row of pill-cards for picking a deck [DeckFormat] (Group B / B1), similar to the
 * game mode selector.
 *
 * Stateless: the currently selected format is passed in and every pick is a callback. Shared
 * by the Deck Studio empty-state and [EditDeckSheet] so the two surfaces look identical.
 *
 * @param selectedFormat the resolved current format (null when unrecognized → no chip selected).
 * @param onFormatSelected invoked with the picked format.
 */
@Composable
internal fun DeckFormatChipRow(
    selectedFormat: DeckFormat?,
    onFormatSelected: (DeckFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        STUDIO_FORMATS.forEach { format ->
            DeckFormatPill(
                format = format,
                selected = format == selectedFormat,
                onClick = { onFormatSelected(format) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single pill card representing one [DeckFormat] option.
 */
@Composable
private fun DeckFormatPill(
    format: DeckFormat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "formatPillScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 300),
        label = "formatPillBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
        animationSpec = tween(durationMillis = 300),
        label = "formatPillBg",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = spacing.md, horizontal = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = when (format) {
                DeckFormat.COMMANDER -> "⚔️"
                DeckFormat.DRAFT -> "📦"
                else -> "🔮"
            },
            style = ty.bodyMedium,
        )
        Text(
            text = stringResource(format.displayNameRes),
            style = ty.labelLarge,
            color = if (selected) mc.primaryAccent else mc.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.decklist_card_count, format.targetDeckSize),
            style = ty.labelSmall,
            color = mc.textSecondary,
        )
    }
}


/**
 * Groups deck slot entries by the given [GroupingMode] for sectioned display.
 *
 * Returns ordered (groupLabel → entries) pairs. The "Lands"/"Land" group is always
 * kept (even when empty) so the add-basic-lands affordance has a home.
 */
internal fun groupCards(cards: List<DeckSlotEntry>, mode: GroupingMode): List<Pair<String, List<DeckSlotEntry>>> {
    return when (mode) {
        GroupingMode.TYPE -> {
            val order = listOf("Creatures", "Instants", "Sorceries", "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other")
            val groups = cards.groupBy { entry ->
                val type = entry.card?.typeLine ?: ""
                when {
                    type.contains("Creature") -> "Creatures"
                    type.contains("Instant") -> "Instants"
                    type.contains("Sorcery") -> "Sorceries"
                    type.contains("Enchantment") -> "Enchantments"
                    type.contains("Artifact") -> "Artifacts"
                    type.contains("Planeswalker") -> "Planeswalkers"
                    type.contains("Land") -> "Lands"
                    else -> "Other"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card?.name } ?: emptyList()
                if (list.isEmpty() && label != "Lands") null
                else label to list
            }
        }
        GroupingMode.COLOR -> {
            // L4: "Other" must be in the order list — unresolved slots (card == null) are
            // bucketed there, and omitting it from `order` silently DROPPED those rows from
            // the editor. Kept only when non-empty (unlike "Land", which always shows).
            val order = listOf("W", "U", "B", "R", "G", "Multicolor", "Colorless", "Land", "Other")
            val groups = cards.groupBy { entry ->
                val card = entry.card ?: return@groupBy "Other"
                if (card.typeLine.contains("Land")) return@groupBy "Land"
                when (card.colors.size) {
                    0 -> "Colorless"
                    1 -> card.colors.first()
                    else -> "Multicolor"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card?.name } ?: emptyList()
                if (list.isEmpty() && label != "Land") null
                else label to list
            }
        }
        GroupingMode.COST -> {
            val nonLands = cards.filter { it.card != null && !BasicLandCalculator.isLand(it.card!!) }
            val groups = nonLands.groupBy { entry ->
                (entry.card?.cmc?.toInt()?.coerceIn(0, 7) ?: 0).toString()
            }
            groups.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key.toInt() }.map { it.key to it.value.sortedBy { it.card?.name } }
        }
        GroupingMode.TAG -> {
            val tagMap = mutableMapOf<String, MutableList<DeckSlotEntry>>()
            cards.forEach { entry ->
                val tags = (entry.card?.tags ?: emptyList()) + (entry.card?.userTags ?: emptyList())
                if (tags.isEmpty()) {
                    tagMap.getOrPut("Untagged") { mutableListOf() }.add(entry)
                } else {
                    tags.forEach { tag ->
                        tagMap.getOrPut(tag.label) { mutableListOf() }.add(entry)
                    }
                }
            }
            tagMap.entries.filter { it.value.isNotEmpty() }.sortedByDescending { it.value.size }.map { it.key to it.value.sortedBy { it.card?.name } }
        }
    }
}
