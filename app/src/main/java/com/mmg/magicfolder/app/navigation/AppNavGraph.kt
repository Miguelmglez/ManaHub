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
import com.mmg.magicfolder.feature.game.model.LayoutTemplate
import com.mmg.magicfolder.feature.game.model.LayoutTemplates
import com.mmg.magicfolder.core.ui.components.MagicBottomBar
import com.mmg.magicfolder.feature.addcard.AddCardScreen
import com.mmg.magicfolder.feature.carddetail.CardDetailScreen
import com.mmg.magicfolder.feature.collection.CollectionScreen
import com.mmg.magicfolder.feature.decks.DeckBuilderScreen
import com.mmg.magicfolder.feature.decks.DeckDetailScreen
import com.mmg.magicfolder.feature.draft.presentation.ui.DraftScreen
import com.mmg.magicfolder.feature.draft.presentation.ui.SetDraftDetailScreen
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
import com.mmg.magicfolder.feature.news.presentation.NewsScreen
import com.mmg.magicfolder.feature.news.presentation.NewsSourcesSettingsScreen
import com.mmg.magicfolder.feature.tournament.TournamentSetupScreen

// ═══════════════════════════════════════════════════════════════════════════════
//  Bottom-bar visibility rules
//  Visible on the four root tabs; hidden on all detail / game / scanner flows.
// ═══════════════════════════════════════════════════════════════════════════════

private val bottomBarRoutes = setOf(
    Screen.Collection.route,
    Screen.News.route,
    Screen.Draft.route,
    Screen.Profile.route,
)

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var pendingPlayerConfigs by remember { mutableStateOf<List<PlayerConfig>?>(null) }
    var pendingLayout        by remember { mutableStateOf<LayoutTemplate?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                MagicBottomBar(
                    currentRoute      = currentRoute,
                    onCollectionClick = { navController.navigateTab(Screen.Collection.route) },
                    onNewsClick       = { navController.navigateTab(Screen.News.route) },
                    onPlayClick       = { navController.navigate(Screen.GameSetup.route) },
                    onDraftClick      = { navController.navigateTab(Screen.Draft.route) },
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
                DeckBuilderScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onDeckSaved    = {
                        navController.navigate(Screen.Collection.route) {
                            popUpTo(Screen.DeckBuilder.route) { inclusive = true }
                        }
                    },
                )
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
                SettingsScreen(
                    onBack              = { navController.popBackStack() },
                    onManageNewsSources = { navController.navigate(Screen.NewsSourcesSettings.route) },
                )
            }

            // ── News ──────────────────────────────────────────────────────────
            composable(Screen.News.route) {
                NewsScreen()
            }

            composable(Screen.NewsSourcesSettings.route) {
                NewsSourcesSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Draft ─────────────────────────────────────────────────────────
            composable(Screen.Draft.route) {
                DraftScreen(
                    onSetClick = { setCode, setName, iconUri, releasedAt ->
                        navController.navigate(
                            Screen.DraftSetDetail.createRoute(setCode, setName, iconUri, releasedAt)
                        )
                    },
                )
            }

            composable(
                route = Screen.DraftSetDetail.route,
                arguments = listOf(
                    navArgument("setCode") { type = NavType.StringType },
                    navArgument("setName") { type = NavType.StringType; defaultValue = "" },
                    navArgument("setIconUri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("setReleasedAt") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                SetDraftDetailScreen(
                    onBack = { navController.popBackStack() },
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                )
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
                    onStartGame          = { mode, configs, layout ->
                        pendingPlayerConfigs = configs
                        pendingLayout        = layout
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
                val layout  = pendingLayout
                LaunchedEffect(Unit) {
                    if (configs != null) {
                        gameVm.initFromConfigs(configs, layout)
                        pendingPlayerConfigs = null
                        pendingLayout        = null
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
