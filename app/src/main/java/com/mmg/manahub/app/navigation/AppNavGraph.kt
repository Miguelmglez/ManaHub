package com.mmg.manahub.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.mmg.manahub.R
import com.mmg.manahub.core.push.ForegroundScreenTracker
import com.mmg.manahub.core.push.PushDeeplinkRouter
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicBottomBar
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.feature.addcard.presentation.AddCardScreen
import com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen
import com.mmg.manahub.feature.collection.presentation.CollectionScreen
import com.mmg.manahub.feature.collection.presentation.CollectionTab
import com.mmg.manahub.feature.decks.presentation.DeckMagicDetailScreen
import com.mmg.manahub.feature.decks.presentation.DeckStudioScreen
import com.mmg.manahub.feature.decks.presentation.improvement.DeckImprovementScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftResultScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftSetupScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftingScreen
import com.mmg.manahub.feature.draft.presentation.ui.SetDraftDetailScreen
import com.mmg.manahub.feature.friends.presentation.FriendsScreen
import com.mmg.manahub.feature.friends.presentation.detail.FriendDetailScreen
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherScreen
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherViewModel
import com.mmg.manahub.feature.game.presentation.GamePlayScreen
import com.mmg.manahub.feature.game.presentation.GameSetupScreen
import com.mmg.manahub.feature.game.presentation.GameSetupViewModel
import com.mmg.manahub.feature.game.presentation.GameSettings
import com.mmg.manahub.feature.game.presentation.GameViewModel
import com.mmg.manahub.feature.game.presentation.PlayerConfig
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.home.presentation.HomeAction
import com.mmg.manahub.feature.home.presentation.HomeHeroState
import com.mmg.manahub.feature.home.presentation.HomeScreen
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import com.mmg.manahub.feature.game.domain.model.LayoutTemplates
import com.mmg.manahub.feature.news.presentation.NewsScreen
import com.mmg.manahub.feature.news.presentation.NewsSourcesSettingsScreen
import com.mmg.manahub.feature.news.presentation.VideoPlayerScreen
import com.mmg.manahub.feature.profile.presentation.ProfileScreen
import com.mmg.manahub.feature.profile.presentation.ProfileTab
import com.mmg.manahub.feature.scanner.presentation.ScannerScreen
import com.mmg.manahub.feature.settings.presentation.SettingsScreen
import com.mmg.manahub.feature.stats.presentation.StatsScreen
import com.mmg.manahub.feature.survey.presentation.SurveyScreen
import com.mmg.manahub.feature.tagdictionary.presentation.TagDictionaryScreen
import com.mmg.manahub.feature.tournament.presentation.TournamentListScreen
import com.mmg.manahub.feature.tournament.presentation.TournamentScreen
import com.mmg.manahub.feature.tournament.presentation.TournamentSetupScreen
import com.mmg.manahub.feature.tournament.presentation.TournamentViewModel
import com.mmg.manahub.feature.trades.presentation.CreateTradeProposalScreen
import com.mmg.manahub.feature.trades.presentation.TradeNegotiationDetailScreen
import com.mmg.manahub.feature.trades.presentation.TradesSharedListScreen
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.presentation.setup.PlaytestSetupScreen
import com.mmg.manahub.feature.playtest.presentation.hand.PlaytestHandScreen

// ═══════════════════════════════════════════════════════════════════════════════
//  Bottom-bar visibility rules
//  Visible on the two root tabs (Home + Library); hidden on all detail / game /
//  scanner flows and on screens now reached through Home (Draft, News, Profile…).
// ═══════════════════════════════════════════════════════════════════════════════

private val bottomBarRoutes = setOf(
    Screen.Home.route,
    Screen.Collection.route,
    Screen.DeckList.route,
    Screen.Trades.route,
)

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    isInPiP: Boolean = false,
) {
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current

    // Activity-scoped so game state persists across all navigation
    val gameVm: GameViewModel = hiltViewModel(activity)
    val gameUiState by gameVm.uiState.collectAsStateWithLifecycle()

    // Activity-scoped so it survives navigation and can process pending invite codes after login
    val inviteVm: InviteDispatcherViewModel = hiltViewModel(activity)

    val navController = rememberNavController()

    // Bridge FCM background deeplinks (Intent extras) into Compose navigation.
    // The callback is cleared on dispose so neither the NavController nor the Activity leaks.
    DisposableEffect(navController) {
        PushDeeplinkRouter.setNavigator { deeplink ->
            val uri = runCatching { android.net.Uri.parse(deeplink) }.getOrNull()
            if (uri == null || uri.scheme != "manahub") {
                android.util.Log.w("AppNavGraph", "Rejected push deeplink with invalid scheme: $deeplink")
                return@setNavigator
            }
            runCatching { navController.navigate(uri) }
                .onFailure { android.util.Log.w("AppNavGraph", "Push deeplink nav failed: $deeplink", it) }
        }
        onDispose { PushDeeplinkRouter.setNavigator(null) }
    }

    // Toast for invite results — shown at the global level so it is visible regardless of
    // which screen the user ends up on after the InviteDispatcherScreen navigates away.
    val inviteToastState = rememberMagicToastState()

    LaunchedEffect(Unit) {
        inviteVm.events.collect { event ->
            when (event) {
                is InviteDispatcherViewModel.UiEvent.InviteAccepted -> {
                    val msg = if (event.inviterNickname != null) {
                        context.getString(com.mmg.manahub.R.string.friends_invite_success, event.inviterNickname)
                    } else {
                        context.getString(com.mmg.manahub.R.string.friends_invite_success_generic)
                    }
                    inviteToastState.show(msg, MagicToastType.SUCCESS)
                }
                is InviteDispatcherViewModel.UiEvent.InviteError -> {
                    val msg = when {
                        event.isSelfInvite -> context.getString(com.mmg.manahub.R.string.friends_invite_self)
                        event.isInvalidCode -> context.getString(com.mmg.manahub.R.string.friends_invite_invalid)
                        else -> context.getString(com.mmg.manahub.R.string.friends_invite_error)
                    }
                    inviteToastState.show(msg, MagicToastType.ERROR)
                }
                InviteDispatcherViewModel.UiEvent.NavigateAway -> {
                    // Navigate to Profile, removing the invite screen from the back stack.
                    navController.navigate(Screen.Profile.baseRoute) {
                        popUpTo(Screen.FriendsInvite.route) { inclusive = true }
                    }
                }
            }
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    var pendingPlaytestSetup by remember { mutableStateOf<PlaytestSetup?>(null) }
    var pendingPlayerConfigs by remember { mutableStateOf<List<PlayerConfig>?>(null) }
    var pendingLayout by remember { mutableStateOf<LayoutTemplate?>(null) }
    var pendingGameSettings by remember { mutableStateOf(GameSettings()) }
    var pendingTournamentMatchId by remember { mutableStateOf<Long?>(null) }
    var pendingTournamentId by remember { mutableStateOf<Long?>(null) }
    var pendingTournamentPlayers by remember { mutableStateOf<List<Long>>(emptyList()) }
    var pendingTournamentMode by remember { mutableStateOf<GameMode?>(null) }

    // hasActiveGame: true only while a game is actively running (not finished).
    // Stays true when the game is abandoned temporarily, allowing resume from Play FAB.
    val hasActiveGame = gameUiState.isGameRunning

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                if (currentRoute in bottomBarRoutes && !isInPiP) {
                    MagicBottomBar(
                        currentRoute = currentRoute,
                        onHomeClick = { navController.navigateTab(Screen.Home.route) },
                        onPlayClick = {
                            if (hasActiveGame) {
                                navController.navigate(
                                    Screen.GamePlay.createRoute(
                                        gameUiState.mode.name,
                                        gameUiState.players.size,
                                    )
                                ) { launchSingleTop = true }
                            } else {
                                navController.navigate(Screen.GameSetup.baseRoute)
                            }
                        },
                        onLibraryClick = { navController.navigateTab(Screen.Collection.route) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = modifier.padding(padding).fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 5 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 5 } },
        ) {

            // ── Home (free-first dashboard) ───────────────────────────────────
            composable(Screen.Home.route) {
                // The live in-memory game state is owned by the activity-scoped
                // GameViewModel up here; pass it down rather than injecting the
                // GameViewModel into HomeViewModel.
                val activeGame = if (hasActiveGame) {
                    HomeHeroState.ActiveGame(
                        mode = gameUiState.mode.name,
                        playerCount = gameUiState.players.size,
                    )
                } else null

                HomeScreen(
                    viewModel = hiltViewModel(),
                    activeGame = activeGame,
                    onAction = { action ->
                        when (action) {
                            HomeAction.StartGame -> {
                                if (hasActiveGame) {
                                    navController.navigate(
                                        Screen.GamePlay.createRoute(
                                            gameUiState.mode.name,
                                            gameUiState.players.size,
                                        )
                                    ) { launchSingleTop = true }
                                } else {
                                    navController.navigate(Screen.GameSetup.baseRoute)
                                }
                            }
                            HomeAction.ScanCard -> navController.navigate(Screen.CollectionScanner.route)
                            HomeAction.SearchCard -> navController.navigate(Screen.CollectionAddCard.route)
                            HomeAction.CreateDeck -> navController.navigate(Screen.DeckStudio.createRoute(null))
                            HomeAction.DraftGuide -> navController.navigate(Screen.Draft.route)
                            HomeAction.DraftSimulator -> navController.navigate(Screen.Draft.route)
                            HomeAction.OpenLibrary -> navController.navigateTab(Screen.Collection.route)
                            HomeAction.OpenDecks -> navController.navigate(Screen.DeckList.route)
                            HomeAction.OpenNews -> navController.navigate(Screen.News.route)
                            HomeAction.OpenStats -> navController.navigate(Screen.Stats.route)
                            HomeAction.OpenFriends -> navController.navigate(Screen.FriendsList.route)
                            HomeAction.OpenTrades -> navController.navigate(Screen.Trades.route)
                            HomeAction.OpenTournaments -> navController.navigate(Screen.TournamentList.route)
                            HomeAction.OpenSettings -> navController.navigate(Screen.Settings.route)
                            HomeAction.OpenProfile -> navController.navigate(Screen.Profile.baseRoute)
                            // No "recent deck" is resolved here, so route to the deck list
                            // (where the user picks a deck to playtest / improve) rather than the
                            // generic Collection card grid.
                            HomeAction.PlaytestRecentDeck -> navController.navigate(Screen.DeckList.route)
                            HomeAction.ImproveRecentDeck -> navController.navigate(Screen.DeckList.route)
                            // CustomizeQuickStart, SaveQuickStart, DismissAccountNudge, RateApp are
                            // handled inside HomeScreen / HomeViewModel.
                            HomeAction.CustomizeQuickStart -> Unit
                            HomeAction.RateApp -> Unit
                            is HomeAction.SaveQuickStart -> Unit
                            HomeAction.CreateAccount -> navController.navigate(Screen.Profile.baseRoute)
                            HomeAction.DismissAccountNudge -> Unit

                            // ── Widget-board navigation ─────────────────────────
                            HomeAction.OpenDraftSimulator -> navController.navigate(Screen.Draft.route)
                            HomeAction.OpenDraftGuide -> navController.navigate(Screen.Draft.route)
                            HomeAction.OpenWishlist -> navController.navigateTab(Screen.Collection.route)
                            HomeAction.OpenAchievements -> navController.navigate(Screen.Stats.route)
                            HomeAction.OpenProfileQuests ->
                                navController.navigate(Screen.Profile.routeWithTab("quests"))
                            is HomeAction.OpenCardDetail -> navController.navigate(
                                Screen.CollectionCardDetail.createRoute(action.scryfallId)
                            )
                            is HomeAction.OpenDeck ->
                                navController.navigate(Screen.DeckDetail.createRoute(action.deckId))
                            is HomeAction.OpenNewsUrl -> {
                                val url = action.url.trim()
                                if (url.isNotEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        android.util.Log.w("AppNavGraph", "Could not open news URL: $url", it)
                                    }
                                } else {
                                    android.util.Log.w("AppNavGraph", "Rejected news URL with invalid/empty scheme: '$url'")
                                }
                            }
                            is HomeAction.OpenDraftSetDetail -> navController.navigate(
                                Screen.DraftSetDetail.createRoute(
                                    setCode = action.set.code,
                                    setName = action.set.name,
                                    setIconUri = action.set.iconSvgUri,
                                    setReleasedAt = action.set.releasedAt,
                                )
                            )

                            // ── Widget board: handled in HomeScreen/VM ───────────
                            HomeAction.OpenWidgetGallery,
                            HomeAction.ResetLayout,
                            HomeAction.RetryDiscover,
                            HomeAction.ResetNewsFilters,
                            is HomeAction.MoveWidget,
                            is HomeAction.AddWidget,
                            is HomeAction.RemoveWidget,
                            is HomeAction.UpdateLayout,
                            is HomeAction.SkipFirstStep,
                            -> Unit
                        }
                    },
                )
            }

            // ── Collection ────────────────────────────────────────────────────
            composable(Screen.Collection.route) {
                CollectionScreen(
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    onScannerClick = { navController.navigate(Screen.CollectionAddCard.route) },
                    onDeckClick = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                    onPlaytestClick = { id ->
                        navController.navigate(Screen.PlaytestSetup.createRoute(id))
                    },
                    onNavigateToTradeProposal = { receiverId ->
                        navController.navigate(Screen.CreateTradeProposal.createRoute(receiverId))
                    },
                    onNavigateToTradeThread = { proposalId, rootProposalId ->
                        navController.navigate(
                            Screen.TradeNegotiationDetail.createRoute(proposalId, rootProposalId)
                        )
                    },
                )
            }

            composable(Screen.DeckList.route) {
                CollectionScreen(
                    initialTab = CollectionTab.DECKS,
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    onScannerClick = { navController.navigate(Screen.CollectionAddCard.route) },
                    onDeckClick = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                    onPlaytestClick = { id ->
                        navController.navigate(Screen.PlaytestSetup.createRoute(id))
                    },
                    onNavigateToTradeProposal = { receiverId ->
                        navController.navigate(Screen.CreateTradeProposal.createRoute(receiverId))
                    },
                    onNavigateToTradeThread = { proposalId, rootProposalId ->
                        navController.navigate(
                            Screen.TradeNegotiationDetail.createRoute(proposalId, rootProposalId)
                        )
                    },
                )
            }

            composable(Screen.Trades.route) {
                CollectionScreen(
                    initialTab = CollectionTab.TRADES,
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    onScannerClick = { navController.navigate(Screen.CollectionAddCard.route) },
                    onDeckClick = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                    onPlaytestClick = { id ->
                        navController.navigate(Screen.PlaytestSetup.createRoute(id))
                    },
                    onNavigateToTradeProposal = { receiverId ->
                        navController.navigate(Screen.CreateTradeProposal.createRoute(receiverId))
                    },
                    onNavigateToTradeThread = { proposalId, rootProposalId ->
                        navController.navigate(
                            Screen.TradeNegotiationDetail.createRoute(proposalId, rootProposalId)
                        )
                    },
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
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCardDetail = { scryfallId ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(scryfallId))
                    },
                )
            }

            composable(
                route = Screen.CollectionCardDetail.route,
                arguments = listOf(navArgument("scryfallId") { type = NavType.StringType }),
            ) {
                CardDetailScreen(
                    onBack              = { navController.popBackStack() },
                    onNavigateToAddCard = { navController.navigate(Screen.CollectionAddCard.route) },
                    onNavigateToDeck    = { id -> navController.navigate(Screen.DeckDetail.createRoute(id)) },
                    onNavigateToCard    = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id)) {
                            popUpTo(Screen.CollectionCardDetail.route) { inclusive = true }
                        }
                    },
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
                    },
                    onReviewSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId, "REVIEW"))
                    },
                    onPlaytest = { id ->
                        navController.navigate(Screen.PlaytestSetup.createRoute(id))
                    },
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

            // ── Deck Studio (unified hybrid builder) ──────────────────────────
            composable(
                route = Screen.DeckStudio.route,
                arguments = listOf(
                    navArgument("deckId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
            ) {
                // The VM reads the optional deckId from SavedStateHandle ("" ⇒ fresh draft).
                DeckStudioScreen(
                    viewModel = hiltViewModel(),
                    onBack = { navController.popBackStack() },
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                )
            }

            // ── Stats ─────────────────────────────────────────────────────────
            composable(Screen.Stats.route) {
                StatsScreen(
                    onCardClick = { id ->
                        navController.navigate(
                            Screen.CollectionCardDetail.createRoute(id)
                        )
                    },
                    onBackClick = navController::popBackStack,
                    onReviewSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId, "REVIEW"))
                    },
                    onDeckClick = { deckId ->
                        navController.navigate(Screen.DeckDetail.createRoute(deckId))
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
                NewsScreen(
                    onVideoClick = { videoId, title ->
                        navController.navigate(Screen.NewsVideoPlayer.createRoute(videoId, title))
                    },
                )
            }

            composable(Screen.NewsSourcesSettings.route) {
                NewsSourcesSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.NewsVideoPlayer.route,
                arguments = listOf(
                    navArgument("videoId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val videoId = android.net.Uri.decode(backStackEntry.arguments?.getString("videoId") ?: return@composable)
                val title = android.net.Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
                VideoPlayerScreen(
                    videoId = videoId,
                    title = title,
                    isInPiP = isInPiP,
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
                    onSimulateDraft = { setCode ->
                        navController.navigate(Screen.DraftSimSetup.createRoute(setCode))
                    },
                )
            }

            // ── Draft Simulator ─────────────────────────────────────────────────
            composable(
                route = Screen.DraftSimSetup.route,
                arguments = listOf(navArgument("setCode") { type = NavType.StringType }),
            ) {
                val setCode = it.arguments?.getString("setCode") ?: return@composable
                DraftSetupScreen(
                    onNavigateToDrafting = {
                        navController.navigate(Screen.DraftSimDrafting.createRoute(setCode))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.DraftSimDrafting.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                val sessionId = it.arguments?.getString("sessionId") ?: return@composable
                DraftingScreen(
                    onNavigateToResult = {
                        navController.navigate(Screen.DraftSimResult.createRoute(sessionId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.DraftSimResult.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                DraftResultScreen(
                    onDeckSaved = {
                        navController.popBackStack(Screen.Draft.route, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Profile ───────────────────────────────────────────────────────
            composable(
                route = Screen.Profile.route,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                // Optional deep-link tab argument: "achievements" → Achievements tab,
                // "quests" → Quests tab, else Overview.
                val initialTab = when (backStackEntry.arguments?.getString("tab")?.lowercase()) {
                    "achievements" -> ProfileTab.ACHIEVEMENTS
                    "quests" -> ProfileTab.QUESTS
                    else -> ProfileTab.OVERVIEW
                }
                ProfileScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onStatsClick = { navController.navigate(Screen.Stats.route) },
                    onFriendsClick = { navController.navigate(Screen.FriendsList.route) },
                    initialTab = initialTab,
                )
            }

            composable(
                route = Screen.FriendsList.route,
                deepLinks = listOf(navDeepLink { uriPattern = "manahub://friends" }),
            ) {
                // Suppress foreground friend push notifications while the user is on this screen.
                DisposableEffect(Unit) {
                    ForegroundScreenTracker.setCurrentDeeplink("manahub://friends")
                    onDispose { ForegroundScreenTracker.setCurrentDeeplink(null) }
                }
                FriendsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFriendDetail = { userId ->
                        navController.navigate(Screen.FriendDetail.createRoute(userId))
                    },
                )
            }

            composable(
                route = Screen.FriendDetail.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) {
                FriendDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    onNavigateToTradeDetail = { proposalId, rootProposalId ->
                        navController.navigate(
                            Screen.TradeNegotiationDetail.createRoute(proposalId, rootProposalId)
                        )
                    },
                )
            }

            // ── Friend invite deep link ────────────────────────────────────────
            composable(
                route = Screen.FriendsInvite.route,
                arguments = listOf(navArgument("code") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "https://miguelmglez.github.io/invite/{code}" },
                    navDeepLink { uriPattern = "manahub://invite/{code}" },
                ),
            ) { backStack ->
                val code = backStack.arguments?.getString("code") ?: ""
                InviteDispatcherScreen(
                    code = code,
                    onNavigateAway = {
                        navController.navigate(Screen.Profile.baseRoute) {
                            popUpTo(Screen.FriendsInvite.route) { inclusive = true }
                        }
                    },
                    inviteVm = inviteVm,
                )
            }

            // ── Trades shared list (deep link) ────────────────────────────────
            composable(
                route     = Screen.TradesSharedList.route,
                arguments = listOf(navArgument("shareId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "https://miguelmglez.github.io/list/{shareId}"
                    },
                ),
            ) {
                TradesSharedListScreen(onBack = { navController.popBackStack() })
            }

            // ── Trade proposal editor ─────────────────────────────────────────
            composable(
                route     = Screen.CreateTradeProposal.route,
                arguments = listOf(
                    navArgument("receiverId") { type = NavType.StringType },
                    navArgument("parentProposalId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("editingProposalId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("rootProposalId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) {
                CreateTradeProposalScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToThread = { proposalId, rootProposalId ->
                        navController.navigate(
                            Screen.TradeNegotiationDetail.createRoute(proposalId, rootProposalId)
                        ) {
                            popUpTo(Screen.CreateTradeProposal.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Screen.Profile.baseRoute)
                    },
                    onNavigateToAddFriends = {
                        navController.navigate(Screen.FriendsList.route)
                    },
                    onNavigateToCardDetail = { scryfallId ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(scryfallId))
                    }
                )
            }

            // ── Trade negotiation thread ──────────────────────────────────────
            composable(
                route     = Screen.TradeNegotiationDetail.route,
                arguments = listOf(
                    navArgument("proposalId") { type = NavType.StringType },
                    navArgument("rootProposalId") { type = NavType.StringType },
                ),
                // URI order is root-first then proposal — matches the push deeplink format.
                deepLinks = listOf(
                    navDeepLink { uriPattern = "manahub://trade/{rootProposalId}/{proposalId}" },
                ),
            ) { backStack ->
                val proposalId = backStack.arguments?.getString("proposalId") ?: ""
                val rootProposalId = backStack.arguments?.getString("rootProposalId") ?: ""
                // Suppress foreground push notifications while this exact thread is on screen.
                DisposableEffect(proposalId, rootProposalId) {
                    ForegroundScreenTracker.setCurrentDeeplink("manahub://trade/$rootProposalId/$proposalId")
                    onDispose { ForegroundScreenTracker.setCurrentDeeplink(null) }
                }
                TradeNegotiationDetailScreen(
                    onBack             = { navController.popBackStack() },
                    onNavigateToCardDetail = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    onNavigateToEditor = { args ->
                        val route = if (args.isCounter) {
                            Screen.CreateTradeProposal.createCounterRoute(
                                receiverId      = args.receiverId,
                                parentProposalId = args.proposalId,
                                rootProposalId  = args.rootProposalId,
                            )
                        } else {
                            Screen.CreateTradeProposal.createEditRoute(
                                receiverId        = args.receiverId,
                                editingProposalId = args.proposalId,
                                rootProposalId    = args.rootProposalId,
                            )
                        }
                        navController.navigate(route)
                    },
                )
            }

            // ── Tournament flow ────────────────────────────────────────────────
            composable(Screen.TournamentList.route) {
                TournamentListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreateTournament = { navController.navigate(Screen.TournamentSetup.route) },
                    onOpenTournament = { id ->
                        navController.navigate(Screen.TournamentDetail.route(id))
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
            //
            // GameSetup now supports an optional joinCode query parameter.
            // All navigate() calls to GameSetup must use Screen.GameSetup.baseRoute
            // (no join code) or Screen.GameSetup.routeWithJoinCode(code) when a deep-link
            // join code needs to pre-open the join sheet.
            composable(
                route = Screen.GameSetup.route, // "game/setup?joinCode={joinCode}"
                arguments = listOf(
                    navArgument("joinCode") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val joinCode = backStackEntry.arguments?.getString("joinCode")?.takeIf { it.isNotBlank() }
                val setupVm: GameSetupViewModel = hiltViewModel()
                GameSetupScreen(
                    viewModel = setupVm,
                    onBack = { navController.popBackStack() },
                    onStartGame = { mode, configs, layout, settings ->
                        pendingPlayerConfigs = configs
                        pendingLayout = layout
                        pendingGameSettings = settings
                        pendingTournamentMatchId = null
                        pendingTournamentId = null
                        pendingTournamentPlayers = emptyList()
                        pendingTournamentMode = null
                        navController.navigate(
                            Screen.GamePlay.createRoute(mode.name, configs.size)
                        ) {
                            popUpTo(Screen.GameSetup.baseRoute) { inclusive = true }
                        }
                    },
                    onOnlineHostGameStart = { sessionId, mode, playerCount ->
                        val configs = (0 until playerCount).mapIndexed { i, _ ->
                            PlayerConfig(
                                id        = i,
                                name      = "",
                                theme     = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                                isAppUser = i == 0,
                            )
                        }
                        gameVm.initFromOnlineSession(sessionId, 0, configs, mode)
                        navController.navigate(Screen.GamePlay.createRoute(mode.name, playerCount)) {
                            popUpTo(Screen.GameSetup.baseRoute) { inclusive = true }
                        }
                    },
                    onOnlineJoinGameStart = { sessionId, slotIndex, modeStr, playerCount ->
                        val mode = runCatching { GameMode.valueOf(modeStr) }.getOrDefault(GameMode.STANDARD)
                        val configs = (0 until playerCount).mapIndexed { i, _ ->
                            PlayerConfig(
                                id        = i,
                                name      = "",
                                theme     = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                                isAppUser = i == slotIndex,
                            )
                        }
                        gameVm.initFromOnlineSession(sessionId, slotIndex, configs, mode)
                        navController.navigate(Screen.GamePlay.createRoute(mode.name, playerCount)) {
                            popUpTo(Screen.GameSetup.baseRoute) { inclusive = true }
                        }
                    },
                    onNavigateToTournamentSetup = { navController.navigate(Screen.TournamentSetup.route) },
                    onNavigateToTournamentDetail = { id -> navController.navigate(Screen.TournamentDetail.route(id)) },
                    prefilledJoinCode = joinCode,
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
                            gameVm.initFromConfigs(configs, routeMode, pendingLayout, pendingGameSettings)
                        }
                        pendingPlayerConfigs = null
                        pendingLayout = null
                        pendingGameSettings = GameSettings()
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
                        navController.navigate(Screen.GameSetup.baseRoute) {
                            popUpTo(Screen.GamePlay.route) { inclusive = true }
                        }
                    },
                    onBackHome = {
                        gameVm.finishGame()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onAbandonGame = {
                        // Abandon temporarily: preserve game state so Play FAB can resume it
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onExitGame = {
                        gameVm.finishGame()
                        navController.navigate(Screen.GameSetup.baseRoute) {
                            popUpTo(Screen.Collection.route) { inclusive = false }
                        }
                    },
                    onSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId))
                    },
                )
            }

            // ── Online multiplayer lobby (redirect stubs) ─────────────────────
            //
            // LobbyHost and LobbyJoin are no longer full-screen destinations.
            // They redirect back to GameSetupScreen (optionally carrying the join code)
            // so the new sheet-based flow handles everything.

            composable(
                route = Screen.LobbyHost.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("playerCount") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                // Redirect: any navigation to the old LobbyHost route lands back on GameSetup.
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.GameSetup.baseRoute) {
                        popUpTo(Screen.LobbyHost.route) { inclusive = true }
                    }
                }
            }

            composable(
                route = Screen.LobbyJoin.route,
                arguments = listOf(
                    navArgument("code") { type = NavType.StringType; defaultValue = "" },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "manahub://join/{code}" },
                ),
            ) { backStackEntry ->
                val code = backStackEntry.arguments?.getString("code") ?: ""
                // Redirect: carry the join code to GameSetup so it can auto-open the join sheet.
                LaunchedEffect(code) {
                    val dest = if (code.isNotBlank()) {
                        Screen.GameSetup.routeWithJoinCode(code)
                    } else {
                        Screen.GameSetup.baseRoute
                    }
                    navController.navigate(dest) {
                        popUpTo(Screen.LobbyJoin.route) { inclusive = true }
                    }
                }
            }

            // ── Deck Playtest ─────────────────────────────────────────────────
            composable(
                route     = Screen.PlaytestSetup.route,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
            ) {
                PlaytestSetupScreen(
                    onBack            = { navController.popBackStack() },
                    onNavigateToHand  = { setup ->
                        pendingPlaytestSetup = setup
                        navController.navigate(Screen.PlaytestHand.createRoute(setup.deckId))
                    },
                )
            }

            composable(
                route     = Screen.PlaytestHand.route,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
            ) {
                val setup = pendingPlaytestSetup
                if (setup != null) {
                    PlaytestHandScreen(
                        setup  = setup,
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    // Process death / back-stack restore: the in-memory setup was lost.
                    // Record as non-fatal to quantify how often process death hits real users.
                    LaunchedEffect(Unit) {
                        FirebaseCrashlytics.getInstance().apply {
                            log("playtest_hand_session_expired: pendingSetup was null on route restore")
                            recordException(
                                IllegalStateException("[AppNavGraph] PlaytestHand restored with null pendingPlaytestSetup — process death suspected")
                            )
                        }
                    }
                    FullErrorState(
                        message    = androidx.compose.ui.res.stringResource(
                            com.mmg.manahub.R.string.playtest_session_expired,
                        ),
                        retryLabel = androidx.compose.ui.res.stringResource(
                            com.mmg.manahub.R.string.action_back,
                        ),
                        onRetry    = { navController.popBackStack() },
                    )
                }
            }

            // ── Post-game survey ──────────────────────────────────────────────
            composable(
                route = Screen.GameSurvey.route,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "COMPLETE" },
                ),
            ) { backStack ->
                val mode = backStack.arguments?.getString("mode") ?: "COMPLETE"
                SurveyScreen(
                    onComplete = {
                        if (mode == "REVIEW") {
                            navController.popBackStack()
                        } else {
                            gameVm.finishGame()
                            navController.navigate(Screen.GameSetup.baseRoute) {
                                popUpTo(Screen.Collection.route) { inclusive = false }
                            }
                        }
                    },
                )
            }
        }
        // Global toast for invite results — overlays all screens without interfering with
        // per-screen toast hosts (they all co-exist independently).
        MagicToastHost(
            state = inviteToastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
        } // end Box
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
