package com.mmg.manahub.web.carddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardTagGroup
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Card Detail — the fifth REAL `:webApp` MVP screen (web roadmap W4b, completed 2026-08-05). A
 * destination you navigate INTO (from [com.mmg.manahub.web.search.CardSearchScreen]'s result
 * tiles and [com.mmg.manahub.web.collection.CollectionScreen]'s owned-card tiles), not a
 * bottom-nav tab — mirrors how `CardDetailScreen` works on Android.
 *
 * Card image, name, mana cost, type line, oracle text, prices, read-only tag DISPLAY, and
 * language/print/art-variant switching (via [onNavigateToCard], re-navigating to this same screen
 * with a different [scryfallId]). See [CardDetailViewModel]'s KDoc for the full scope
 * boundary — tag EDITING stays out of scope.
 *
 * Responsive per web roadmap plan §1: stacked column (image above text) at
 * [ManaWindowSizeClass.COMPACT], side-by-side (fixed-width image + scrollable text column) at
 * [ManaWindowSizeClass.MEDIUM]/[ManaWindowSizeClass.EXPANDED]/[ManaWindowSizeClass.LARGE].
 */
@Composable
fun CardDetailScreen(
    scryfallId: String,
    windowSizeClass: ManaWindowSizeClass,
    onBack: () -> Unit,
    onNavigateToCard: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    // Key on scryfallId: even though this is a real nav destination (fresh NavBackStackEntry per
    // navigate() call, not the overlay pattern documented in CLAUDE.md's koinViewModel gotcha),
    // keying explicitly is cheap insurance against a future refactor that reuses this composable's
    // call site for multiple ids without a fresh back-stack entry.
    val viewModel = koinViewModel<CardDetailViewModel>(key = scryfallId) { parametersOf(scryfallId) }
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
                text = uiState.card?.name ?: "Card Detail",
                style = typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (uiState.card != null) {
                IconButton(onClick = viewModel::onOpenPrintsPicker) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "View other printings and languages",
                        tint = colors.textPrimary,
                    )
                }
                IconButton(onClick = viewModel::onOpenArtVariantsPicker) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "View art variants",
                        tint = colors.textPrimary,
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingState(colors.primaryAccent)
                uiState.error != null -> ErrorState(uiState.error.orEmpty(), colors.lifeNegative)
                uiState.card != null -> CardDetailContent(
                    card = uiState.card!!,
                    strategyTags = uiState.strategyTags,
                    windowSizeClass = windowSizeClass,
                )
                else -> Unit
            }
        }
    }

    if (uiState.showPrintsPicker) {
        CardVersionPickerDialog(
            title = "Printings & languages",
            cards = uiState.printsAndLanguages,
            isLoading = uiState.printsLoading,
            currentScryfallId = scryfallId,
            onSelect = { card ->
                viewModel.onDismissPrintsPicker()
                onNavigateToCard(card.scryfallId)
            },
            onDismiss = viewModel::onDismissPrintsPicker,
        )
    }

    if (uiState.showArtVariantsPicker) {
        CardVersionPickerDialog(
            title = "Art variants",
            cards = uiState.artVariants,
            isLoading = uiState.artVariantsLoading,
            currentScryfallId = scryfallId,
            onSelect = { card ->
                viewModel.onDismissArtVariantsPicker()
                onNavigateToCard(card.scryfallId)
            },
            onDismiss = viewModel::onDismissArtVariantsPicker,
        )
    }
}

/**
 * The ONE generic "pick a Card, navigate to its detail" picker, reused for prints/languages AND
 * art variants (per the task brief — no three bespoke pickers). A plain [MagicAlertDialog] with a
 * [LazyColumn] of [CardVersionRow]s in its `content` slot, rather than a `ModalBottomSheet` — no
 * web screen has used `ModalBottomSheet` yet (unverified on wasmJs at the time of writing), while
 * [MagicAlertDialog] (wrapping `BasicAlertDialog`, a core Compose Multiplatform primitive) is
 * already the project's mandatory dialog component and needs no new platform verification.
 */
@Composable
private fun CardVersionPickerDialog(
    title: String,
    cards: List<Card>,
    isLoading: Boolean,
    currentScryfallId: String,
    onSelect: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissLabel = "Close",
        onDismiss = onDismiss,
        content = {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }

                cards.isEmpty() -> Text(
                    text = "No other versions found.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(cards, key = { it.scryfallId }) { card ->
                        CardVersionRow(
                            card = card,
                            isSelected = card.scryfallId == currentScryfallId,
                            onClick = { onSelect(card) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CardVersionRow(card: Card, isSelected: Boolean, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (isSelected) colors.primaryAccent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${card.setName} (${card.setCode.uppercase()})",
                style = typography.bodyMedium,
                color = if (isSelected) colors.primaryAccent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "#${card.collectorNumber} · ${CardConstants.getFlag(card.lang)} ${CardConstants.getLanguageName(card.lang)}",
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
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
private fun ErrorState(message: String, errorColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Couldn't load this card: $message",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = errorColor,
        )
    }
}

private val DETAIL_IMAGE_WIDTH = 280.dp

@Composable
private fun CardDetailContent(card: Card, strategyTags: List<CardTag>, windowSizeClass: ManaWindowSizeClass) {
    val spacing = MaterialTheme.spacing
    if (windowSizeClass == ManaWindowSizeClass.COMPACT) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                MagicCard(card = card, modifier = Modifier.widthIn(max = DETAIL_IMAGE_WIDTH).fillMaxWidth())
            }
            CardDetailTextBlock(card = card, strategyTags = strategyTags)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            MagicCard(card = card, modifier = Modifier.width(DETAIL_IMAGE_WIDTH))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                CardDetailTextBlock(card = card, strategyTags = strategyTags)
            }
        }
    }
}

@Composable
private fun CardDetailTextBlock(card: Card, strategyTags: List<CardTag>) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CardName(
            name = card.name,
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        val manaCost = card.manaCost
        if (!manaCost.isNullOrBlank()) {
            ManaCostImages(manaCost = manaCost, symbolSize = 20.dp)
        }

        Text(
            text = card.typeLine,
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )

        val oracleText = card.oracleText
        if (!oracleText.isNullOrBlank()) {
            OracleText(
                text = oracleText,
                style = typography.bodyMedium.copy(color = colors.textPrimary),
            )
        }

        // Read-only tag DISPLAY (W4b completion slice) -- union of the card's own auto-confirmed/
        // user tags (empty from a bare Scryfall fetch on web today) with the precomputed
        // `card_strategy_tags` result, deduped by key. Tag EDITING is out of scope (see
        // CardDetailViewModel's KDoc) -- every chip here is purely decorative (no onClick/onRemove).
        val allTags = (card.tags + card.userTags + strategyTags).distinctBy { it.key }
        if (allTags.isNotEmpty()) {
            CardTagsRow(tags = allTags)
        }

        CardPricesBlock(card = card)
    }
}

@Composable
private fun CardTagsRow(tags: List<CardTag>) {
    CardTagGroup(
        tags = tags,
        tagLabel = { it.displayLabel }
    )
}

@Composable
private fun CardPricesBlock(card: Card) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val rows = buildList {
        card.priceUsd?.let { add("USD" to formatPrice(it, "$")) }
        card.priceUsdFoil?.let { add("USD Foil" to formatPrice(it, "$")) }
        card.priceEur?.let { add("EUR" to formatPrice(it, "€")) }
        card.priceEurFoil?.let { add("EUR Foil" to formatPrice(it, "€")) }
    }

    Column(
        modifier = Modifier.padding(top = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "Prices",
            style = typography.labelLarge,
            color = colors.textPrimary,
        )
        if (rows.isEmpty()) {
            Text(
                text = "No pricing data available.",
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            rows.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = "$label:", style = typography.bodySmall, color = colors.textSecondary)
                    Text(text = value, style = typography.bodySmall, color = colors.textPrimary)
                }
            }
        }
    }
}

/** Formats a price [value] as e.g. "$3.42" -- no `java.text`/platform formatter available in commonMain/wasmJs. */
private fun formatPrice(value: Double, currencySymbol: String): String {
    val cents = kotlin.math.round(value * 100).toLong().coerceAtLeast(0L)
    val dollars = cents / 100
    val remainder = cents % 100
    return "$currencySymbol$dollars.${remainder.toString().padStart(2, '0')}"
}
