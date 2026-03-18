package com.mmg.magicfolder.app.navigation

sealed class Screen(val route: String) {
    data object Collection  : Screen("collection")
    data object Scanner     : Screen("scanner")
    data object Stats       : Screen("stats")
    data object Synergy     : Screen("synergy")
    data object DeckBuilder : Screen("deck_builder")

    data object CardDetail : Screen("card_detail/{scryfallId}") {
        fun createRoute(scryfallId: String) = "card_detail/$scryfallId"
    }
}