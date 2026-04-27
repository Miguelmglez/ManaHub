package com.mmg.manahub.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.mmg.manahub.core.ui.components.MagicBottomBar
import com.mmg.manahub.feature.addcard.AddCardScreen
import com.mmg.manahub.feature.carddetail.CardDetailScreen
import com.mmg.manahub.feature.collection.CollectionScreen
import com.mmg.manahub.feature.decks.DeckMagicDetailScreen
import com.mmg.manahub.feature.decks.DeckMagicScreen
import com.mmg.manahub.feature.decks.improvement.DeckImprovementScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftScreen
import com.mmg.manahub.feature.draft.presentation.ui.SetDraftDetailScreen
import com.mmg.manahub.feature.friends.presentation.FriendsScreen
import com.mmg.manahub.feature.game.GamePlayScreen
import com.mmg.manahub.feature.trades.presentation.TradesSharedListScreen
import com.mmg.manahub.feature.game.GameSetupScreen
import com.mmg.manahub.feature.game.GameSetupViewModel
import com.mmg.manahub.feature.game.GameViewModel
import com.mmg.manahub.feature.game.PlayerConfig
import com.mmg.manahub.feature.game.model.GameMode
import com.mmg.manahub.feature.game.model.LayoutTemplate
import com.mmg.manahub.feature.game.model.LayoutTemplates
import com.mmg.manahub.feature.news.presentation.NewsScreen
import com.mmg.manahub.feature.news.presentation.NewsSourcesSettingsScreen
import com.mmg.manahub.feature.profile.ProfileScreen
import com.mmg.manahub.feature.scanner.ScannerScreen
import com.mmg.manahub.feature.settings.SettingsScreen
import com.mmg.manahub.feature.stats.StatsScreen
import com.mmg.manahub.feature.survey.SurveyScreen
import com.mmg.manahub.feature.tagdictionary.TagDictionaryScreen
import com.mmg.manahub.feature.tournament.TournamentListScreen
import com.mmg.manahub.feature.tournament.TournamentScreen
import com.mmg.manahub.feature.tournament.TournamentSetupScreen
import com.mmg.manahub.feature.tournament.TournamentViewModel

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
    val activity = LocalContext.current as ComponentActivity
    // Activity-scoped so game state persists across all navigation
    val gameVm: GameViewModel = hiltViewModel(activity)
    val gameUiState by gameVm.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    var pendingPlayerConfigs by remember { mutableStateOf<List<PlayerConfig>?>(null) }
    var pendingLayout by remember { mutableStateOf<LayoutTemplate?>(null) }
    var pendingTournamentMatchId by remember { mutableStateOf<Long?>(null) }
    var pendingTournamentId by remember { mutableStateOf<Long?>(null) }
    var pendingTournamentPlayers by remember { mutableStateOf<List<Long>>(emptyList()) }
    var pendingTournamentMode by remember { mutableStateOf<GameMode?>(null) }

    // hasActiveGame: true only while a game is actively running (not finished).
    // Stays true when the game is abandoned temporarily, allowing resume from Play FAB.
    val hasActiveGame = gameUiState.isGameRunning

    Scaffold(
        bottomBar = {
            Column {
                if (currentRoute in bottomBarRoutes) {
                    MagicBottomBar(
                        currentRoute = currentRoute,
                        onCollectionClick = { navController.navigateTab(Screen.Collection.route) },
                        onNewsClick = { navController.navigateTab(Screen.News.route) },
                        onPlayClick = {
                            if (hasActiveGame) {
                                navController.navigate(
                                    Screen.GamePlay.createRoute(
                                        gameUiState.mode.name,
                                        gameUiState.players.size,
                                    )
                                ) { launchSingleTop = true }
                            } else {
                                navController.navigate(Screen.GameSetup.route)
                            }
                        },
                        onDraftClick = { navController.navigateTab(Screen.Draft.route) },
                        onProfileClick = { navController.navigateTab(Screen.Profile.route) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Collection.route,
            modifier = modifier.padding(padding),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 5 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 5 } },
        ) {

            // ── Collection ────────────────────────────────────────────────────
            composable(Screen.Collection.route) {
                CollectionScreen(
                    onCardClick = { id ->
                        navController.navigate(
                            Screen.CollectionCardDetail.createRoute(
                                id
                            )
                        )
                    },
                    onScannerClick = { navController.navigate(Screen.CollectionAddCard.route) },
                    onDeckClick = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) }
                )
            }

            // Add card (tabbed: text search + scanner link)
            composable(Screen.CollectionAddCard.route) {
                AddCardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate(Screen.CollectionScanner.route) },
                    onNavigateToCardDetail = { scryfallId ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(scryfallId))
                    },
                )
            }

            composable(Screen.CollectionScanner.route) {
                ScannerScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.CollectionCardDetail.route,
                arguments = listOf(navArgument("scryfallId") { type = NavType.StringType }),
            ) {
                CardDetailScreen(
                    onBack              = { navController.popBackStack() },
                    onNavigateToAddCard = { navController.navigate(Screen.CollectionAddCard.route) },
                    onNavigateToDeck    = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                )
            }

            // ── Decks ─────────────────────────────────────────────────────────
            composable(
                route = Screen.DeckDetail.route,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
            ) {
                DeckMagicDetailScreen(
                    onBack = { navController.popBackStack() },
                    onImproveDeck = { id ->
                        navController.navigate(Screen.DeckImprovement.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.DeckImprovement.route,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
            ) {
                DeckImprovementScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.DeckAddCards.route,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
            ) {
                //   DeckAddCardsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.DeckBuilder.route) {
                DeckMagicScreen()
            }

            composable(Screen.Synergy.route) {
                DeckMagicScreen()
            }

            // ── Stats ─────────────────────────────────────────────────────────
            composable(Screen.Stats.route) {
                StatsScreen(
                    onCardClick = { id ->
                        navController.navigate(
                            Screen.CollectionCardDetail.createRoute(
                                id
                            )
                        )
                    },
                )
            }

            // ── Settings ──────────────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageNewsSources = { navController.navigate(Screen.NewsSourcesSettings.route) },
                    onManageTagDictionary = { navController.navigate(Screen.TagDictionary.route) },
                )
            }

            composable(Screen.TagDictionary.route) {
                TagDictionaryScreen(onBack = { navController.popBackStack() })
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
                ProfileScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onStatsClick = { navController.navigate(Screen.Stats.route) },
                    onFriendsClick = { navController.navigate(Screen.FriendsList.route) },
                )
            }

            composable(Screen.FriendsList.route) {
                FriendsScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Trades shared list (deep link) ────────────────────────────────
            composable(
                route     = Screen.TradesSharedList.route,
                arguments = listOf(navArgument("shareId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "https://trades.manahub.app/list/{shareId}"
                    },
                ),
            ) {
                TradesSharedListScreen(onBack = { navController.popBackStack() })
            }

            // ── Tournament flow ────────────────────────────────────────────────
            composable(Screen.TournamentList.route) {
                TournamentListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreateTournament = { navController.navigate(Screen.TournamentSetup.route) },
                    onOpenTournament = { id ->
                        navController.navigate(
                            Screen.TournamentDetail.route(
                                id
                            )
                        )
                    },
                )
            }

            composable(Screen.TournamentSetup.route) {
                TournamentSetupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onTournamentCreated = { id ->
                        navController.navigate(Screen.TournamentDetail.route(id)) {
                            popUpTo(Screen.TournamentSetup.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Screen.TournamentDetail.route,
                arguments = listOf(navArgument("tournamentId") { type = NavType.LongType }),
            ) { entry ->
                val tournamentId = entry.arguments?.getLong("tournamentId") ?: 0L
                val tournamentVm: TournamentViewModel = hiltViewModel(entry)
                TournamentScreen(
                    tournamentId = tournamentId,
                    onNavigateBack = { navController.popBackStack() },
                    onStartMatch = { matchId, tId ->
                        val (playerIds, configs) = tournamentVm.buildPlayerConfigsForMatch(matchId)
                        if (configs.isNotEmpty()) {
                            val mode = tournamentVm.getGameMode()
                            val layout = LayoutTemplates.getDefaultLayout(configs.size)
                            pendingPlayerConfigs = configs
                            pendingLayout = layout
                            pendingTournamentMatchId = matchId
                            pendingTournamentId = tId
                            pendingTournamentPlayers = playerIds
                            pendingTournamentMode = mode
                            navController.navigate(
                                Screen.GamePlay.createRoute(
                                    mode.name,
                                    configs.size
                                )
                            )
                        }
                    },
                    viewModel = tournamentVm,
                )
            }

            // ── Game flow ─────────────────────────────────────────────────────
            composable(Screen.GameSetup.route) {
                val setupVm: GameSetupViewModel = hiltViewModel()
                GameSetupScreen(
                    viewModel = setupVm,
                    onBack = { navController.popBackStack() },
                    onStartGame = { mode, configs, layout ->
                        pendingPlayerConfigs = configs
                        pendingLayout = layout
                        pendingTournamentMatchId = null
                        pendingTournamentId = null
                        pendingTournamentPlayers = emptyList()
                        pendingTournamentMode = null
                        navController.navigate(
                            Screen.GamePlay.createRoute(
                                mode.name,
                                configs.size
                            )
                        ) {
                            popUpTo(Screen.GameSetup.route) { inclusive = true }
                        }
                    },
                    onNavigateToTournament = { navController.navigate(Screen.TournamentSetup.route) },
                )
            }

            composable(
                route = Screen.GamePlay.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("playerCount") { type = NavType.IntType },
                ),
            ) { entry ->
                val configs = pendingPlayerConfigs
                val matchId = pendingTournamentMatchId
                val routeMode = entry.arguments?.getString("mode")?.let { name ->
                    try { GameMode.valueOf(name) } catch (e: Exception) { GameMode.STANDARD }
                } ?: GameMode.STANDARD

                LaunchedEffect(configs) {
                    if (configs != null) {
                        if (matchId != null) {
                            gameVm.initFromTournamentMatch(
                                matchId = matchId,
                                tournamentId = pendingTournamentId ?: 0L,
                                tournamentPlayerIds = pendingTournamentPlayers,
                                configs = configs,
                                mode = pendingTournamentMode ?: routeMode,
                                layout = pendingLayout,
                            )
                            pendingTournamentMatchId = null
                            pendingTournamentId = null
                            pendingTournamentPlayers = emptyList()
                            pendingTournamentMode = null
                        } else {
                            gameVm.initFromConfigs(configs, routeMode, pendingLayout)
                        }
                        pendingPlayerConfigs = null
                        pendingLayout = null
                    }
                }
                // Re-read from ViewModel state to get most up-to-date tournament context
                val tournamentId = gameUiState.activeTournamentId
                GamePlayScreen(
                    viewModel = gameVm,
                    onTournamentClick = tournamentId?.let { tId ->
                        { navController.navigate(Screen.TournamentDetail.route(tId)) }
                    },
                    onNewGame = {
                        navController.navigate(Screen.GameSetup.route) {
                            popUpTo(Screen.GamePlay.route) { inclusive = true }
                        }
                    },
                    onBackHome = {
                        // Finish the current game and return to setup
                        gameVm.finishGame()
                        navController.navigate(Screen.GameSetup.route) {
                            popUpTo(Screen.Collection.route) { inclusive = false }
                        }
                    },
                    onAbandonGame = {
                        // Abandon temporarily: preserve game state so Play FAB can resume it
                        navController.navigate(Screen.Collection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onExitGame = {
                        gameVm.finishGame()
                        navController.navigate(Screen.GameSetup.route) {
                            popUpTo(Screen.Collection.route) { inclusive = false }
                        }
                    },
                    onSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId))
                    },
                )
            }
            // ── Post-game survey ──────────────────────────────────────────────
            composable(
                route = Screen.GameSurvey.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { backStack ->
                val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
                val gameResult = gameUiState.gameResult

                if (gameResult != null) {
                    SurveyScreen(
                        sessionId = sessionId,
                        gameResult = gameResult,
                        onComplete = {
                            gameVm.finishGame()
                            navController.navigate(Screen.GameSetup.route) {
                                popUpTo(Screen.Collection.route) { inclusive = false }
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
        restoreState = true
    }
}


