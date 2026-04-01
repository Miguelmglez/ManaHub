package com.mmg.magicfolder.feature.decks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.BasicLandDistribution
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DeckCard
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.components.ManaSymbolImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ── DeckCardRow ───────────────────────────────────────────────────────────────

@Composable
fun DeckCardRow(
    deckCard:    DeckCard,
    onAdd:       (() -> Unit)? = null,
    onRemove:    (() -> Unit)? = null,
    modifier:    Modifier = Modifier,
) {
    val mc   = MaterialTheme.magicColors
    val card = deckCard.card

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(mc.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model              = card.imageArtCrop,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(width = 44.dp, height = 32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(mc.surfaceVariant),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = card.name,
                style    = MaterialTheme.magicTypography.bodyMedium,
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.manaCost?.let { cost ->
                    ManaCostImages(manaCost = cost, symbolSize = 12.dp)
                }
                Text(
                    text  = card.typeLine.substringBefore(" —").substringBefore(" -").take(20),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
                if (deckCard.isOwned) {
                    Text(
                        text  = stringResource(R.string.deckbuilder_owned_label),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifePositive,
                    )
                }
            }
        }

        if (deckCard.quantity > 0) {
            Text(
                text  = "×${deckCard.quantity}",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove",
                    tint = mc.textDisabled, modifier = Modifier.size(14.dp))
            }
        }

        if (onAdd != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.15f))
                    .clickable { onAdd() },
            ) {
                Text("+", style = MaterialTheme.magicTypography.labelLarge, color = mc.primaryAccent)
            }
        }
    }
}

// ── CommanderBanner ───────────────────────────────────────────────────────────

@Composable
fun CommanderBanner(
    commander: Card,
    modifier:  Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(mc.goldMtg.copy(alpha = 0.12f))
            .border(0.5.dp, mc.goldMtg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model              = commander.imageArtCrop,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(width = 48.dp, height = 34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(mc.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = stringResource(R.string.deckbuilder_commander_label),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.goldMtg,
            )
            Text(
                text     = commander.name,
                style    = MaterialTheme.magicTypography.bodyMedium,
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        commander.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 14.dp) }
    }
}

// ── LandsSection ─────────────────────────────────────────────────────────────

@Composable
fun LandsSection(
    nonBasicLands:  List<DeckCard>,
    basicLands:     BasicLandDistribution,
    onRemoveNonBasic: (String) -> Unit,
    modifier:       Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text  = stringResource(R.string.deckbuilder_lands),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.textSecondary,
        )

        // Non-basic lands
        nonBasicLands.forEach { dc ->
            DeckCardRow(
                deckCard = dc,
                onRemove = { onRemoveNonBasic(dc.card.scryfallId) },
            )
        }

        // Basic land distribution
        if (basicLands.total > 0) {
            Row(
                modifier              = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(mc.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Basic Lands",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                basicLands.toMap().forEach { (color, count) ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ManaSymbolImage(token = color, size = 14.dp)
                        Text(
                            text  = "$count",
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

// ── BuildingFilters ───────────────────────────────────────────────────────────

@Composable
fun BuildingFilters(
    selectedColors:  Set<String>,
    onToggleColor:   (String) -> Unit,
    onClearFilters:  () -> Unit,
    modifier:        Modifier = Modifier,
) {
    val mc     = MaterialTheme.magicColors
    val colors = listOf("W", "U", "B", "R", "G")

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        colors.forEach { color ->
            val selected = color in selectedColors
            ManaSymbolImage(
                token    = color,
                size     = 28.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) Color.White.copy(alpha = 0.7f) else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onToggleColor(color) }
                    .then(
                        if (!selected) Modifier.background(Color.Black.copy(0.4f), CircleShape)
                        else Modifier
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectedColors.isNotEmpty()) {
            TextButton(onClick = onClearFilters) {
                Text(
                    text  = stringResource(R.string.action_clear_filters),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}

// ── CommanderSearchSheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommanderSearchSheet(
    results:          List<Card>,
    isSearching:      Boolean,
    onQueryChange:    (String) -> Unit,
    onSelectCard:     (Card) -> Unit,
    onDismiss:        () -> Unit,
) {
    val mc           = MaterialTheme.magicColors
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query        by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest   = onDismiss,
        sheetState         = sheetState,
        containerColor     = mc.backgroundSecondary,
        dragHandle         = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text  = stringResource(R.string.deckbuilder_setup_commander_label),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )

            OutlinedTextField(
                value         = query,
                onValueChange = { query = it; onQueryChange(it) },
                placeholder   = {
                    Text(
                        stringResource(R.string.deckbuilder_search_commander_hint),
                        color = mc.textDisabled,
                    )
                },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = mc.textDisabled) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor     = mc.textPrimary,
                    unfocusedTextColor   = mc.textPrimary,
                    cursorColor          = mc.primaryAccent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
            )

            if (isSearching) {
                Box(
                    modifier          = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment  = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results, key = { it.scryfallId }) { card ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(mc.surface)
                                .clickable { onSelectCard(card) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AsyncImage(
                                model              = card.imageArtCrop,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(width = 48.dp, height = 34.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(mc.surfaceVariant),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text     = card.name,
                                    style    = MaterialTheme.magicTypography.bodyMedium,
                                    color    = mc.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text     = card.typeLine,
                                    style    = MaterialTheme.magicTypography.labelSmall,
                                    color    = mc.textDisabled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 14.dp) }
                        }
                    }
                }
            }
        }
    }
}
