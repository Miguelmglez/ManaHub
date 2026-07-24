package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2
import org.jetbrains.compose.resources.painterResource

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
            CardName(
                name = discovery.label,
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
                            placeholder = painterResource(Res.drawable.mtg_card_back),
                            error = painterResource(Res.drawable.mtg_card_back),
                            fallback = painterResource(Res.drawable.mtg_card_back),
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

/**
 * Deck Builder v2 Phase 5 (plan §3.5) discovery row -- the [DeckDiscoveryV2] sibling of
 * [DiscoveryRow], rendered instead of it when `DeckFeatureFlags.DISCOVERIES_V2_ENABLED` is on.
 * Adds a dominant-colors mana-pip row (D11's "Build this" hands the SAME colors to the wizard) and
 * a real member count (never a bare cluster-size proxy — [DeckDiscoveryV2.memberCount] is the
 * ALREADY color-coherent count, computed by [com.mmg.manahub.feature.decks.domain.template
 * .DiscoverSynergiesV2UseCase]).
 *
 * @param rootCoordinates the Strategies tab's root [LayoutCoordinates] (forwarded from
 *   [InspirationsSheetContentV2]'s hosting `Box`), used so [SynergyCardTile] can report each
 *   tapped tile's on-screen [Rect] for the inline [com.mmg.manahub.core.ui.components
 *   .MagicCardInspectionOverlay] zoom -- Strategies-tab card taps inspect in place rather than
 *   navigating away (unlike the Combos tab, which opens the real card detail screen).
 * @param onCardTap reports the tapped [Card] + its [Rect] so the caller can drive the inline
 *   inspection overlay.
 * @param onBuildThis hands off to the v2 wizard, pre-filled with this discovery's strategy/tribe
 *   hint + dominant colors (D11) -- replaces [DiscoveryRow]'s seed-sheet handoff.
 */
@Composable
internal fun DiscoveryRowV2(
    discovery: DeckDiscoveryV2,
    rootCoordinates: LayoutCoordinates?,
    onCardTap: (Card, Rect) -> Unit,
    onBuildThis: () -> Unit,
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
            CardName(name = discovery.label, style = ty.titleMedium, color = mc.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(R.string.deck_studio_inspiration_fit, discovery.memberCount),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
                if (discovery.dominantColors.isNotEmpty()) {
                    Spacer(Modifier.width(spacing.xs))
                    // L1 (design review): xxs is exactly 2dp -- use the token, not the literal.
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        discovery.dominantColors.sortedBy { it.name }.forEach { color ->
                            ManaSymbolImage(token = color.symbol, size = 14.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(discovery.members.take(6), key = { it.scryfallId }) { card ->
                    SynergyCardTile(
                        card = card,
                        rootCoordinates = rootCoordinates,
                        onTap = onCardTap,
                    )
                }
            }

            Spacer(Modifier.height(spacing.sm))
            OutlinedButton(
                onClick = onBuildThis,
                modifier = Modifier.fillMaxWidth().heightIn(min = ArtTouchTarget),
                border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                shape = ButtonShape,
            ) {
                Text(text = stringResource(R.string.deck_wizard_build_this), style = ty.labelLarge, color = mc.primaryAccent)
            }
        }
    }
}

/** Full card-image tile size for [SynergyCardTile] (matches the established `CardPickerTile`
 * proportions: 90dp width at the standard 63:88 card aspect). */
private val SynergyTileWidth = 90.dp
private const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * The single shared image+magnifier tile used by BOTH the Strategies tab's "matching cards"
 * preview and each category's [DiscoveryRowV2] member row -- one visual source of truth so the
 * image/magnifier/rect-capture logic isn't duplicated between the two call sites.
 *
 * Mirrors [com.mmg.manahub.core.ui.components.CardPickerField]'s `CardPickerTile` rect-capture
 * pattern (`rootCoordinates.localBoundingBoxOf(coords)`) and [VariantSelectorSheet]'s
 * `VariantCardItem` magnifier-overlay visual (`Icons.Default.ZoomIn`, bottom-end corner) so
 * inline card inspection looks identical everywhere it appears in the app.
 *
 * @param rootCoordinates the ancestor `Box`'s coordinates the tapped rect is computed relative
 *   to (the SAME `Box` the inline inspection overlay is drawn within); `null` skips rect tracking
 *   (the tile stays tappable but reports [Rect.Zero]).
 * @param onTap reports the tapped [Card] and its on-screen [Rect].
 */
@Composable
internal fun SynergyCardTile(
    card: Card,
    rootCoordinates: LayoutCoordinates?,
    onTap: (Card, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    var tileRect by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier
            .heightIn(min = ArtTouchTarget)
            .width(SynergyTileWidth)
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .onGloballyPositioned { coords ->
                val root = rootCoordinates
                if (root != null && root.isAttached && coords.isAttached) {
                    tileRect = root.localBoundingBoxOf(coords)
                }
            }
            .clickable { onTap(card, tileRect) },
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = stringResource(R.string.deck_studio_inspiration_card_art),
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
        // Magnifier badge: a small circular scrim behind the icon for AA-safe contrast against
        // any card art regardless of the active MagicTheme palette -- same justified raw-Color
        // precedent as `VariantSelectorSheet.kt`'s `FullScreenImageViewer` scrim (decorative,
        // image-contextual chrome, not a themed content color).
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(sp.xxs)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * [SynergyCardTile] sibling for the Strategies-tab pickable-card row (visual-overhaul pass --
 * replaces the previous name-only `FilterChip` row, see `StrategiesTabContent` in
 * `DeckStudioScreen.kt`). Unlike [SynergyCardTile] (a single tap = zoom, selection lives
 * elsewhere on that screen), this tile needs TWO independent actions on one thumbnail: tapping
 * the card image toggles it in/out of [com.mmg.manahub.feature.decks.domain.template
 * .DiscoverySearchFilter]'s `selectedCardNames` search filter, while the small magnifier badge in
 * the bottom-end corner still opens the inline [com.mmg.manahub.core.ui.components
 * .MagicCardInspectionOverlay] like every other tile in this tab. The badge is a sibling `Box`
 * layered on top with its own `clickable`, per the established split-tap precedent
 * (`WizardCardImageTile` in `DeckWizardDirectionIdentity.kt`) -- a tap within its bounds is
 * consumed by the badge and never reaches the image's `clickable` underneath. [isSelected] draws
 * an accent border plus a top-start checkmark badge (mirrors [ComboRow.kt]'s `ComboCardTile`
 * highlighted-border precedent) since there is no longer a `FilterChip` to carry the selected
 * tint.
 */
@Composable
internal fun PickableSynergyCardTile(
    card: Card,
    isSelected: Boolean,
    rootCoordinates: LayoutCoordinates?,
    onToggleSelect: () -> Unit,
    onZoom: (Card, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    var tileRect by remember { mutableStateOf(Rect.Zero) }
    val zoomDescription = stringResource(R.string.deck_wizard_zoom_card, card.name)

    Box(
        modifier = modifier
            .heightIn(min = ArtTouchTarget)
            .width(SynergyTileWidth)
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .then(
                if (isSelected) Modifier.border(2.dp, mc.primaryAccent, CardShape) else Modifier
            )
            .onGloballyPositioned { coords ->
                val root = rootCoordinates
                if (root != null && root.isAttached && coords.isAttached) {
                    tileRect = root.localBoundingBoxOf(coords)
                }
            },
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable(onClick = onToggleSelect),
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(sp.xxs)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = mc.onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .clickable { onZoom(card, tileRect) }
                .semantics { contentDescription = zoomDescription },
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(sp.xxs)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
