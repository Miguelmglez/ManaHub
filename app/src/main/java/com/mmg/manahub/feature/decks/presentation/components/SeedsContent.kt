package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import com.mmg.manahub.feature.decks.domain.usecase.InferredIdentity
import com.mmg.manahub.feature.decks.presentation.engine.DeckRole
import com.mmg.manahub.feature.decks.presentation.engine.DeckSkeleton
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import com.mmg.manahub.feature.decks.presentation.improvement.components.BudgetFilterBar

/**
 * SEEDS step content: pick 1+ seed cards, see the inferred color identity + detected strategy + the
 * role skeleton the generated deck will target, choose a budget, and generate the deck.
 *
 * Stateless — all state is hoisted to [com.mmg.manahub.feature.decks.presentation.DeckMagicViewModel].
 *
 * @param seedCards the currently picked seeds (shown as a hero list with remove actions).
 * @param identity the inferred identity (colors + strategy) or null when no seeds are picked.
 * @param skeleton the role skeleton for the active format (drives the skeleton preview bars).
 * @param budget the active budget constraints.
 * @param query the seed-search text.
 * @param searchResults Scryfall search results for the current [query].
 * @param isSearching true while a seed search is in flight.
 * @param canGenerate true when generation is allowed (≥1 seed and not already generating).
 * @param isGenerating true while the deck is being generated.
 */
@Composable
fun SeedsContent(
    seedCards: List<Card>,
    identity: InferredIdentity?,
    skeleton: DeckSkeleton,
    budget: BudgetConstraints,
    query: String,
    searchResults: List<Card>,
    isSearching: Boolean,
    canGenerate: Boolean,
    isGenerating: Boolean,
    onQueryChange: (String) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onBudgetChanged: (BudgetConstraints) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = sp.lg, end = sp.lg, top = sp.lg, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
        ) {
            item(key = "search") {
                SeedSearchField(
                    query = query,
                    isSearching = isSearching,
                    onQueryChange = onQueryChange,
                )
            }

            // Search results dropdown (inline list, only while the user is typing).
            if (query.trim().length >= 2 && searchResults.isNotEmpty()) {
                item(key = "results_header") {
                    Text(
                        text = stringResource(R.string.deck_seeds_results_title),
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                    )
                }
                items(searchResults, key = { "res_${it.scryfallId}" }) { card ->
                    SeedResultRow(card = card, onAdd = { onAddSeed(card) })
                }
            }

            // Picked seeds (hero list).
            item(key = "seeds_header") {
                Text(
                    text = stringResource(R.string.deck_seeds_picked_title),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (seedCards.isEmpty()) {
                item(key = "seeds_empty") {
                    EmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.deck_seeds_empty_title),
                        subtitle = stringResource(R.string.deck_seeds_empty_message),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )
                }
            } else {
                items(seedCards, key = { "seed_${it.scryfallId}" }) { card ->
                    SeedHeroRow(card = card, onRemove = { onRemoveSeed(card) })
                }
            }

            // Inferred identity (colors + strategy).
            if (identity != null) {
                item(key = "identity") {
                    InferredIdentityCard(identity = identity)
                }
                item(key = "skeleton") {
                    SkeletonPreviewCard(skeleton = skeleton)
                }
            }

            // Budget filter.
            item(key = "budget") {
                BudgetFilterBar(budget = budget, onBudgetChanged = onBudgetChanged)
            }
        }

        // Sticky "Generate deck" CTA pinned to the bottom.
        Surface(
            color = mc.background,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Button(
                onClick = onGenerate,
                enabled = canGenerate,
                shape = ChipShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                    disabledContainerColor = mc.surfaceVariant,
                    disabledContentColor = mc.textDisabled,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.lg, vertical = sp.md)
                    .height(52.dp),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = mc.background,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(sp.sm))
                    Text(
                        text = stringResource(R.string.deck_seeds_generating),
                        style = MaterialTheme.magicTypography.titleMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.deck_seeds_generate_cta),
                        style = MaterialTheme.magicTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Search text field for finding seed cards (debounced upstream in the ViewModel). */
@Composable
private fun SeedSearchField(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.deck_seeds_search_hint),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textDisabled,
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = mc.primaryAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
            }
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textSecondary,
                    )
                }
            }
        } else null,
        shape = CardShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            focusedTextColor = mc.textPrimary,
            unfocusedTextColor = mc.textPrimary,
            cursorColor = mc.primaryAccent,
        ),
    )
}

/** A search-result row with an add button (≥48dp target). */
@Composable
private fun SeedResultRow(card: Card, onAdd: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        onClick = onAdd,
        shape = CardShape,
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(ChipShape),
            )
            Column(Modifier.weight(1f)) {
                Text(card.name, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.setName, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deck_seeds_add_seed), tint = mc.primaryAccent)
            }
        }
    }
}

/** A picked-seed hero row with art and a remove button. */
@Composable
private fun SeedHeroRow(card: Card, onRemove: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        shape = CardShape,
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(ChipShape),
            )
            Column(Modifier.weight(1f)) {
                Text(card.name, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.typeLine, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deck_seeds_remove_seed), tint = mc.textSecondary)
            }
        }
    }
}

/** Inferred identity card: mana pips + detected strategy. */
@Composable
private fun InferredIdentityCard(identity: InferredIdentity) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.deck_seeds_identity_title).uppercase(),
                style = ty.labelMedium,
                color = mc.primaryAccent,
                fontWeight = FontWeight.Bold,
            )

            // Colors row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text(
                    text = stringResource(R.string.deck_seeds_identity_colors),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (identity.colorIdentity.isEmpty()) {
                    Text(stringResource(R.string.deck_seeds_identity_colorless), style = ty.bodyMedium, color = mc.textPrimary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        ManaColor.entries
                            .filter { it in identity.colorIdentity }
                            .forEach { ManaPip(color = it) }
                    }
                }
            }

            // Strategy row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.deck_seeds_identity_strategy),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                val strategyLabel = identity.strategy?.let { "${it.icon} ${it.displayName}" }
                    ?: stringResource(R.string.deck_seeds_identity_strategy_unknown)
                Text(text = strategyLabel, style = ty.bodyMedium, color = mc.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A single colored mana pip with its letter. */
@Composable
private fun ManaPip(color: ManaColor) {
    val mc = MaterialTheme.magicColors
    val pipColor: Color = when (color) {
        ManaColor.W -> mc.manaW
        ManaColor.U -> mc.manaU
        ManaColor.B -> mc.manaB
        ManaColor.R -> mc.manaR
        ManaColor.G -> mc.manaG
        ManaColor.C -> mc.manaC
    }
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape).background(pipColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = color.symbol,
            style = MaterialTheme.magicTypography.labelSmall,
            // Pip backgrounds are saturated; the symbol uses textPrimary which reads on every theme's pips.
            color = mc.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Preview of the role skeleton the generated deck will target (ideal counts per functional role). */
@Composable
private fun SkeletonPreviewCard(skeleton: DeckSkeleton) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val slots = skeleton.slots.filter { it.ideal > 0 }
    val maxIdeal = slots.maxOfOrNull { it.ideal }?.coerceAtLeast(1) ?: 1

    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.deck_seeds_skeleton_title).uppercase(),
                style = ty.labelMedium,
                color = mc.primaryAccent,
                fontWeight = FontWeight.Bold,
            )
            slots.forEach { slot ->
                SkeletonBar(role = slot.role, ideal = slot.ideal, maxIdeal = maxIdeal)
            }
        }
    }
}

/** One labelled role bar: a proportional fill plus the ideal count. */
@Composable
private fun SkeletonBar(role: DeckRole, ideal: Int, maxIdeal: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(
            text = role.displayLabel(),
            style = ty.labelMedium,
            color = mc.textSecondary,
            modifier = Modifier.width(120.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val fill = (ideal.toFloat() / maxIdeal).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(ChipShape)
                .background(mc.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(10.dp)
                    .clip(ChipShape)
                    .background(mc.primaryAccent),
            )
        }
        Text(text = ideal.toString(), style = ty.labelMedium, color = mc.textPrimary, modifier = Modifier.width(24.dp))
    }
}

/** Short human label for a deck role (English-only, no localization branch needed). */
private fun DeckRole.displayLabel(): String = when (this) {
    DeckRole.RAMP -> "Ramp"
    DeckRole.CARD_ADVANTAGE -> "Card advantage"
    DeckRole.SPOT_REMOVAL -> "Spot removal"
    DeckRole.BOARD_WIPE -> "Board wipes"
    DeckRole.INTERACTION -> "Interaction"
    DeckRole.TUTOR -> "Tutors"
    DeckRole.PAYOFF -> "Payoffs"
    DeckRole.SYNERGY -> "Synergy"
    DeckRole.THREAT -> "Threats"
    DeckRole.LAND -> "Lands"
    DeckRole.FILLER -> "Filler"
}
