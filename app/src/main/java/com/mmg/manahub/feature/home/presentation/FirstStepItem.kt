package com.mmg.manahub.feature.home.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.mmg.manahub.R

/**
 * Represents a single onboarding step shown in the First Steps carousel on the Home dashboard.
 *
 * @param id        Stable string key used to persist the skipped state in DataStore.
 * @param titleRes  String resource for the slide title.
 * @param subtitleRes String resource for the slide subtitle/description.
 * @param icon      Icon rendered at the top of the slide.
 * @param action    The [HomeAction] fired when the user taps the CTA button.
 */
data class FirstStepItem(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val action: HomeAction,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Step ID constants — used as DataStore persistence keys and as stable keys
//  in the carousel. Keep these in sync with the condition table in HomeViewModel.
// ─────────────────────────────────────────────────────────────────────────────

const val STEP_FIRST_ADD_CARD        = "first_add_card"
const val STEP_FIRST_SCAN_CARD       = "first_scan_card"
const val STEP_FIRST_CREATE_ACCOUNT  = "first_create_account"
const val STEP_FIRST_CREATE_DECK     = "first_create_deck"
const val STEP_FIRST_PLAYTEST_DECK   = "first_playtest_deck"
const val STEP_FIRST_ADD_FRIEND      = "first_add_friend"
const val STEP_FIRST_REVIEW_FRIEND   = "first_review_friend"
const val STEP_FIRST_PLAY_GAME       = "first_play_game"
const val STEP_FIRST_COLLECTION_STATS = "first_collection_stats"
const val STEP_FIRST_DRAFT_GUIDE     = "first_draft_guide"
const val STEP_FIRST_NEWS            = "first_news"
const val STEP_FIRST_CREATE_TRADE    = "first_create_trade"
const val STEP_FIRST_ADD_WISHLIST    = "first_add_wishlist"
const val STEP_FIRST_OPEN_FOR_TRADE  = "first_open_for_trade"
const val STEP_FIRST_PREFERENCES     = "first_preferences"
const val STEP_FIRST_COMPLETE_PROFILE = "first_complete_profile"
const val STEP_FIRST_RATE_APP        = "first_rate_app"

// ─────────────────────────────────────────────────────────────────────────────
//  Full ordered catalogue of all possible steps.
//
//  Show-conditions are evaluated in HomeViewModel.buildVisibleSteps() — not here.
//  This list is purely declarative: icon, copy, and CTA action per step.
// ─────────────────────────────────────────────────────────────────────────────

/** Ordered catalogue of all 17 possible first-step definitions. */
val ALL_FIRST_STEPS: List<FirstStepItem> = listOf(
    FirstStepItem(
        id = STEP_FIRST_ADD_CARD,
        titleRes = R.string.first_step_add_card_title,
        subtitleRes = R.string.first_step_add_card_subtitle,
        icon = Icons.Default.LibraryBooks,
        action = HomeAction.OpenLibrary,
    ),
    FirstStepItem(
        id = STEP_FIRST_SCAN_CARD,
        titleRes = R.string.first_step_scan_card_title,
        subtitleRes = R.string.first_step_scan_card_subtitle,
        icon = Icons.Default.QrCodeScanner,
        action = HomeAction.ScanCard,
    ),
    FirstStepItem(
        id = STEP_FIRST_CREATE_ACCOUNT,
        titleRes = R.string.first_step_create_account_title,
        subtitleRes = R.string.first_step_create_account_subtitle,
        icon = Icons.Default.AccountCircle,
        action = HomeAction.CreateAccount,
    ),
    FirstStepItem(
        id = STEP_FIRST_CREATE_DECK,
        titleRes = R.string.first_step_create_deck_title,
        subtitleRes = R.string.first_step_create_deck_subtitle,
        icon = Icons.Default.Style,
        action = HomeAction.CreateDeck,
    ),
    FirstStepItem(
        id = STEP_FIRST_PLAYTEST_DECK,
        titleRes = R.string.first_step_playtest_deck_title,
        subtitleRes = R.string.first_step_playtest_deck_subtitle,
        icon = Icons.Default.PlayArrow,
        action = HomeAction.OpenDecks,
    ),
    FirstStepItem(
        id = STEP_FIRST_ADD_FRIEND,
        titleRes = R.string.first_step_add_friend_title,
        subtitleRes = R.string.first_step_add_friend_subtitle,
        icon = Icons.Default.People,
        action = HomeAction.OpenFriends,
    ),
    FirstStepItem(
        id = STEP_FIRST_REVIEW_FRIEND,
        titleRes = R.string.first_step_review_friend_title,
        subtitleRes = R.string.first_step_review_friend_subtitle,
        icon = Icons.Default.Group,
        action = HomeAction.OpenFriends,
    ),
    FirstStepItem(
        id = STEP_FIRST_PLAY_GAME,
        titleRes = R.string.first_step_play_game_title,
        subtitleRes = R.string.first_step_play_game_subtitle,
        icon = Icons.Default.AutoAwesome,
        action = HomeAction.StartGame,
    ),
    FirstStepItem(
        id = STEP_FIRST_COLLECTION_STATS,
        titleRes = R.string.first_step_collection_stats_title,
        subtitleRes = R.string.first_step_collection_stats_subtitle,
        icon = Icons.Default.BarChart,
        action = HomeAction.OpenStats,
    ),
    FirstStepItem(
        id = STEP_FIRST_DRAFT_GUIDE,
        titleRes = R.string.first_step_draft_guide_title,
        subtitleRes = R.string.first_step_draft_guide_subtitle,
        icon = Icons.Default.MenuBook,
        action = HomeAction.DraftGuide,
    ),
    FirstStepItem(
        id = STEP_FIRST_NEWS,
        titleRes = R.string.first_step_news_title,
        subtitleRes = R.string.first_step_news_subtitle,
        icon = Icons.Default.Article,
        action = HomeAction.OpenNews,
    ),
    FirstStepItem(
        id = STEP_FIRST_CREATE_TRADE,
        titleRes = R.string.first_step_create_trade_title,
        subtitleRes = R.string.first_step_create_trade_subtitle,
        icon = Icons.Default.SwapHoriz,
        action = HomeAction.OpenTrades,
    ),
    FirstStepItem(
        id = STEP_FIRST_ADD_WISHLIST,
        titleRes = R.string.first_step_add_wishlist_title,
        subtitleRes = R.string.first_step_add_wishlist_subtitle,
        icon = Icons.Default.Favorite,
        action = HomeAction.OpenWishlist,
    ),
    FirstStepItem(
        id = STEP_FIRST_OPEN_FOR_TRADE,
        titleRes = R.string.first_step_open_for_trade_title,
        subtitleRes = R.string.first_step_open_for_trade_subtitle,
        icon = Icons.Default.SwapHoriz,
        action = HomeAction.OpenTrades,
    ),
    FirstStepItem(
        id = STEP_FIRST_PREFERENCES,
        titleRes = R.string.first_step_preferences_title,
        subtitleRes = R.string.first_step_preferences_subtitle,
        icon = Icons.Default.Settings,
        action = HomeAction.OpenSettings,
    ),
    FirstStepItem(
        id = STEP_FIRST_COMPLETE_PROFILE,
        titleRes = R.string.first_step_complete_profile_title,
        subtitleRes = R.string.first_step_complete_profile_subtitle,
        icon = Icons.Default.AccountCircle,
        action = HomeAction.OpenProfile,
    ),
    FirstStepItem(
        id = STEP_FIRST_RATE_APP,
        titleRes = R.string.first_step_rate_app_title,
        subtitleRes = R.string.first_step_rate_app_subtitle,
        icon = Icons.Default.Star,
        action = HomeAction.OpenSettings,
    ),
)
