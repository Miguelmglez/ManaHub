package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery

/** Height of the cluster-strength indicator bar track. */
private val FitBarHeight = 6.dp

/** Card-art thumbnail size for the discovery preview row. */
private val ArtThumbWidth = 52.dp
private val ArtThumbHeight = 38.dp

/** Minimum touch-target size so each art thumbnail is tappable (≥48dp). */
private val ArtTouchTarget = 48.dp

/** The cluster size treated as a "strong" synergy when normalizing the fit bar. */
private const val STRONG_CLUSTER_SIZE = 8f

/**
 * A single collection-synergy discovery card for the Inspirations surface (Phase 4).
 *
 * Adapted from the legacy `DiscoveryCard` (DeckMagicScreen) but rebuilt on ManaHub
 * design tokens (no raw shapes/dp-fonts/CardDefaults). [MagicDiscovery] carries no
 * numeric score, so cluster strength is represented as a labelled horizontal bar
 * (fill = cluster size / [STRONG_CLUSTER_SIZE]) rather than a bare percentage.
 *
 * @param onCardClick opens a card detail screen for the tapped art (by scryfallId).
 * @param onSeedStudio pre-seeds the studio's seed sheet from this discovery.
 */
@Composable
internal fun DiscoveryRow(
    discovery: MagicDiscovery,
    onCardClick: (String) -> Unit,
    onSeedStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.backgroundSecondary,
        shape = CardShape,
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(
                text = discovery.label,
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                text = discovery.primaryTag.label(),
                style = ty.labelMedium,
                color = mc.goldMtg,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = discovery.description,
                style = ty.bodySmall,
                color = mc.textSecondary,
            )

            // ── Fit indicator: labelled cluster-strength bar (no bare %) ──────────
            Spacer(Modifier.height(spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fillFraction = (discovery.cards.size / STRONG_CLUSTER_SIZE).coerceIn(0.1f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(FitBarHeight)
                        .clip(ChipShape)
                        .background(mc.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .height(FitBarHeight)
                            .clip(ChipShape)
                            .background(mc.primaryAccent),
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.deck_studio_inspiration_fit, discovery.cards.size),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }

            // ── Card-art preview row ──────────────────────────────────────────────
            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(discovery.cards.take(6), key = { it.card.scryfallId }) { magicCard ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = ArtTouchTarget)
                            .clip(ChipShape)
                            .clickable { onCardClick(magicCard.card.scryfallId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = magicCard.card.imageArtCrop,
                            contentDescription = stringResource(R.string.deck_studio_inspiration_card_art),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = ArtThumbWidth, height = ArtThumbHeight)
                                .clip(ChipShape),
                        )
                    }
                }
            }

            // ── Primary action ────────────────────────────────────────────────────
            Spacer(Modifier.height(spacing.sm))
            OutlinedButton(
                onClick = onSeedStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ArtTouchTarget),
                border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                shape = ButtonShape,
            ) {
                Text(
                    text = stringResource(R.string.deck_studio_inspiration_seed_studio),
                    style = ty.labelLarge,
                    color = mc.primaryAccent,
                )
            }
        }
    }
}
