package com.mmg.manahub.feature.home.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.ui.graphics.vector.ImageVector
import com.mmg.manahub.R
import com.mmg.manahub.core.model.WidgetSize

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
    /** Newest cards added to the local collection (Home feature overhaul Phase 2.1). Works
     * fully offline — no account required. */
    RECENTLY_ADDED(
        persistedId = "recently_added",
        defaultTitleRes = R.string.widget_title_recently_added,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COLLECTION,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.History,
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
    // SOCIAL_HUB was split (Home widget board overhaul, TASK 5) into FRIENDS + COMMUNITY_DECKS —
    // see the companion LEGACY_SOCIAL_HUB_PERSISTED_ID migration below (its persisted layout
    // token is expanded into both new widgets at decode time so no existing board goes empty).
    TRADES_HUB(
        persistedId = "trades_hub",
        defaultTitleRes = R.string.widget_title_trades_hub,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.SOCIAL,
        audience = WidgetAudience.ACCOUNT_GATED,
        icon = Icons.Default.SwapHoriz,
    ),
    /** Friend list + pending-request headline (Home widget board overhaul, TASK 5a — split off
     * SOCIAL_HUB's friends slide). */
    FRIENDS(
        persistedId = "friends",
        defaultTitleRes = R.string.widget_title_friends,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.SOCIAL,
        audience = WidgetAudience.ACCOUNT_GATED,
        icon = Icons.Default.Group,
    ),

    // ── Community (Deck Doctor Community/Archetype plan, Phase 5 + widget board overhaul TASK 5b) ─
    /** Top-3 trending commanders of the week; tap navigates into the Community Hub's Discover
     * section (reuses [HomeAction.OpenCommunityDecks] — the Hub lands on Discover by default when
     * `communityEngineEnabledFlow` is on). Silently hidden — never an error state — on Worker
     * failure or when the community engine is off; see [HomeViewModel.trendingFlow]. */
    TRENDING_COMMANDERS(
        persistedId = "trending_commanders",
        defaultTitleRes = R.string.widget_title_trending_commanders,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COMMUNITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Group,
    ),
    /** Browsable community decks (Archidekt), category-selectable — Home widget board overhaul,
     * TASK 5b. Works without an account (browsing is public); replaces SOCIAL_HUB's Archidekt
     * trending-deck slide with richer [com.mmg.manahub.core.ui.components.DeckItem] cards that
     * navigate NATIVELY into [com.mmg.manahub.app.navigation.Screen.CommunityDeckDetail] instead
     * of opening an external URL. */
    COMMUNITY_DECKS(
        persistedId = "community_decks_browse",
        defaultTitleRes = R.string.widget_title_community_decks,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COMMUNITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Style,
    ),

    // ── Daily Puzzle (ADR-006), Batch B2 ─────────────────────────────────────────
    /** Preview of today's Cardle-style guessing puzzle; tap navigates to
     * [com.mmg.manahub.app.navigation.Screen.DailyPuzzle]. Gallery-only/opt-in (NOT in either
     * default layout, matching [TRENDING_COMMANDERS]' board-length discipline) and deliberately
     * NOT gamification-gated ([isGamification] below stays a fixed allowlist of exactly
     * [PROGRESSION_HUB]/[QUESTS_HUB]) — the puzzle must stay playable with the gamification master
     * toggle off (ADR-005 Decision 1's "gate the backend, not just the UI" principle does not apply
     * here: the puzzle screen is not a gamification surface, it merely EMITS XP/streak progress on
     * solve, same as any other feature). */
    DAILY_PUZZLE(
        persistedId = "daily_puzzle",
        defaultTitleRes = R.string.widget_title_daily_puzzle,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.DISCOVER,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.Extension,
    ),

    // ── Competitive (Phase 5) ────────────────────────────────────────────────────
    /** Launcher tile into the Competitive screen (metagame rankings, 17lands Limited ratings,
     * event locator, Pro Tour news filter). Gallery-only/opt-in (NOT in either default layout,
     * matching [TRENDING_COMMANDERS]/[DAILY_PUZZLE]'s board-length discipline) and gated by the
     * `competitiveEnabledFlow` DataStore flag (reactive, unlike [DAILY_PUZZLE]'s compile-time
     * [com.mmg.manahub.feature.puzzle.presentation.PuzzleFeatureFlags] gate — see
     * [HomeViewModel.competitiveEnabledFlow], kept OUTSIDE [HomeUiState] for the same reason as
     * `trendingFlow`/`dailyPuzzleFlow`). */
    COMPETITIVE(
        persistedId = "competitive",
        defaultTitleRes = R.string.widget_title_competitive,
        supportedSizes = setOf(WidgetSize.MEDIUM),
        category = WidgetCategory.COMMUNITY,
        audience = WidgetAudience.ALL,
        icon = Icons.Default.EmojiEvents,
    );

   /* MULTI_CARD_ADD(
    persistedId = "multi_card_add",
    defaultTitleRes = R.string.widget_title_multi_card_add,
    supportedSizes = setOf(WidgetSize.MEDIUM),
    category = WidgetCategory.COLLECTION,
    audience = WidgetAudience.ALL,
    icon = Icons.Default.CollectionsBookmark,
    );*/

    /** True for widgets that belong to the gamification system (hidden when the toggle is off). */
    val isGamification: Boolean
        get() = this == PROGRESSION_HUB || this == QUESTS_HUB

    companion object {
        /** Resolves a persisted id back to its type, or null if unknown/removed. */
        fun fromPersistedId(id: String): HomeWidgetType? =
            entries.firstOrNull { it.persistedId == id }

        /**
         * The retired SOCIAL_HUB widget's old persisted id (Home widget board overhaul, TASK 5c).
         * A layout token with this id is expanded into [FRIENDS] + [COMMUNITY_DECKS] at decode
         * time — see `WidgetInstance.toInstancesWithMigration` — so an existing user's board never
         * silently loses the whole slot the way an unknown-id token would.
         */
        const val LEGACY_SOCIAL_HUB_PERSISTED_ID: String = "social_hub"
    }
}
