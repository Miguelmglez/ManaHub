package com.mmg.manahub.app.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.model.LayoutTemplate
import com.mmg.manahub.core.model.LayoutTemplates
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.push.ForegroundScreenTracker
import com.mmg.manahub.core.push.PushDeeplinkRouter
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicBottomBar
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.addcard.presentation.AddCardScreen
import com.mmg.manahub.feature.auth.data.repository.AuthRepositoryImpl
import com.mmg.manahub.feature.auth.presentation.AccountManagementScreen
import com.mmg.manahub.feature.auth.presentation.ResetPasswordConfirmScreen
import com.mmg.manahub.feature.auth.presentation.isActiveRecoveryFlow
import com.mmg.manahub.feature.auth.presentation.UpdateEmailScreen
import com.mmg.manahub.feature.auth.presentation.UpdatePasswordScreen
import com.mmg.manahub.feature.carddetail.presentation.CardDetailScreen
import com.mmg.manahub.feature.collection.presentation.CollectionScreen
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDeckDetailScreen
import com.mmg.manahub.feature.communitydecks.presentation.CommunityDecksScreen
import com.mmg.manahub.feature.competitive.presentation.CompetitiveScreen
import com.mmg.manahub.feature.decks.presentation.DeckStudioScreen
import com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftSetupScreen
import com.mmg.manahub.feature.draft.presentation.ui.DraftSimulatorScreen
import com.mmg.manahub.feature.draft.presentation.ui.SetDraftDetailScreen
import com.mmg.manahub.feature.friends.presentation.FriendsScreen
import com.mmg.manahub.feature.friends.presentation.detail.FriendDetailScreen
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherScreen
import com.mmg.manahub.feature.friends.presentation.invite.InviteDispatcherViewModel
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.presentation.GamePlayScreen
import com.mmg.manahub.feature.game.presentation.GameSettings
import com.mmg.manahub.feature.game.presentation.GameSetupScreen
import com.mmg.manahub.feature.game.presentation.GameSetupViewModel
import com.mmg.manahub.feature.game.presentation.GameViewModel
import com.mmg.manahub.feature.game.presentation.PlayerConfig
import com.mmg.manahub.feature.home.presentation.HomeAction
import com.mmg.manahub.feature.home.presentation.HomeHeroState
import com.mmg.manahub.feature.home.presentation.HomeScreen
import com.mmg.manahub.feature.massiveadd.presentation.MassiveAddCardScreen
import com.mmg.manahub.feature.news.presentation.NewsScreen
import com.mmg.manahub.feature.news.presentation.NewsSourcesSettingsScreen
import com.mmg.manahub.feature.news.presentation.VideoPlayerScreen
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.feature.playtest.presentation.hand.PlaytestHandScreen
import com.mmg.manahub.feature.playtest.presentation.setup.PlaytestSetupScreen
import com.mmg.manahub.feature.profile.presentation.ProfileScreen
import com.mmg.manahub.feature.profile.presentation.ProfileTab
import com.mmg.manahub.feature.puzzle.presentation.PuzzleScreen
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// ═══════════════════════════════════════════════════════════════════════════════
//  Bottom-bar visibility rules
//  Visible on the root Library tab; hidden on all detail / game /
//  scanner flows and on screens now reached through Home (Draft, News, Profile…).
// ═══════════════════════════════════════════════════════════════════════════════

private val bottomBarRoutes = setOf(
    Screen.Home.route,
    Screen.Collection.route,
)

/**
 * Pure routing predicate for the password-recovery `LaunchedEffect` in [AppNavGraph] (CRITICAL fix,
 * 2026-08-18; hardened 2026-08-18 — see `feature/auth/CLAUDE.md`'s "Routing to
 * Screen.ResetPasswordConfirm" section, `feedback_supabase_deeplink_onsessionsuccess_race`, and
 * `docs/plans/password-recovery-hardening-plan-2026-08-18.md`). Extracted to a top-level `internal`
 * function, rather than left inline in the `LaunchedEffect` body, specifically so it is
 * unit-testable with plain JUnit — `MainActivity`/`AppNavGraph` otherwise have NO test coverage (per
 * the 2026-08-18 edge-case-tester audit that found the bugs this predicate exists to fix), and this
 * is the one piece of that routing logic cheap to pull out into something a fast, Compose-free test
 * can verify.
 *
 * @param sessionState The app's current [SessionState] (from `AuthRepository.sessionState`).
 * @param currentRoute The nav-graph's current destination route, or `null` if none is composed yet.
 * @param marker The pending-recovery marker as (sessionId, markedAtEpochMs), from
 *   `UserPreferencesDataStore.pendingRecoveryMarkerFlow`, or `null` when no marker is armed. Passed
 *   through to [isActiveRecoveryFlow] — see that function's KDoc for why `isRecoverySession` alone
 *   (the pre-2026-08-18-hardening signal) is not sufficient: it also admits a signup-confirmation
 *   session.
 * @param nowEpochMs The current time, injected for deterministic unit testing.
 * @return `true` only when [isActiveRecoveryFlow] holds AND the app is not already showing
 *   [Screen.ResetPasswordConfirm] — this second condition is the re-entrancy guard (HIGH fix):
 *   `AuthRepositoryImpl.sessionState`'s enrichment flow can emit the same recovery-authenticated
 *   state more than once (a "fast" emit then an "enriched" emit), and without this check each
 *   emission would independently trigger another `navigate(...)` call.
 */
internal fun shouldRouteToRecoveryScreen(
    sessionState: SessionState,
    currentRoute: String?,
    marker: Pair<String, Long>?,
    nowEpochMs: Long,
): Boolean {
    return isActiveRecoveryFlow(sessionState, marker, nowEpochMs) &&
        currentRoute != Screen.ResetPasswordConfirm.route
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    isInPiP: Boolean = false,
) {
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current

    // Activity-scoped so game state persists across all navigation.
    // KMP migration — Phase 1: GameViewModel is now a Koin "island" VM, resolved via
    // koinViewModel(viewModelStoreOwner = activity) — the exact equivalent of the old
    // hiltViewModel(activity): same Activity ViewModelStore → same single instance across navigation,
    // and the activity's CreationExtras carry the mode/playerCount nav args into the SavedStateHandle.
    val gameVm: GameViewModel = koinViewModel(viewModelStoreOwner = activity)
    val gameUiState by gameVm.uiState.collectAsStateWithLifecycle()

    // Activity-scoped so it survives navigation and can process pending invite codes after login.
    // KMP migration — Phase 1: InviteDispatcherViewModel is now a Koin "island" VM, so it is resolved
    // via koinViewModel(viewModelStoreOwner = activity) — the exact equivalent of the old
    // hiltViewModel(activity): same Activity ViewModelStore → same single instance across navigation.
    val inviteVm: InviteDispatcherViewModel = koinViewModel(viewModelStoreOwner = activity)

    val navController = rememberNavController()

    // Bridge FCM background deeplinks (Intent extras) into Compose navigation.
    // The callback is cleared on dispose so neither the NavController nor the Activity leaks.
    DisposableEffect(navController) {
        PushDeeplinkRouter.setNavigator { deeplink ->
            val uri = runCatching { Uri.parse(deeplink) }.getOrNull()
            if (uri == null || uri.scheme != "manahub") {
                Log.w("AppNavGraph", "Rejected push deeplink with invalid scheme: $deeplink")
                return@setNavigator
            }
            runCatching { navController.navigate(uri) }
                .onFailure { Log.w("AppNavGraph", "Push deeplink nav failed: $deeplink", it) }
        }
        onDispose { PushDeeplinkRouter.setNavigator(null) }
    }

    // ── Password-recovery routing (CRITICAL fix, 2026-08-18) ────────────────────────────
    // This LaunchedEffect is now the SOLE trigger for routing to Screen.ResetPasswordConfirm —
    // see MainActivity.handleSupabaseAuthDeepLink's KDoc and
    // feedback_supabase_deeplink_onsessionsuccess_race for the full history. It replaces a prior
    // MainActivity-side "wait on sessionState with a bounded timeout, then enqueue via
    // PushDeeplinkRouter" fix that closed the original onSessionSuccess/importSession race but
    // reopened a narrower version of it: handleDeeplinks() persists the recovery session
    // (Keystore-backed SessionManager, autoLoadFromStorage = true) BEFORE that wait coroutine even
    // starts, so a process death during the wait (e.g. the user backgrounds the app right after
    // tapping the email link) silently dropped the enqueue while the session survived in storage —
    // same end state as the original bug (landing on Home fully authenticated, no "set new
    // password" form), different trigger.
    //
    // Observing AuthRepository.sessionState directly here — independent of MainActivity's intent
    // handling, coroutine, or even the specific process incarnation that imported the session —
    // makes that failure mode structurally impossible: whenever sessionState resolves to
    // Authenticated(isRecoverySession = true), in ANY process (a fresh deep-link tap, a cold start
    // that restored an already-persisted recovery session, or a warm resume), this effect routes
    // the user. It also has no timeout, unlike the old MainActivity wait — a slow user_profiles
    // enrichment fetch (AuthRepositoryImpl.sessionState's flatMapLatest, e.g. for a Google-provider
    // account on a slow connection) simply delays routing instead of silently failing it.
    //
    // Re-entrancy guard (HIGH fix, 2026-08-18): sessionState can re-emit multiple times while
    // still recovery-authenticated (AuthRepositoryImpl's enrichment flow emits a "fast" state and
    // then an "enriched" one for the same recovery session). Checking the CURRENT destination
    // before navigating — plus launchSingleTop as a second guard — makes every emission after the
    // first a structural no-op instead of pushing a duplicate Screen.ResetPasswordConfirm entry
    // onto the back stack (which would otherwise make "Back to sign in" pop to the wrong place on
    // a double-tapped/duplicate email link). This is the single navigation trigger for this
    // screen now — PushDeeplinkRouter is no longer involved in the recovery flow at all, so there
    // is no second path that could race this one.
    val authRepository: AuthRepository = koinInject()
    val recoverySessionState by authRepository.sessionState.collectAsStateWithLifecycle()
    // password-recovery-hardening-plan-2026-08-18 §3: isRecoverySession (amr) alone is not
    // sufficient — GoTrue also tags a signup-confirmation session amr: otp. The pending-recovery
    // marker is the second, app-side half of the gate — see UserPreferencesDataStore's KDoc and
    // isActiveRecoveryFlow's KDoc in feature.auth.presentation.
    val userPreferencesDataStore: UserPreferencesDataStore = koinInject()
    val pendingRecoveryMarker by userPreferencesDataStore.pendingRecoveryMarkerFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val recoveryCoroutineScope = rememberCoroutineScope()
    LaunchedEffect(recoverySessionState, pendingRecoveryMarker) {
        if (shouldRouteToRecoveryScreen(
                recoverySessionState,
                navController.currentDestination?.route,
                pendingRecoveryMarker,
                System.currentTimeMillis(),
            )
        ) {
            FirebaseCrashlytics.getInstance().log("app_nav_graph_recovery_session_routed")
            navController.navigate(Screen.ResetPasswordConfirm.route) { launchSingleTop = true }
        }
    }

    // Toast for invite results — shown at the global level so it is visible regardless of
    // which screen the user ends up on after the InviteDispatcherScreen navigates away.
    val inviteToastState = rememberMagicToastState()

    LaunchedEffect(Unit) {
        inviteVm.events.collect { event ->
            when (event) {
                is InviteDispatcherViewModel.UiEvent.InviteAccepted -> {
                    val msg = if (event.inviterNickname != null) {
                        context.getString(R.string.friends_invite_success, event.inviterNickname)
                    } else {
                        context.getString(R.string.friends_invite_success_generic)
                    }
                    inviteToastState.show(msg, MagicToastType.SUCCESS)
                }
                is InviteDispatcherViewModel.UiEvent.InviteError -> {
                    val msg = when {
                        event.isSelfInvite -> context.getString(R.string.friends_invite_self)
                        event.isInvalidCode -> context.getString(R.string.friends_invite_invalid)
                        else -> context.getString(R.string.friends_invite_error)
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
                        homeRoute = Screen.Home.route,
                        collectionBaseRoute = Screen.Collection.baseRoute,
                        gamePainter = painterResource(R.drawable.ic_battle),
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
                        onLibraryClick = {
                            navController.navigate(Screen.Collection.baseRoute) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        SharedTransitionLayout {
            Box(modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier,
                    enterTransition = { fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 5 } },
                    exitTransition = { fadeOut(tween(500)) },
                    popEnterTransition = { fadeIn(tween(400)) },
                    popExitTransition = { fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { it / 5 } },
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
                            // HomeViewModel is now resolved by Koin via the screen's koinViewModel() default
                            // param (KMP migration — Home Koin island). Other features still use hiltViewModel().
                            activeGame = activeGame,
                            onAction = { action: HomeAction ->
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
                                    HomeAction.OpenLibrary -> navController.navigateTab(Screen.Collection.baseRoute)
                                    HomeAction.OpenDecks -> navController.navigate(Screen.Collection.routeWithTab("decks"))
                                    HomeAction.OpenNews -> navController.navigate(Screen.News.route)
                                    HomeAction.OpenStats -> navController.navigate(Screen.Stats.route)
                                    HomeAction.OpenFriends -> navController.navigate(Screen.FriendsList.route)
                                    HomeAction.OpenTrades -> navController.navigate(Screen.Collection.routeWithTab("trades"))
                        HomeAction.OpenCommunityDecks -> navController.navigate(Screen.CommunityDecks.route)
                        // Home widget board overhaul, TASK 5b/5c — opens the deck detail NATIVELY
                        // instead of the old SOCIAL_HUB slide's external-browser redirect.
                        is HomeAction.OpenCommunityDeck ->
                            navController.navigate(Screen.CommunityDeckDetail.createRoute(action.archidektId))
                        HomeAction.OpenTournaments -> navController.navigate(Screen.TournamentList.route)
                                    HomeAction.OpenSettings -> navController.navigate(Screen.Settings.route)
                                    HomeAction.OpenProfile -> navController.navigate(Screen.Profile.baseRoute)
                                    // PlaytestRecentDeck is resolved into NavigatePlaytest(deckId) inside
                                    // HomeScreen (the only layer with access to uiState.decks).
                                    HomeAction.PlaytestRecentDeck -> Unit
                                    is HomeAction.NavigatePlaytest -> {
                                        val deckId = action.deckId
                                        if (deckId != null) {
                                            navController.navigate(Screen.PlaytestSetup.createRoute(deckId))
                                        } else {
                                            navController.navigate(Screen.Collection.routeWithTab("decks"))
                                        }
                                    }
                                    HomeAction.ImproveRecentDeck -> navController.navigate(Screen.Collection.routeWithTab("decks"))
                                    // CustomizeQuickStart, SaveQuickStart, DismissAccountNudge, RateApp are
                                    // handled inside HomeScreen / HomeViewModel.
                                    HomeAction.CustomizeQuickStart -> Unit
                                    HomeAction.RateApp -> {
                                        val reviewManager = ReviewManagerFactory.create(context)
                                        val request = reviewManager.requestReviewFlow()
                                        request.addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val reviewInfo = task.result
                                                activity.let { reviewManager.launchReviewFlow(it, reviewInfo) }
                                            } else {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                                                }
                                            }
                                        }
                                    }
                                    is HomeAction.SaveQuickStart -> Unit
                                    HomeAction.CreateAccount -> navController.navigate(Screen.Profile.baseRoute)
                                    HomeAction.DismissAccountNudge -> Unit

                                    // ── Widget-board navigation ─────────────────────────
                                    HomeAction.OpenDraftSimulator -> navController.navigate(Screen.Draft.route)
                                    HomeAction.OpenDraftGuide -> navController.navigate(Screen.Draft.route)
                                    HomeAction.OpenWishlist -> navController.navigateTab(Screen.Collection.baseRoute)
                                    HomeAction.OpenAchievements -> navController.navigate(Screen.Stats.route)
                                    HomeAction.OpenProfileQuests ->
                                        navController.navigate(Screen.Profile.routeWithTab("quests"))
                                    HomeAction.OpenDailyPuzzle -> navController.navigate(Screen.DailyPuzzle.route)
                                    is HomeAction.OpenCardDetail -> navController.navigate(
                                        Screen.CollectionCardDetail.createRoute(action.scryfallId, action.sharedTransitionKey)
                                    )
                                    is HomeAction.OpenDeck ->
                                        navController.navigate(Screen.DeckStudio.createRoute(action.deckId))
                                    is HomeAction.OpenNewsUrl -> {
                                        val url = action.url.trim()
                                        if (url.isNotEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }.onFailure {
                                                Log.w("AppNavGraph", "Could not open news URL: $url", it)
                                            }
                                        } else {
                                            Log.w("AppNavGraph", "Rejected news URL with invalid/empty scheme: '$url'")
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
                                    is HomeAction.OpenCompetitive->{
                                        navController.navigate(Screen.Competitive.route)
                                    }
                                    is HomeAction.OpenMultiAdd-> navController.navigate(Screen.CollectionMassiveAddCard)
                                    // ── Widget board: handled in HomeScreen/VM ───────────
                                    HomeAction.OpenWidgetGallery,
                                    HomeAction.ResetLayout,
                                    HomeAction.RetryDiscover,
                                    HomeAction.RefreshDiscover,
                                    HomeAction.RefreshRandomCard,
                                    is HomeAction.SelectDiscoverSet,
                                    HomeAction.ResetNewsFilters,
                                    is HomeAction.MoveWidget,
                                    is HomeAction.AddWidget,
                                    is HomeAction.RemoveWidget,
                                    is HomeAction.UpdateLayout,
                                    is HomeAction.SkipFirstStep,
                                    is HomeAction.SelectCommunityDecksCategory,
                                    -> Unit
                                }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    // ── Collection (Library) ──────────────────────────────────────────
                    composable(
                        route = Screen.Collection.route,
                        arguments = listOf(
                            navArgument("tab") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { backStackEntry ->
                        CollectionScreen(
                            // G.1 fix: read the "tab" arg directly off THIS backStackEntry (always
                            // reflects the current navigate() call, e.g. "decks" from the Draft
                            // Simulator hand-off) rather than relying on CollectionViewModel's
                            // SavedStateHandle-read-at-init (which freezes on the value seen the
                            // FIRST time this destination's ViewModel was constructed — a restored
                            // instance, per navigateTab's restoreState=true contract, keeps that
                            // frozen value and never re-reads a fresh arg). See CollectionScreen's
                            // initialTabArg LaunchedEffect for how this is applied.
                            initialTabArg = backStackEntry.arguments?.getString("tab"),
                            onCardClick = { id, key ->
                                navController.navigate(Screen.CollectionCardDetail.createRoute(id, key))
                            },
                            onAddCardClick = { navController.navigate(Screen.CollectionAddCard.route) },
                            onDeckClick = { id -> navController.navigate(Screen.DeckStudio.createRoute(id)) },
                            onCreateDeck = { navController.navigate(Screen.DeckStudio.createRoute(null)) },
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
                            onBrowseCommunityDecks = {
                                navController.navigate(Screen.CommunityDecks.route)
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
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
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }

                    composable(Screen.CollectionMassiveAddCard.route) {
                        MassiveAddCardScreen(
                            onBack = { navController.popBackStack() },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
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
                            onNavigateToAddCard = { navController.navigate(Screen.CollectionAddCard.route) },
                            onNavigateToDeck = { id -> navController.navigate(Screen.DeckStudio.createRoute(id)) },
                            onNavigateToCommunityDecks = { cardName ->
                                navController.navigate(Screen.CommunityDecksByCard.createRoute(cardName))
                            }
                        )
                    }

                    composable(
                        route = Screen.CollectionCardDetail.route,
                        arguments = listOf(
                            navArgument("scryfallId") { type = NavType.StringType },
                            navArgument("sharedTransitionKey") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        ),
                        enterTransition = { 
                            fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(450))
                        },
                        exitTransition = { fadeOut(tween(500)) }
                    ) { backStackEntry ->
                        val sharedTransitionKey = backStackEntry.arguments?.getString("sharedTransitionKey")
                        CardDetailScreen(
                            onBack              = { navController.popBackStack() },
                            onNavigateToAddCard = { navController.navigate(Screen.CollectionAddCard.route) },
                            onNavigateToDeck    = { id -> navController.navigate(Screen.DeckStudio.createRoute(id)) },
                            onNavigateToCard    = { id ->
                                navController.navigate(Screen.CollectionCardDetail.createRoute(id)) {
                                    popUpTo(Screen.CollectionCardDetail.route) { inclusive = true }
                                }
                            },
                            onNavigateToCommunityDecks = { cardName ->
                                navController.navigate(Screen.CommunityDecksByCard.createRoute(cardName))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            sharedTransitionKey = sharedTransitionKey
                        )
                    }

            // ── Community Decks (Archidekt browse + import) ───────────────────
            composable(
                route = Screen.CommunityDeckDetail.route,
                arguments = listOf(navArgument("archidektId") { type = NavType.IntType }),
            ) {
                CommunityDeckDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDeck = { deckId ->
                        navController.navigate(Screen.DeckStudio.createRoute(deckId)) {
                            popUpTo(Screen.CommunityDeckDetail.route) { inclusive = true }
                        }
                    },
                    onCardClick = { scryfallId ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(scryfallId))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }

            // Daily Puzzle (ADR-006, Batch B2)
            composable(route = Screen.DailyPuzzle.route) {
                PuzzleScreen(onBack = { navController.popBackStack() })
            }

            // Community Decks browse / search (landing)
            composable(route = Screen.CommunityDecks.route) {
                CommunityDecksScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDeck = { archidektId ->
                        navController.navigate(Screen.CommunityDeckDetail.createRoute(archidektId))
                    },
                )
            }

            // Community Decks filtered by card name (deep-linked from card detail)
            composable(
                route = Screen.CommunityDecksByCard.route,
                arguments = listOf(navArgument("cardName") { type = NavType.StringType }),
            ) {
                CommunityDecksScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDeck = { archidektId ->
                        navController.navigate(Screen.CommunityDeckDetail.createRoute(archidektId))
                    },
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
                    },
                    navArgument("fromDraft") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                // E.10a: a deck reached via the Draft Simulator's "deck saved" hand-off has no
                // useful `popBackStack()` target — Phase A/C's popUpTo already removed the
                // setup/drafting entries, so a plain pop lands on Screen.Draft (the set browser),
                // not where a user managing their new deck expects to end up. Route back to
                // Collection's Decks tab instead; every other entry point is unaffected (fromDraft
                // defaults false, keeping the exact popBackStack() behavior it always had).
                //
                // F.5 fix: this used to hand-roll its own popUpTo/navigate instead of reusing
                // navigateTab (the ONE pattern every other bottom-tab transition in the app uses —
                // MagicBottomBar's onHomeClick/onLibraryClick, and every other navigateTab call
                // site). The hand-rolled version was missing `saveState = true` on the popUpTo and
                // `restoreState = true` on the navigate — both present in navigateTab and in
                // MagicBottomBar's own onLibraryClick — so a Collection entry pushed this way never
                // entered the save/restore state-tracking contract the bottom bar's OWN Home/Library
                // taps rely on immediately afterward (Compose Navigation's saveState/restoreState is
                // keyed per DESTINATION and shared across every push/pop of that destination — an
                // entry that bypasses it is exactly the kind of inconsistency that shows up as "the
                // bottom bar stops responding" on a SUBSEQUENT tap, not on this navigation itself).
                // It also targeted `Screen.Home.route` instead of `graph.startDestinationId` (the
                // two resolve to the same destination today since Home IS the start destination, so
                // this alone wasn't the defect — but drifting from the single canonical pattern is
                // exactly how these two diverge again later). Reusing navigateTab structurally
                // eliminates both: it's the same call already proven correct by every other tab
                // transition in production.
                val fromDraft = backStackEntry.arguments?.getBoolean("fromDraft") ?: false
                val deckStudioOnBack: () -> Unit = if (fromDraft) {
                    { navController.navigateTab(Screen.Collection.routeWithTab("decks")) }
                } else {
                    { navController.popBackStack() }
                }
                // The VM reads the optional deckId from SavedStateHandle ("" ⇒ fresh draft).
                DeckStudioScreen(
                    viewModel = koinViewModel(),
                    onBack = deckStudioOnBack,
                    onCardClick = { id ->
                        navController.navigate(Screen.CollectionCardDetail.createRoute(id))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onPlaytest = { deckId ->
                        navController.navigate(Screen.PlaytestSetup.createRoute(deckId))
                    },
                    onReviewSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId, "REVIEW"))
                    },
                    onNavigateToCommunityDecksByCard = { cardName ->
                        navController.navigate(Screen.CommunityDecksByCard.createRoute(cardName))
                    },
                    onNavigateToCommunityDeckDetail = { archidektId ->
                        navController.navigate(Screen.CommunityDeckDetail.createRoute(archidektId))
                    },
                    onNavigateToWizard = { archetype, theme, tribe, colors, seeds ->
                        navController.navigate(Screen.DeckWizard.createRoute(archetype, theme, tribe, colors, seeds))
                    },
                    onNavigateToMassiveAddCards = {
                        navController.navigate(Screen.CollectionMassiveAddCard.route)
                    },
                )
            }

            // ── Deck Builder v2 wizard (docs/plans/deck-builder-v2-plan.md §3.4) ─────
            composable(
                route = Screen.DeckWizard.route,
                arguments = listOf(
                    navArgument("archetype") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    navArgument("theme") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    navArgument("tribe") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    navArgument("colors") { type = NavType.StringType; defaultValue = ""; nullable = false },
                    navArgument("seeds") { type = NavType.StringType; defaultValue = ""; nullable = false },
                ),
            ) {
                DeckWizardScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeckStudio = { deckId ->
                        // Replace the wizard on the back stack with Deck Studio so system back from
                        // Studio returns to whatever screen opened the wizard, not back to Result.
                        navController.navigate(Screen.DeckStudio.createRoute(deckId)) {
                            popUpTo(Screen.DeckWizard.route) { inclusive = true }
                        }
                    },
                    onCardClick = { id -> navController.navigate(Screen.CollectionCardDetail.createRoute(id)) },
                )
            }

            // ── Stats ─────────────────────────────────────────────────────────
            composable(Screen.Stats.route) {
                StatsScreen(
                    onCardClick = { id, key ->
                        navController.navigate(
                            Screen.CollectionCardDetail.createRoute(id, key)
                        )
                    },
                    onBackClick = navController::popBackStack,
                    onReviewSurvey = { sessionId ->
                        navController.navigate(Screen.GameSurvey.createRoute(sessionId, "REVIEW"))
                    },
                    onDeckClick = { deckId ->
                        navController.navigate(Screen.DeckStudio.createRoute(deckId))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }

            // ── Settings ──────────────────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageNewsSources = { navController.navigate(Screen.NewsSourcesSettings.route) },
                    onManageTagDictionary = { navController.navigate(Screen.TagDictionary.route) },
                    onManageAccount = { navController.navigate(Screen.AccountManagement.route) },
                )
            }

            composable(Screen.TagDictionary.route) {
                TagDictionaryScreen(onBack = { navController.popBackStack() })
            }

            // ── News ──────────────────────────────────────────────────────────
            composable(Screen.News.route) {
                NewsScreen(
                    onBack = { navController.popBackStack() },

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
                val videoId = Uri.decode(backStackEntry.arguments?.getString("videoId") ?: return@composable)
                val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
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
                    onBack = {navController.popBackStack()},
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

            // Phase C: Drafting + Result collapsed into one destination — DraftSimulatorScreen hosts
            // both the active-draft "Picks" tab and the deck-preview/save "Deck" tab, switched via an
            // in-screen TabRow instead of a second nav hop. See Screen.DraftSimDrafting's KDoc.
            composable(
                route = Screen.DraftSimDrafting.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                DraftSimulatorScreen(
                    onDeckSaved = { deckId ->
                        // Open the newly created deck in Deck Studio instead of discarding it —
                        // pop the whole draft flow (setup/drafting) off the back stack so Deck
                        // Studio replaces it, mirroring the wizard's "created X, now edit X"
                        // pattern (Screen.DeckWizard -> Screen.DeckStudio, above). fromDraft=true
                        // (E.10a) special-cases Deck Studio's own onBack to land on Collection's
                        // Decks tab instead of Screen.Draft (the set browser) once the user leaves.
                        navController.navigate(Screen.DeckStudio.createRoute(deckId, fromDraft = true)) {
                            popUpTo(Screen.Draft.route) { inclusive = false }
                        }
                    },
                    // E.10b/E.10c: reached ONLY after DraftSimulatorScreen's own in-screen
                    // MagicAlertDialog confirms the user actually wants to leave (cancel an
                    // in-progress draft, or exit the Deck tab without saving) — this callback never
                    // fires on a bare tap, only on confirm.
                    //
                    // F.6 fix: "go to the set browser list" (Screen.Draft) in the original brief was
                    // a mistake — the correct target is the SPECIFIC set's detail page the user had
                    // open before starting the draft (Screen.DraftSetDetail), which is still sitting
                    // on the back stack below DraftSimSetup/DraftSimDrafting untouched (the nav chain
                    // here is always Draft -> DraftSetDetail -> DraftSimSetup -> DraftSimDrafting —
                    // DraftSimSetup only navigates forward to DraftSimDrafting, so DraftSetDetail is
                    // always present). A pure popBackStack (not navigate+popUpTo) is the right tool:
                    // it reveals that EXISTING entry with its original setCode/setName/etc. args
                    // already resolved, instead of pushing a brand-new one that would need the args
                    // re-supplied. The old `navigate(Screen.Draft.route){ popUpTo(Screen.Draft.route)
                    // { inclusive = true } }` was doubly wrong: (1) wrong target, and (2) `navigate`
                    // ALWAYS pushes a new entry after popping, even with a matching popUpTo — so if
                    // Compose Navigation's popUpTo-target-not-found handling ever silently no-ops
                    // (behavior that has varied across library versions) instead of throwing, the old
                    // DraftSetDetail/DraftSimSetup/DraftSimDrafting entries would stay stacked
                    // UNDER a freshly-pushed duplicate Screen.Draft, reachable again via further
                    // system-back presses. A real `popBackStack(route, inclusive)` always REMOVES the
                    // entries above the target, full stop — no ambiguity about whether the target was
                    // found — which structurally rules out that stale-entry class of bug. The
                    // defensive `popBackStack()` fallback only fires if DraftSetDetail is somehow not
                    // on the stack (e.g. a future entry point that bypasses it), so cancelling never
                    // strands the user.
                    onBack = {
                        if (!navController.popBackStack(Screen.DraftSetDetail.route, inclusive = false)) {
                            navController.popBackStack()
                        }
                    },
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
                    onManageAccountClick = { navController.navigate(Screen.AccountManagement.route) },
                    initialTab = initialTab,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Account management (Phase 4b) ───────────────────────────────────
            composable(Screen.AccountManagement.route) {
                AccountManagementScreen(
                    onBack = { navController.popBackStack() },
                    // Both "Change email" and "Change password"/"Set a password" now navigate
                    // DIRECTLY to their final-step screen — no intermediate reauthentication-code
                    // gate. Email is protected by Supabase's "Secure email change" double-confirm
                    // (see AuthRepository.confirmEmailUpdate's KDoc); password is protected by
                    // Supabase's "Require current password when updating" setting, enforced
                    // server-side on the updateUser call itself (see AuthRepository.updatePassword's
                    // KDoc). launchSingleTop guards against a rapid double-tap pushing two
                    // destinations onto the back stack.
                    onNavigateToUpdateEmail = {
                        navController.navigate(Screen.UpdateEmail.route) { launchSingleTop = true }
                    },
                    onNavigateToUpdatePassword = { requireCurrentPassword ->
                        navController.navigate(
                            Screen.UpdatePassword.routeWithRequireCurrentPassword(requireCurrentPassword)
                        ) { launchSingleTop = true }
                    },
                    onSignedOut = {
                        // Sign-out/delete-account both leave the user unauthenticated — return to
                        // Profile's Unauthenticated card rather than staying on an account screen
                        // that no longer makes sense.
                        navController.navigate(Screen.Profile.baseRoute) {
                            popUpTo(Screen.AccountManagement.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Screen.UpdateEmail.route) {
                UpdateEmailScreen(
                    onBack = { navController.popBackStack() },
                    onEmailUpdated = {
                        navController.popBackStack(Screen.AccountManagement.route, inclusive = false)
                    },
                )
            }

            composable(
                route = Screen.UpdatePassword.route,
                arguments = listOf(
                    navArgument("requireCurrentPassword") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                val requireCurrentPassword =
                    backStackEntry.arguments?.getBoolean("requireCurrentPassword") ?: false
                UpdatePasswordScreen(
                    requireCurrentPassword = requireCurrentPassword,
                    onBack = { navController.popBackStack() },
                    onPasswordUpdated = {
                        navController.popBackStack(Screen.AccountManagement.route, inclusive = false)
                    },
                )
            }

            // ── "Forgot password" recovery-link completion ──────────────────────
            //
            // Supabase's IMPLICIT auth flow (this project's default — see SupabaseClientFactory.kt)
            // delivers the recovery callback to `manahub://auth` with the session tokens AND
            // `type=recovery` in the URL FRAGMENT (never a query param under implicit flow).
            // MainActivity.handleSupabaseAuthDeepLink calls supabaseClient.handleDeeplinks(intent)
            // unconditionally for every `manahub://auth` callback — this is what imports the
            // temporary, fully-authenticated recovery UserSession.
            //
            // ROUTING (hardened 2026-08-18): reaching this screen is now driven ENTIRELY by the
            // reactive `LaunchedEffect(recoverySessionState)` near the top of this function, which
            // observes AuthRepository.sessionState directly and navigates here the instant it sees
            // Authenticated(isRecoverySession = true) — see that effect's KDoc for the full
            // rationale (it replaced a MainActivity-side PushDeeplinkRouter enqueue that was
            // process-death-fragile). This destination no longer relies on the
            // `manahub://auth/recovery` deep link below being triggered by anyone; the pattern is
            // kept declared only as a harmless, unused secondary entry point (NavController deep
            // link matching), not as the routing mechanism.
            composable(
                route = Screen.ResetPasswordConfirm.route,
                deepLinks = listOf(navDeepLink { uriPattern = "manahub://auth/recovery" }),
            ) {
                ResetPasswordConfirmScreen(
                    onBack = {
                        // HARDENED 2026-08-17 → 2026-08-18 (password-recovery-hardening-plan
                        // §3.4, the "onBack leaves an authenticated recovery session" fix): merely
                        // popping the back stack (the 2026-08-17 defense-in-depth fix) left the
                        // recovery session itself fully authenticated and active — a user tapping
                        // Back landed on whatever screen was underneath (e.g. ProfileScreen),
                        // rendered fully signed in via a session that was only ever supposed to be
                        // good for setting a new password. Abandoning the flow must actually END
                        // that session, not just navigate away from it.
                        //
                        // Clears the pending-recovery marker AND signs out with SignOutScope.LOCAL
                        // (offline-safe, cannot fail, and does not revoke the user's sessions on
                        // their other devices — deliberately NOT GLOBAL, see
                        // AuthRepositoryImpl.abandonRecoverySession's KDoc). AuthRepositoryImpl is
                        // resolved via a narrow downcast of the interface-typed koinInject() above:
                        // this method is deliberately NOT on the AuthRepository interface (it is an
                        // Android-deep-link-specific mechanism with no web equivalent yet — adding
                        // it to the shared interface would force an unrelated no-op onto the web
                        // target purely to satisfy the compiler). The downcast cannot fail in
                        // practice (Koin's single<AuthRepository> binding always constructs
                        // AuthRepositoryImpl here), but the fallback branch still clears the marker
                        // directly so a future sessionState emission can never wrongly re-satisfy
                        // isActiveRecoveryFlow via a stale marker even if it somehow did.
                        // SEQUENCED, not fire-and-forget (T6 adversarial-audit MEDIUM fix,
                        // 2026-08-18): this used to launch the cleanup and call navigate() on the
                        // very next statement without awaiting it. The top-level
                        // LaunchedEffect(recoverySessionState, pendingRecoveryMarker) is not scoped
                        // to this destination and keeps observing — in the window after
                        // navigate(Home) but before the marker-clear/sign-out actually committed,
                        // currentRoute was Home (≠ ResetPasswordConfirm) while sessionState and the
                        // marker were BOTH still valid, so shouldRouteToRecoveryScreen was satisfied
                        // again and the effect yanked the user straight back onto the form — which
                        // was still genuinely submittable, since nothing had been cleared yet. One
                        // coroutine now runs the cleanup to completion FIRST and navigates only
                        // after, mirroring confirmPasswordReset's success path, which already
                        // completes its cleanup before ever returning to its caller.
                        recoveryCoroutineScope.launch {
                            val authRepositoryImpl = authRepository as? AuthRepositoryImpl
                            if (authRepositoryImpl != null) {
                                authRepositoryImpl.abandonRecoverySession()
                            } else {
                                FirebaseCrashlytics.getInstance()
                                    .recordException(IllegalStateException("AuthRepository was not AuthRepositoryImpl in ResetPasswordConfirmScreen.onBack"))
                                userPreferencesDataStore.clearPendingRecoveryMarker()
                            }
                            // Do NOT merely pop the back stack — that can land on a screen that
                            // assumes an authenticated context (the exact bug this fix closes).
                            // Always land on the app's unauthenticated entry point with a clean
                            // stack, and only AFTER the cleanup above has actually committed.
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onPasswordReset = {
                        // The marker-clear + GLOBAL sign-out for a SUCCESSFUL reset already happened
                        // inside AuthRepositoryImpl.confirmPasswordReset itself (before it returned
                        // Success) — deliberately not repeated here, since gating a security-critical
                        // state transition on this UI callback surviving to run is exactly the class
                        // of process-death-fragile bug this whole plan exists to eliminate. This
                        // callback is pure navigation + messaging.
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                        inviteToastState.show(
                            context.getString(R.string.auth_error_password_set_session_revoked),
                            MagicToastType.SUCCESS,
                        )
                    },
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
                // KMP migration — Tournament Koin island. The VM is entry-scoped (one instance per
                // TournamentDetail back-stack entry) and its SavedStateHandle is populated from this
                // entry's CreationExtras (carrying the `tournamentId` nav arg) — the exact equivalent of
                // the old hiltViewModel(entry).
                val tournamentVm: TournamentViewModel = koinViewModel(viewModelStoreOwner = entry)
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
                val setupVm: GameSetupViewModel = koinViewModel()
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
                    onOnlineHostGameStart = { sessionId, mode, playerCount, guestToken ->
                        val configs = (0 until playerCount).mapIndexed { i, _ ->
                            PlayerConfig(
                                id        = i,
                                name      = "",
                                theme     = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                                isAppUser = i == 0,
                            )
                        }
                        gameVm.initFromOnlineSession(sessionId, 0, configs, mode, guestToken = guestToken)
                        navController.navigate(Screen.GamePlay.createRoute(mode.name, playerCount)) {
                            popUpTo(Screen.GameSetup.baseRoute) { inclusive = true }
                        }
                    },
                    onOnlineJoinGameStart = { sessionId, slotIndex, modeStr, playerCount, guestToken ->
                        val mode = runCatching { GameMode.valueOf(modeStr) }.getOrDefault(GameMode.STANDARD)
                        val configs = (0 until playerCount).mapIndexed { i, _ ->
                            PlayerConfig(
                                id        = i,
                                name      = "",
                                theme     = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                                isAppUser = i == slotIndex,
                            )
                        }
                        gameVm.initFromOnlineSession(sessionId, slotIndex, configs, mode, guestToken = guestToken)
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
                            popUpTo(Screen.Collection.baseRoute) { inclusive = false }
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
                // When online sessions are flag-disabled, surface a toast first — a stale
                // bookmark/back-stack entry should read as "feature unavailable", not a silent
                // no-op that looks like nothing happened.
                LaunchedEffect(Unit) {
                    if (!FeatureFlags.Online.ONLINE_SESSIONS_ENABLED) {
                        inviteToastState.show(context.getString(R.string.online_sessions_unavailable), MagicToastType.INFO)
                    }
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
                // When online sessions are flag-disabled, drop the code and surface a toast instead
                // of silently landing on GameSetup as if the manahub://join/{code} deep link never
                // arrived — see OnlineFeatureFlags' KDoc.
                LaunchedEffect(code) {
                    val dest = if (!FeatureFlags.Online.ONLINE_SESSIONS_ENABLED) {
                        if (code.isNotBlank()) {
                            inviteToastState.show(context.getString(R.string.online_sessions_unavailable), MagicToastType.INFO)
                        }
                        Screen.GameSetup.baseRoute
                    } else if (code.isNotBlank()) {
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
                        message    = stringResource(
                            R.string.playtest_session_expired,
                        ),
                        retryLabel = stringResource(
                            R.string.action_back,
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
                                popUpTo(Screen.Collection.baseRoute) { inclusive = false }
                            }
                        }
                    },
                )
            }
            composable(
                route = Screen.Competitive.route,
            ){
                CompetitiveScreen(
                    onBack = {
                        navController.popBackStack()
                    }
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
        } // end SharedTransitionLayout
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
