package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.core.model.QuickStartAction

/**
 * All user intents emitted by the Home screen.
 *
 * Navigation is resolved at the [com.mmg.manahub.app.navigation.AppNavGraph] level;
 * data mutations (Quick Start order, nudge dismissal) are handled by
 * [HomeViewModel]. The screen itself stays stateless and free of navigation logic.
 */
sealed interface HomeAction {
    object StartGame : HomeAction
    object ScanCard : HomeAction
    object CreateDeck : HomeAction
    object DraftGuide : HomeAction
    object DraftSimulator : HomeAction
    object SearchCard : HomeAction
    object OpenLibrary : HomeAction
    object OpenDecks : HomeAction
    object OpenNews : HomeAction
    object OpenStats : HomeAction
    object OpenFriends : HomeAction
    object OpenTrades : HomeAction
    object OpenCommunityDecks : HomeAction

    /**
     * Opens a specific community deck's detail NATIVELY (Home widget board overhaul, TASK 5b/5c) —
     * replaces the old behaviour of opening an Archidekt deck URL in the system browser.
     */
    data class OpenCommunityDeck(val archidektId: Int) : HomeAction

    /**
     * Switches the Home COMMUNITY_DECKS widget's category and persists the choice (Home widget
     * board overhaul, TASK 5b). Handled in [HomeViewModel].
     */
    data class SelectCommunityDecksCategory(val category: HomeCommunityDeckCategory) : HomeAction
    object OpenTournaments : HomeAction
    object OpenSettings : HomeAction
    object OpenProfile : HomeAction

    /** Improve Your Game section. */
    object PlaytestRecentDeck : HomeAction
    object ImproveRecentDeck : HomeAction

    /** Open the Quick Start customization sheet (handled inside HomeScreen). */
    object CustomizeQuickStart : HomeAction

    /** Persist a newly chosen set of exactly four Quick Start actions. */
    data class SaveQuickStart(val actions: List<QuickStartAction>) : HomeAction

    object CreateAccount : HomeAction
    object DismissAccountNudge : HomeAction

    /**
     * Marks a first-step carousel slide as skipped by the user.
     * Persisted in DataStore via [HomeViewModel.skipFirstStep].
     *
     * @param stepId The [FirstStepItem.id] of the step to skip.
     */
    data class SkipFirstStep(val stepId: String) : HomeAction

    // ── Widget board ────────────────────────────────────────────────────────────
    object OpenWidgetGallery : HomeAction
    data class MoveWidget(val from: Int, val to: Int) : HomeAction
    data class UpdateLayout(val layout: List<WidgetInstance>) : HomeAction
    data class AddWidget(val type: HomeWidgetType) : HomeAction
    data class RemoveWidget(val type: HomeWidgetType) : HomeAction
    object ResetLayout : HomeAction

    /** Opens the platform store listing so the user can rate the app (UI resolves the deep link). */
    object RateApp : HomeAction

    /**
     * Retries the Discover cards fetch after a failure.
     * Handled in [HomeViewModel]: resets the discover load state to Loading and re-runs the fetch.
     */
    object RetryDiscover : HomeAction

    /**
     * Re-fetches a fresh single random card for the Random card widget, on demand.
     * Always issues a new Scryfall request (no once-guard). Handled in [HomeViewModel].
     */
    object RefreshRandomCard : HomeAction

    /**
     * Re-fetches the Discover cards row, on demand. Forces a new Scryfall request even when
     * cards are already loaded (bypasses the lazy once-guard). Handled in [HomeViewModel].
     */
    object RefreshDiscover : HomeAction

    /**
     * Scopes the Discover cards row to a single MTG set (or clears the filter when null).
     * Re-fetches the row for the new scope. Handled in [HomeViewModel].
     *
     * @param set the set to scope to, or null to clear the filter and use the default random query.
     */
    data class SelectDiscoverSet(val set: com.mmg.manahub.core.model.MagicSet?) : HomeAction

    /**
     * Resets the persisted News filters (languages/types/sources) back to their defaults
     * (English-only). Handled in [HomeViewModel] via DataStore.
     */
    object ResetNewsFilters : HomeAction

    // ── Per-widget navigation intents (resolved by AppNavGraph) ─────────────────
    object OpenDraftSimulator : HomeAction
    object OpenDraftGuide : HomeAction
    object OpenWishlist : HomeAction
    object OpenAchievements : HomeAction

    /** Opens the Profile screen on the Quests tab (gamification Phase 2). */
    object OpenProfileQuests : HomeAction

    /** Opens the Daily Puzzle screen (ADR-006), from either the widget's title/CTA. */
    object OpenDailyPuzzle : HomeAction
    data class OpenCardDetail(val scryfallId: String, val sharedTransitionKey: String? = null) : HomeAction
    data class OpenDeck(val deckId: String) : HomeAction

    /** Open a specific news article or video URL in the system browser. */
    data class OpenNewsUrl(val url: String) : HomeAction

    /** Open the full draft guide detail for a specific set. */
    data class OpenDraftSetDetail(val set: com.mmg.manahub.core.model.DraftSet) : HomeAction

    /**
     * Navigates into the Deck Playtest setup screen for [deckId], or the deck list when
     * [deckId] is null (no decks yet). Resolved from [PlaytestRecentDeck] inside [HomeScreen]'s
     * stateful entry point (the only place with access to [HomeUiState.decks]) — the static
     * [FirstStepItem] catalog cannot bake a dynamic deck id into its declarative action, so
     * [PlaytestRecentDeck] stays a payload-less trigger and this carries the resolved id.
     */
    data class NavigatePlaytest(val deckId: String?) : HomeAction
}
