package com.mmg.manahub.feature.home.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.mmg.manahub.core.ui.theme.Spacing
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/** Width of a card thumbnail in the Home card rows (Discover, Recently added, Wishlist). */
internal val HomeCardThumbWidth: Dp = 110.dp

/** Full MTG card aspect ratio (745:1040). */
internal const val HomeCardAspectRatio = 0.717f

/** Width of a reduced deck tile (Your decks, Community decks, Trending). */
internal val HomeDeckTileWidth: Dp = 160.dp

/** Width of a compact draft set tile (Latest sets). */
internal val HomeDraftSetTileWidth: Dp = 160.dp

/** Width of a news card (MTG News). */
internal val HomeNewsCardWidth: Dp = 220.dp

/** Minimum hub slide height; the real height grows with the font scale. */
internal val HubSlideMinHeight: Dp = 140.dp

/** Width of a trade-suggestion column; its thumbnail is squeezed to this width. */
internal val TradeSuggestionColumnWidth: Dp = 72.dp

private val QuickActionTileMinHeight = 84.dp
private val QuickActionIconSize = 28.dp
private val LevelBadgeSize = 48.dp
private val ProgressBarHeight = 6.dp
private val StreakIconSize = 14.dp
private val HeroIconBadgeSize = 64.dp
private val HeaderRowMinHeight = 48.dp
private val WinRateRingSize = 80.dp
private val KpiBadgeSize = 40.dp
private val StatKpiBadgeSize = 56.dp
private val ColorDotSize = 10.dp
private val CollectionRingSize = 140.dp
// Mirror DraftSetCard(isCompact = true): fixed 110dp frame, 10dp padding, 24dp set symbol.
private val DraftSetCompactMinHeight = 110.dp
private val DraftSetCompactPadding = 10.dp
private val DraftSetCompactIconSize = 24.dp
private val NewsMetaGap = 6.dp
private val DeckTileMetaIconSize = 14.dp
private val FriendAvatarSize = 56.dp
private val FriendChipIconSize = 18.dp
private val MinTouchTarget = 48.dp
private val CtaIconBox = 24.dp
private val InboxBadgeSize = 36.dp

// Absorbs sub-dp rounding of text line boxes so content never clips inside a fixed slot.
private val SlotSlack = 2.dp

/**
 * The typography line heights the Home size tokens depend on, already converted to dp (so they
 * include the user's font scale).
 */
@Immutable
data class HomeLineHeights(
    val titleMedium: Dp,
    val labelLarge: Dp,
    val labelMedium: Dp,
    val labelSmall: Dp,
    val bodyLarge: Dp,
    val bodyMedium: Dp,
    val bodySmall: Dp,
    val displayMedium: Dp,
)

/**
 * Fixed body heights of every Home widget. Each widget's loading skeleton, gated placeholder,
 * empty/error body and content all render inside this one height, so a state change can never
 * resize a board item (which would shift the scroll position and any shared-element bounds).
 *
 * @param bodyHeights body height per widget type; absent when the widget's geometry is defined by
 *   its own frame instead (CARD_OF_THE_DAY, RULES_TIP).
 */
@Immutable
data class HomeWidgetMetrics(
    val bodyHeights: Map<HomeWidgetType, Dp>,
    val cardThumbHeight: Dp,
    val deckTileHeight: Dp,
    val deckTileWithOwnerHeight: Dp,
    val draftSetTileHeight: Dp,
    val newsCardHeight: Dp,
    val gameStatsSlideHeight: Dp,
    val collectionSlideHeight: Dp,
    val tradesSlideHeight: Dp,
) {
    /** The fixed body height of [type], or null when the widget sizes itself by its own frame. */
    fun bodyHeight(type: HomeWidgetType): Dp? = bodyHeights[type]
}

/**
 * Computes every Home size token from the current line heights and spacing. Pure so it can be unit
 * tested at several font scales.
 */
fun computeHomeWidgetMetrics(lh: HomeLineHeights, sp: Spacing): HomeWidgetMetrics {
    val shell = sp.xxs * 2
    val cardThumb = HomeCardThumbWidth / HomeCardAspectRatio

    // DeckItem(reduced): 16:9 art + info column [name, owner?, spacer xs, meta row] spaced by xs.
    fun deckTile(withOwner: Boolean): Dp {
        val art = HomeDeckTileWidth * 9f / 16f
        val owner = if (withOwner) lh.labelSmall + sp.xs else 0.dp
        return art + sp.sm * 2 + lh.titleMedium + owner + sp.xs * 3 +
            max(lh.labelSmall, DeckTileMetaIconSize) + SlotSlack
    }
    val deckTile = deckTile(withOwner = false)
    val deckTileWithOwner = deckTile(withOwner = true)

    // Compact DraftSetCard: [24dp symbol row, 2-line titleMedium name, labelSmall meta row].
    val draftSetTile = max(
        DraftSetCompactMinHeight,
        DraftSetCompactPadding * 2 + DraftSetCompactIconSize + lh.titleMedium * 2 + lh.labelSmall + SlotSlack,
    )

    // NewsItemCard(VERTICAL, titleMinLines = 2): 16:9 thumbnail + padded [2-line title, gap, meta].
    val newsCard = HomeNewsCardWidth * 9f / 16f + sp.md * 2 + lh.bodyLarge * 2 + NewsMetaGap +
        lh.labelSmall + SlotSlack

    val gameStatsSlide = maxOf(
        HubSlideMinHeight,
        sp.md * 2 + KpiBadgeSize + sp.xs + lh.titleMedium + lh.labelSmall,
        WinRateRingSize + sp.sm * 2,
        sp.sm * 2 + lh.labelSmall + lh.titleMedium + max(lh.labelMedium, ColorDotSize),
        sp.sm * 2 + lh.labelSmall * 2 + lh.titleMedium,
        max(StatKpiBadgeSize, lh.displayMedium + lh.labelSmall),
    ) + SlotSlack

    val statBox = sp.md * 2 + lh.titleMedium + lh.labelSmall
    val collectionSlide = maxOf(HubSlideMinHeight, statBox * 2 + sp.sm, CollectionRingSize) + SlotSlack

    val tradeRow = max(MinTouchTarget, sp.xs * 2 + lh.labelSmall + lh.bodySmall)
    val tradesSlide = maxOf(
        HubSlideMinHeight,
        lh.labelMedium + sp.xs + tradeRow * 3 + sp.xs * 3,
        lh.labelMedium + sp.xs + TradeSuggestionColumnWidth / HomeCardAspectRatio + sp.xxs + lh.labelSmall,
        max(InboxBadgeSize, lh.titleMedium * 2 + lh.labelSmall),
    ) + SlotSlack

    val heroCarousel = shell + HeaderRowMinHeight + sp.xxs + sp.md * 2 + sp.xl * 2 +
        HeroIconBadgeSize + sp.md + lh.titleMedium + sp.xs + lh.bodySmall * 2 + SlotSlack

    val quickActionTile = max(QuickActionTileMinHeight, sp.xs * 2 + QuickActionIconSize + sp.xs + lh.labelSmall)
    val questRow = sp.sm * 2 + maxOf(lh.bodyMedium, lh.labelMedium + sp.xxs + ProgressBarHeight, lh.labelSmall)
    val friendsChip = sp.sm * 2 + max(FriendChipIconSize, lh.labelMedium)
    // MagicCtaButton: md vertical padding around a labelLarge line (or its 24dp icon box).
    val ctaButton = sp.md * 2 + max(lh.labelLarge, CtaIconBox)
    val singleCta = maxOf(lh.bodyMedium * 2, ctaButton, MinTouchTarget)

    val heights = mapOf(
        HomeWidgetType.CONTEXT_HERO to heroCarousel,
        HomeWidgetType.QUICK_ACTIONS to shell + quickActionTile * 2 + sp.xxs + SlotSlack,
        HomeWidgetType.PROGRESSION_HUB to shell +
            max(LevelBadgeSize, lh.titleMedium + sp.xs + ProgressBarHeight) + sp.xxs +
            sp.xs * 2 + max(lh.labelSmall, StreakIconSize) + SlotSlack,
        HomeWidgetType.QUESTS_HUB to shell + questRow * 3 + sp.sm * 2 + SlotSlack,
        HomeWidgetType.GAME_STATS_HUB to shell + gameStatsSlide,
        HomeWidgetType.COLLECTION_STATS_HUB to shell + collectionSlide,
        HomeWidgetType.YOUR_DECKS_SHELF to shell + deckTile,
        HomeWidgetType.RECENTLY_ADDED to shell + cardThumb + SlotSlack,
        HomeWidgetType.WISHLIST_PROGRESS to shell + lh.labelMedium + sp.xs + cardThumb + SlotSlack,
        HomeWidgetType.DISCOVER_CARDS to shell + cardThumb + SlotSlack,
        HomeWidgetType.LATEST_SETS to shell + draftSetTile,
        HomeWidgetType.MTG_NEWS to shell + newsCard,
        HomeWidgetType.TRADES_HUB to shell + tradesSlide,
        HomeWidgetType.FRIENDS to shell + friendsChip + sp.xxs + lh.labelSmall + sp.xxs +
            FriendAvatarSize + sp.xxs + lh.labelSmall + SlotSlack,
        HomeWidgetType.COMMUNITY_DECKS to shell + deckTileWithOwner,
        HomeWidgetType.TRENDING_COMMANDERS to shell + deckTile,
        HomeWidgetType.DAILY_PUZZLE to shell + singleCta + SlotSlack,
    )

    return HomeWidgetMetrics(
        bodyHeights = heights,
        cardThumbHeight = cardThumb,
        deckTileHeight = deckTile,
        deckTileWithOwnerHeight = deckTileWithOwner,
        draftSetTileHeight = draftSetTile,
        newsCardHeight = newsCard,
        gameStatsSlideHeight = gameStatsSlide,
        collectionSlideHeight = collectionSlide,
        tradesSlideHeight = tradesSlide,
    )
}

/** The Home size tokens for the current typography, spacing and font scale. */
@Composable
fun rememberHomeWidgetMetrics(): HomeWidgetMetrics {
    val density = LocalDensity.current
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    return remember(density, ty, sp) {
        with(density) {
            computeHomeWidgetMetrics(
                lh = HomeLineHeights(
                    titleMedium = ty.titleMedium.lineHeight.toDp(),
                    labelLarge = ty.labelLarge.lineHeight.toDp(),
                    labelMedium = ty.labelMedium.lineHeight.toDp(),
                    labelSmall = ty.labelSmall.lineHeight.toDp(),
                    bodyLarge = ty.bodyLarge.lineHeight.toDp(),
                    bodyMedium = ty.bodyMedium.lineHeight.toDp(),
                    bodySmall = ty.bodySmall.lineHeight.toDp(),
                    displayMedium = ty.displayMedium.lineHeight.toDp(),
                ),
                sp = sp,
            )
        }
    }
}
