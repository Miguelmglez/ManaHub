package com.mmg.manahub.feature.home.presentation

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
    object OpenTournaments : HomeAction
    object OpenSettings : HomeAction
    object OpenProfile : HomeAction

    /** Improve Your Game section. */
    object PlaytestRecentDeck : HomeAction
    object ImproveRecentDeck : HomeAction

    /** Resume a specific Continue item. */
    data class ContinueItem(val item: com.mmg.manahub.feature.home.presentation.ContinueItem) : HomeAction

    /** Open the Quick Start customization sheet (handled inside HomeScreen). */
    object CustomizeQuickStart : HomeAction

    /** Persist a newly chosen set of exactly four Quick Start actions. */
    data class SaveQuickStart(val actions: List<QuickStartAction>) : HomeAction

    object CreateAccount : HomeAction
    object DismissAccountNudge : HomeAction
}
