package com.mmg.manahub.web.carddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Card Detail — the fifth REAL `:webApp` MVP screen (web roadmap W4b). A destination you navigate
 * INTO (from [com.mmg.manahub.web.search.CardSearchScreen]'s result tiles and
 * [com.mmg.manahub.web.collection.CollectionScreen]'s owned-card tiles), not a bottom-nav tab —
 * mirrors how `CardDetailScreen` works on Android.
 *
 * DELIBERATELY minimal and read-only: card image, name, mana cost, type line, oracle text, and
 * prices only. See [CardDetailViewModel]'s KDoc for the full list of Android-side concerns
 * explicitly out of scope for this slice.
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
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingState(colors.primaryAccent)
                uiState.error != null -> ErrorState(uiState.error.orEmpty(), colors.lifeNegative)
                uiState.card != null -> CardDetailContent(
                    card = uiState.card!!,
                    windowSizeClass = windowSizeClass,
                )
                else -> Unit
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
private fun CardDetailContent(card: Card, windowSizeClass: ManaWindowSizeClass) {
    val spacing = MaterialTheme.spacing
    if (windowSizeClass == ManaWindowSizeClass.COMPACT) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                MagicCard(card = card, modifier = Modifier.widthIn(max = DETAIL_IMAGE_WIDTH).fillMaxWidth())
            }
            CardDetailTextBlock(card = card)
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
                CardDetailTextBlock(card = card)
            }
        }
    }
}

@Composable
private fun CardDetailTextBlock(card: Card) {
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

        CardPricesBlock(card = card)
    }
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
