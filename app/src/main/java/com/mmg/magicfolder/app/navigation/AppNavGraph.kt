package com.mmg.magicfolder.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mmg.magicfolder.feature.game.GameSetupViewModel
import com.mmg.magicfolder.feature.game.PlayerConfig
import com.mmg.magicfolder.core.ui.components.MagicBottomBar
import com.mmg.magicfolder.feature.addcard.AddCardScreen
import com.mmg.magicfolder.feature.carddetail.CardDetailScreen
import com.mmg.magicfolder.feature.collection.CollectionScreen
import com.mmg.magicfolder.feature.decks.DeckBuilderScreen
import com.mmg.magicfolder.feature.decks.DeckDetailScreen
import com.mmg.magicfolder.feature.game.GamePlayScreen
import com.mmg.magicfolder.feature.game.GameSetupScreen
import com.mmg.magicfolder.feature.game.GameViewModel
import com.mmg.magicfolder.feature.survey.SurveyScreen
import com.mmg.magicfolder.feature.profile.ProfileScreen
import com.mmg.magicfolder.feature.scanner.ScannerScreen
import com.mmg.magicfolder.feature.settings.SettingsScreen
import com.mmg.magicfolder.feature.stats.StatsScreen
import com.mmg.magicfolder.feature.tournament.TournamentListScreen
import com.mmg.magicfolder.feature.tournament.TournamentScreen
import com.mmg.magicfolder.feature.tournament.TournamentSetupScreen

// ═══════════════════════════════════════════════════════════════════════════════
//  Bottom-bar visibility rules
//  Visible on the three root tabs; hidden on all detail / game / scanner flows.
// ═══════════════════════════════════════════════════════════════════════════════

private val bottomBarRoutes = setOf(
    Screen.Collection.route,
    Screen.Profile.route,
)

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var pendingPlayerConfigs by remember { mutableStateOf<List<PlayerConfig>?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                MagicBottomBar(
                    currentRoute      = currentRoute,
                    onCollectionClick = { navController.navigateTab(Screen.Collection.route) },
                    onPlayClick       = { navController.navigate(Screen.GameSetup.route) },
                    onProfileClick    = { navController.navigateTab(Screen.Profile.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController      = navController,
            startDestination   = Screen.Collection.route,
            modifier           = modifier.padding(padding),
            enterTransition    = { fadeIn(tween(300)) + slideInHorizontally(tween(300))  { it / 5 } },
            exitTransition     = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition  = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 5 } },
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
                    onNavigateBack      = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate(Screen.CollectionScanner.route) },
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
                    onCardClick      = { id -> navController.navigate(Screen.CollectionCardDetail.createRoute(id)) },
                    onSettingsClick  = { navController.navigate(Screen.Settings.route) },
                )
            }

            // ── Settings ──────────────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            // ── Profile ───────────────────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen()
            }

            // ── Tournament flow ────────────────────────────────────────────────
            composable(Screen.TournamentList.route) {
                TournamentListScreen(
                    onNavigateBack     = { navController.popBackStack() },
                    onCreateTournament = { navController.navigate(Screen.TournamentSetup.route) },
                    onOpenTournament   = { id -> navController.navigate(Screen.TournamentDetail.route(id)) },
                )
            }

            composable(Screen.TournamentSetup.route) {
                TournamentSetupScreen(
                    onNavigateBack      = { navController.popBackStack() },
                    onTournamentCreated = { id ->
                        navController.navigate(Screen.TournamentDetail.route(id)) {
                            popUpTo(Screen.TournamentSetup.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route     = Screen.TournamentDetail.route,
                arguments = listOf(navArgument("tournamentId") { type = NavType.LongType }),
            ) { entry ->
                TournamentScreen(
                    tournamentId   = entry.arguments?.getLong("tournamentId") ?: 0L,
                    onNavigateBack = { navController.popBackStack() },
                    onStartMatch   = { matchId, tId ->
                        navController.navigate(Screen.GameSetup.route)
                    },
                )
            }

            // ── Game flow ─────────────────────────────────────────────────────
            composable(Screen.GameSetup.route) {
                val setupVm: GameSetupViewModel = hiltViewModel()
                GameSetupScreen(
                    viewModel = setupVm,onBack               = { navController.popBackStack() },
                    onStartGame          = { mode, configs ->
                        pendingPlayerConfigs = configs
                        navController.navigate(Screen.GamePlay.createRoute(mode.name, configs.size)) {
                            popUpTo(Screen.GameSetup.route) { inclusive = true }
                        }
                    },
                    onNavigateToTournament = { navController.navigate(Screen.TournamentSetup.route) },
                )
            }

            composable(
                route     = Screen.GamePlay.route,
                arguments = listOf(
                    navArgument("mode")        { type = NavType.StringType },
                    navArgument("playerCount") { type = NavType.IntType    },
                ),
            ) {
                val gameVm: GameViewModel = hiltViewModel()
                val configs = pendingPlayerConfigs
                LaunchedEffect(Unit) {
                    if (configs != null) {
                        gameVm.initFromConfigs(configs)
                        pendingPlayerConfigs = null
                    }
                }
                GamePlayScreen(
                    viewModel  = gameVm,
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
                    onSurvey   = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId))
                    },
                )
            }
            // ── Post-game survey ──────────────────────────────────────────────
            composable(
                route     = Screen.GameSurvey.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { backStack ->
                val sessionId  = backStack.arguments?.getLong("sessionId") ?: 0L
                val gameEntry  = remember(backStack) {
                    navController.getBackStackEntry(Screen.GamePlay.route)
                }
                val gameVm: GameViewModel = hiltViewModel(gameEntry)
                val gameUiState by gameVm.uiState.collectAsStateWithLifecycle()
                val gameResult = gameUiState.gameResult

                if (gameResult != null) {
                    SurveyScreen(
                        sessionId  = sessionId,
                        gameResult = gameResult,
                        onComplete = {
                            navController.navigate(Screen.Collection.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
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
