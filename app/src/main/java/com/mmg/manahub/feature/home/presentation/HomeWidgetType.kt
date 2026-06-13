package com.mmg.manahub.feature.home.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.ui.graphics.vector.ImageVector
import com.mmg.manahub.R

/**
 * The catalog of every widget that can appear on the Home dashboard.
 *
 * Each entry is self-describing: a stable [persistedId] (decoupled from the enum
 * name so the enum can be renamed without breaking saved layouts), a default
 * title resource, the [WidgetSize]s it supports, its gallery [category], and its
 * [audience]. [isAlwaysPresent] marks widgets the user may not remove (the
 * context hero).
 *
 * After the dashboard consolidation, every widget renders at a single
 * [WidgetSize.MEDIUM] size — resizing was removed in favor of self-contained
 * "hub" widgets that aggregate what were previously many small cards.
 */
enum class HomeWidgetType(
    val persistedId: String,
    val defaultTitleRes: Int,
    val supportedSizes: Set<WidgetSize>,
    val category: WidgetCategory,
    val audience: WidgetAudience,
    val icon: ImageVector,
    val isAlwaysPresent: Boolean = false,
) {

    // ── Activity ────────────────────────────────────────────────────────────────
    CONTEXT_HERO(
        persistedId = "context_hero",
        defaultTitleRes = R.string.widget_title_context_hero,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.ACTIVITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.AutoAwesome,
        isAlwaysPresent = true,
    ),
    QUICK_ACTIONS(
        persistedId = "quick_actions",
        defaultTitleRes = R.string.widget_title_quick_actions,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.ACTIVITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.TipsAndUpdates,
    ),
    // ── Gamification (Phase 2) — hidden entirely when the master toggle is off ───
    PROGRESSION_HUB(
        persistedId = "progression_hub",
        defaultTitleRes = R.string.widget_title_progression_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.ACTIVITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.MilitaryTech,
    ),
    QUESTS_HUB(
        persistedId = "quests_hub",
        defaultTitleRes = R.string.widget_title_quests_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.ACTIVITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Flag,
    ),

    // ── Stats ───────────────────────────────────────────────────────────────────
    GAME_STATS_HUB(
        persistedId = "game_stats_hub",
        defaultTitleRes = R.string.widget_title_game_stats_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.STATS,
        audience = WidgetAudience.SIGNED_IN,
        icon = Icons.Default.BarChart,
    ),

    // ── Collection ────────────────────────────────────────────────────────────────
    COLLECTION_STATS_HUB(
        persistedId = "collection_stats_hub",
        defaultTitleRes = R.string.widget_title_collection_stats_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COLLECTION,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Layers,
    ),
    YOUR_DECKS_SHELF(
        persistedId = "your_decks_shelf",
        defaultTitleRes = R.string.widget_title_decks_shelf,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COLLECTION,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Style,
    ),
    WISHLIST_PROGRESS(
        persistedId = "wishlist_progress",
        defaultTitleRes = R.string.widget_title_wishlist,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COLLECTION,
        audience = WidgetAudience.ACCOUNT_GATED,
        icon = Icons.Default.AutoAwesome,
    ),

    // ── Discover ──────────────────────────────────────────────────────────────────
    DISCOVER_CARDS(
        persistedId = "discover_cards",
        defaultTitleRes = R.string.widget_title_discover,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Style,
    ),
    CARD_OF_THE_DAY(
        persistedId = "card_of_the_day",
        defaultTitleRes = R.string.widget_title_card_of_day,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.AutoAwesome,
    ),
    LATEST_SETS(
        persistedId = "latest_sets",
        defaultTitleRes = R.string.widget_title_latest_sets,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Layers,
    ),
    MTG_NEWS(
        persistedId = "mtg_news",
        defaultTitleRes = R.string.widget_title_news,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Feed,
    ),
    RULES_TIP(
        persistedId = "rules_tip",
        defaultTitleRes = R.string.widget_title_rules_tip,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.MenuBook,
    ),

    // ── Social ────────────────────────────────────────────────────────────────────
    SOCIAL_HUB(
        persistedId = "social_hub",
        defaultTitleRes = R.string.widget_title_social_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.SOCIAL,
        audience = WidgetAudience.ACCOUNT_GATED,
        icon = Icons.Default.Group,
    ),
    TRADES_HUB(
        persistedId = "trades_hub",
        defaultTitleRes = R.string.widget_title_trades_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.SOCIAL,
        audience = WidgetAudience.ACCOUNT_GATED,
        icon = Icons.Default.SwapHoriz,
    );

    /** True for widgets that belong to the gamification system (hidden when the toggle is off). */
    val isGamification: Boolean
        get() = this == PROGRESSION_HUB || this == QUESTS_HUB

    companion object {
        /** Resolves a persisted id back to its type, or null if unknown/removed. */
        fun fromPersistedId(id: String): HomeWidgetType? =
            entries.firstOrNull { it.persistedId == id }
    }
}
