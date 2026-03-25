package com.mmg.magicfolder.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mmg.magicfolder.core.ui.components.MagicBottomBar
import com.mmg.magicfolder.feature.addcard.AddCardScreen
import com.mmg.magicfolder.feature.carddetail.CardDetailScreen
import com.mmg.magicfolder.feature.collection.CollectionScreen
import com.mmg.magicfolder.feature.decks.DeckBuilderScreen
import com.mmg.magicfolder.feature.decks.DeckDetailScreen
import com.mmg.magicfolder.feature.game.GamePlayScreen
import com.mmg.magicfolder.feature.game.GameSetupScreen
import com.mmg.magicfolder.feature.profile.ProfileScreen
import com.mmg.magicfolder.feature.scanner.ScannerScreen
import com.mmg.magicfolder.feature.stats.StatsScreen

// ═══════════════════════════════════════════════════════════════════════════════
//  Bottom-bar visibility rules
//  Visible on the three root tabs; hidden on all detail / game / scanner flows.
// ═══════════════════════════════════════════════════════════════════════════════

private val bottomBarRoutes = setOf(
    Screen.Collection.route,
    Screen.Stats.route,
    Screen.Profile.route,
)

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                MagicBottomBar(
                    currentRoute      = currentRoute,
                    onCollectionClick = { navController.navigateTab(Screen.Collection.route) },
                    onStatsClick      = { navController.navigateTab(Screen.Stats.route) },
                    onPlayClick       = { navController.navigate(Screen.GameSetup.route) },
                    onProfileClick    = { navController.navigateTab(Screen.Profile.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Collection.route,
            modifier         = modifier.padding(padding),
        ) {

            // ── Collection ────────────────────────────────────────────────────
            composable(Screen.Collection.route) {
                CollectionScreen(
                    onCardClick       = { id -> navController.navigate(Screen.CollectionCardDetail.createRoute(id)) },
                    onScannerClick    = { navController.navigate(Screen.CollectionAddCard.route) },
                    onDeckClick       = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                    onCreateDeckClick = { navController.navigate(Screen.DeckBuilder.route) },
                )
            }

            // Add card (tabbed: text search + scanner link)
            composable(Screen.CollectionAddCard.route) {
                AddCardScreen(
                    onBack         = { navController.popBackStack() },
                    onScannerClick = { navController.navigate(Screen.CollectionScanner.route) },
                )
            }

            composable(Screen.CollectionScanner.route) {
                ScannerScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route     = Screen.CollectionCardDetail.route,
                arguments = listOf(navArgument("scryfallId") { type = NavType.StringType }),
            ) {
                CardDetailScreen(onBack = { navController.popBackStack() })
            }

            // ── Decks ─────────────────────────────────────────────────────────
            composable(
                route     = Screen.DeckDetail.route,
                arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
            ) { entry ->
                val deckId = entry.arguments?.getLong("deckId") ?: 0L
                DeckDetailScreen(
                    deckId = deckId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.DeckBuilder.route) {
                DeckBuilderScreen(onBack = { navController.popBackStack() })
            }

            // ── Stats ─────────────────────────────────────────────────────────
            composable(Screen.Stats.route) {
                StatsScreen(
                    onCardClick = { id -> navController.navigate(Screen.CollectionCardDetail.createRoute(id)) },
                )
            }

            // ── Profile ───────────────────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen()
            }

            // ── Game flow ─────────────────────────────────────────────────────
            composable(Screen.GameSetup.route) {
                GameSetupScreen(
                    onBack      = { navController.popBackStack() },
                    onStartGame = { mode, count ->
                        navController.navigate(Screen.GamePlay.createRoute(mode.name, count)) {
                            popUpTo(Screen.GameSetup.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route     = Screen.GamePlay.route,
                arguments = listOf(
                    navArgument("mode")        { type = NavType.StringType },
                    navArgument("playerCount") { type = NavType.IntType    },
                ),
            ) {
                GamePlayScreen(
                    onNewGame  = {
                        navController.navigate(Screen.GameSetup.route) {
                            popUpTo(Screen.GamePlay.route) { inclusive = true }
                        }
                    },
                    onBackHome = {
                        navController.navigate(Screen.Collection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Navigation helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Single-top navigation for bottom-bar tabs: saves state, avoids duplicates. */
private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}
