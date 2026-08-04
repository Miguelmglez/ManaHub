package com.mmg.manahub.web.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.mmg.manahub.core.ui.layout.AdaptiveNavItem
import com.mmg.manahub.core.ui.layout.AdaptiveScaffold
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.web.auth.AuthScreen
import com.mmg.manahub.web.carddetail.CardDetailScreen
import com.mmg.manahub.web.collection.CollectionScreen
import com.mmg.manahub.web.deckeditor.DeckEditorScreen
import com.mmg.manahub.web.decks.DeckListScreen
import com.mmg.manahub.web.friends.FriendsScreen
import com.mmg.manahub.web.home.HomeScreen
import com.mmg.manahub.web.profile.ProfileScreen
import com.mmg.manahub.web.search.CardSearchScreen
import com.mmg.manahub.web.settings.SettingsScreen
import com.mmg.manahub.web.theme.ThemeShowcaseScreen
import com.mmg.manahub.web.trades.TradeThreadScreen
import com.mmg.manahub.web.trades.TradesScreen
import kotlinx.browser.window
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
//  Routes (web roadmap W4a) — type-safe kotlinx.serialization route objects, the
//  org.jetbrains.androidx.navigation (CMP navigation-compose-multiplatform) idiom.
//
//  DELIBERATELY SCOPED DOWN from the master plan's original W4 description: these routes are
//  `:webApp`-only and have NO relationship to Android's live `Screen.kt` sealed-class routes
//  (`app/src/main/java/com/mmg/manahub/app/navigation/Screen.kt`). Unifying the two onto one
//  shared route table (swapping Android's `androidx.navigation:navigation-compose` for this same
//  JetBrains artifact too) is an explicitly DEFERRED, separate, opt-in future task — see the
//  module KDoc on [App][com.mmg.manahub.web.App] and `docs/plans/kmp-migration-progress.md`.
//
//  `HomeRoute` and `ThemeRoute` rendered the SAME screen ([ThemeShowcaseScreen]) from W4a through
//  W4c — a placeholder fallthrough inherited from the pre-W4a index-switch (both "Home" and
//  "Theme" nav items pointed at the same screen before real navigation existed). Web roadmap W4d
//  replaces [HomeRoute]'s content with a real, distinct [HomeScreen] — see that screen's own KDoc
//  for scope. [ThemeRoute] keeps [ThemeShowcaseScreen] as its own dedicated settings-like surface
//  (the 12-palette picker is still genuinely useful on its own tab).
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
@SerialName("home")
private object HomeRoute

@Serializable
@SerialName("search")
private object SearchRoute

@Serializable
@SerialName("decks")
private object DecksRoute

@Serializable
@SerialName("collection")
private object CollectionRoute

@Serializable
@SerialName("theme")
private object ThemeRoute

@Serializable
@SerialName("account")
private object AccountRoute

/**
 * Web scope expansion (Settings -> Profile -> Add Card, approved 2026-08-04) — a zero-arg route
 * like [ThemeRoute]/[AccountRoute], but deliberately NOT given an [AdaptiveNavItem] entry below.
 * The nav rail/bottom bar already carries 6 top-level tabs; adding Settings as a 7th (with Profile
 * and Add Card still to come) would crowd [ManaWindowSizeClass.COMPACT]'s bottom bar further, so
 * this is reachable only from [AuthScreen]'s "Settings" row instead — matching Android's own
 * `feature/settings/` UX precedent (Settings hangs off the account surface, not the primary tab
 * bar).
 */
@Serializable
@SerialName("settings")
private object SettingsRoute

/**
 * Web scope expansion (Settings -> Profile -> Add Card, approved 2026-08-04) -- same shape and
 * same rationale as [SettingsRoute]: a zero-arg route reachable only from
 * [com.mmg.manahub.web.auth.AuthScreen]'s "Profile" row, deliberately NOT given an
 * [AdaptiveNavItem] entry (would be an 8th top-level tab counting Settings, well past what
 * [ManaWindowSizeClass.COMPACT]'s bottom bar should carry). Matches Android's own
 * `feature/profile/` UX precedent (Profile hangs off the account surface too, via
 * `Screen.Profile`, not the primary bottom bar).
 */
@Serializable
@SerialName("profile")
private object ProfileRoute

/**
 * Web scope expansion (Friends slice, approved 2026-08-04, second wave after Settings/Profile/Add
 * Card) -- same shape and rationale as [SettingsRoute]/[ProfileRoute]: reachable only from
 * [com.mmg.manahub.web.auth.AuthScreen]'s "Friends" row, deliberately NOT given an
 * [AdaptiveNavItem] entry (a 9th account-adjacent surface would crowd
 * [ManaWindowSizeClass.COMPACT]'s bottom bar further, same reasoning as Settings/Profile).
 */
@Serializable
@SerialName("friends")
private object FriendsRoute

/**
 * Trades slice (web scope expansion, approved 2026-08-04, Friends + Trades wave) -- UNLIKE
 * [SettingsRoute]/[ProfileRoute]/[FriendsRoute] above, Trades DOES get its own [AdaptiveNavItem]
 * entry (a 7th top-level tab) -- it is a primary feature (real proposal negotiation, not an
 * account-adjacent settings surface), per the task brief's explicit call for real nav-bar
 * presence.
 */
@Serializable
@SerialName("trades")
private object TradesRoute

/**
 * Web roadmap W4b — the FIRST parameterized route in this graph (every W4a route was a zero-arg
 * `object`). A destination you navigate INTO from a card tile ([SearchRoute]/[CollectionRoute]
 * results), never a top-level nav item, so it deliberately has no [AdaptiveNavItem] entry below.
 */
@Serializable
@SerialName("card")
private data class CardDetailRoute(val scryfallId: String)

/**
 * Web roadmap W4c — the SECOND parameterized route (same shape as [CardDetailRoute]: a
 * destination you navigate INTO from [com.mmg.manahub.web.decks.DeckListScreen]'s deck rows, never
 * a top-level nav item, so no [AdaptiveNavItem] entry either). Encodes identically to
 * [CardDetailRoute] — a required `String` arg as a URL path segment (`#deck/<deckId>`), per the
 * W4b finding — parsed with the SAME dedicated prefix-strip approach, not the exact-match table.
 */
@Serializable
@SerialName("deck")
private data class DeckEditorRoute(val deckId: String)

/**
 * Trades slice (web scope expansion, approved 2026-08-04, Friends + Trades wave) -- the THIRD
 * parameterized route (same shape as [CardDetailRoute]/[DeckEditorRoute]): a destination you
 * navigate INTO from [TradesScreen]'s proposal rows, never a top-level nav item. Encodes as
 * `#trade/<rootProposalId>`.
 */
@Serializable
@SerialName("trade")
private data class TradeThreadRoute(val rootProposalId: String)

/**
 * The routes above, keyed by their [kotlinx.serialization] serial name (the exact string
 * [androidx.navigation.bindToBrowserNavigation]'s DEFAULT `getBackStackEntryRoute` writes into the
 * URL fragment for a no-argument object route — confirmed live: the fragment reads e.g.
 * `#search`, not a percent-encoded path). Used only by [bindWebBrowserNavigation]'s one-time
 * initial-fragment lookup below. [CardDetailRoute]/[DeckEditorRoute] are NOT in this map (they
 * carry an argument, so their fragments are `#card/<scryfallId>`/`#deck/<deckId>`, not a bare
 * serial name) — see the dedicated branches in [bindWebBrowserNavigation] instead.
 */
private val ROUTES_BY_SERIAL_NAME: Map<String, Any> = mapOf(
    "home" to HomeRoute,
    "search" to SearchRoute,
    "decks" to DecksRoute,
    "collection" to CollectionRoute,
    "theme" to ThemeRoute,
    "account" to AccountRoute,
    "settings" to SettingsRoute,
    "profile" to ProfileRoute,
    "friends" to FriendsRoute,
    "trades" to TradesRoute,
)

/**
 * Binds [this] [NavHostController] to browser URL routing (web roadmap W4a).
 *
 * [androidx.navigation.bindToBrowserNavigation] (`@ExperimentalBrowserHistoryApi`) reflects the
 * CURRENT route into the URL fragment going forward and listens for the browser's back/forward
 * buttons — but it does **not**, on its own, replay the page's OWN initial URL fragment into a
 * matching `navigate()` call. Verified live (Playwright): loading fresh at
 * `.../#search` with no pre-navigation step resolved back to [HomeRoute] (the `NavHost`
 * `startDestination`), not [SearchRoute] — a real deep-link-on-reload gap, not a hypothetical one.
 * Fixed the same way the official CMP docs sample does it: parse `window.location.hash` and
 * `navigate()` to the matching route BEFORE calling `bindToBrowserNavigation()`, in the SAME
 * suspend function/coroutine so the ordering is guaranteed relative to itself.
 *
 * **`currentBackStackEntryFlow.first()` gate, and why it's needed even though this function is
 * already called from a [LaunchedEffect] declared textually AFTER [NavHost] in the same
 * composable:** co-locating the effect after `NavHost` is NOT enough on its own here, because
 * [AdaptiveScaffold]'s `content` lambda (which is where the `NavHost` call actually lives) is
 * wrapped in `remember { movableContentOf { ... } }` (see that component's own KDoc) — Compose can
 * relocate/defer a `movableContentOf` region's first real composition by a frame relative to the
 * composable that requested it, so on a COLD initial composition `NavHost` calling
 * `navController.graph = ...` is not guaranteed to have already run by the time this
 * [LaunchedEffect] starts executing. Calling `navigate()`/reading `.graph` before that attach threw
 * a real, reproducible `IllegalStateException: You must call setGraph() before calling getGraph()`
 * on a fresh page load at a non-root URL (verified live: the exception was swallowed internally —
 * no `pageerror`, just a `console.error` — but it still permanently prevented ANY canvas/UI from
 * ever mounting for that page load). Awaiting the FIRST emission of
 * [NavHostController.currentBackStackEntryFlow] is a direct, race-free readiness signal (it only
 * emits once the graph is attached and the start destination's entry exists) instead of relying on
 * Compose's effect-vs-composition ordering, which [AdaptiveScaffold]'s `movableContentOf` usage
 * makes unsafe to assume here.
 *
 * **Web roadmap W4b addendum — [CardDetailRoute] deep-link.** [ROUTES_BY_SERIAL_NAME] only covers
 * the zero-arg routes (their fragment IS their serial name, verbatim). [CardDetailRoute] carries a
 * `scryfallId` arg, so `navigation-compose-multiplatform`'s default route encoding (the same
 * required-arg-as-path-segment scheme stock AndroidX Navigation typed routes use — confirmed via
 * the `navigation-common` klib decompile referenced in the W4a memory addendum, which surfaced
 * `RouteEncoder`/`generateRouteWithArgs` internals) produces a fragment shaped `#card/<scryfallId>`,
 * not the bare `#card` a zero-arg route would get. Parsed with a simple prefix strip rather than a
 * lookup table entry, since the value portion is unbounded (unlike the six fixed top-level routes).
 *
 * **Web roadmap W4c addendum — [DeckEditorRoute] deep-link.** Same shape, same fix: a
 * `"deck/"`-prefix branch alongside [CardDetailRoute]'s `"card/"` one.
 */
@OptIn(ExperimentalBrowserHistoryApi::class)
private suspend fun NavHostController.bindWebBrowserNavigation() {
    currentBackStackEntryFlow.first()

    val initialFragment = window.location.hash.removePrefix("#")
    val cardDetailId = initialFragment.takeIf { it.startsWith("card/") }
        ?.removePrefix("card/")
        ?.takeIf { it.isNotBlank() }
    val deckEditorId = initialFragment.takeIf { it.startsWith("deck/") }
        ?.removePrefix("deck/")
        ?.takeIf { it.isNotBlank() }
    val tradeThreadId = initialFragment.takeIf { it.startsWith("trade/") }
        ?.removePrefix("trade/")
        ?.takeIf { it.isNotBlank() }
    val initialRoute = ROUTES_BY_SERIAL_NAME[initialFragment]
    when {
        cardDetailId != null -> navigate(CardDetailRoute(scryfallId = cardDetailId)) { launchSingleTop = true }
        deckEditorId != null -> navigate(DeckEditorRoute(deckId = deckEditorId)) { launchSingleTop = true }
        tradeThreadId != null -> navigate(TradeThreadRoute(rootProposalId = tradeThreadId)) { launchSingleTop = true }
        initialRoute != null && initialRoute != HomeRoute -> navigate(initialRoute) { launchSingleTop = true }
    }
    bindToBrowserNavigation()
}

/**
 * The real `:webApp` navigation graph (web roadmap W4a), replacing the old `selectedNavIndex`
 * `when` block in `App.kt`. Builds a [NavHost] inside the existing [AdaptiveScaffold] shell (W1,
 * untouched internals) so every destination keeps the same responsive nav chrome + content
 * clamp/padding it already had.
 *
 * [navController] is CREATED by the caller ([com.mmg.manahub.web.App], via `rememberNavController()`)
 * but bound to browser URL routing ([bindWebBrowserNavigation]) HERE, in a [LaunchedEffect] declared
 * AFTER the [NavHost] call below, in the SAME composable. This co-location matters: an earlier
 * version bound in [com.mmg.manahub.web.App] (the PARENT composable, one level up) instead, and hit
 * a real ordering bug on a fresh deep-link page load — `navController.graph` (accessed inside
 * `navigate()`, which [bindWebBrowserNavigation] calls for a non-root initial URL fragment) threw
 * `IllegalStateException: You must call setGraph() before calling getGraph()`, because the parent's
 * [LaunchedEffect] could fire before [NavHost] (a CHILD composable, further down the tree) finished
 * attaching its graph to the shared [navController]. Declaring the effect in the SAME composable as
 * [NavHost] avoids that race entirely — confirmed live (Playwright): a fresh page load at
 * `.../#search` now resolves to [SearchRoute] with zero console errors.
 *
 * [selectedTheme]/[onThemeSelected] are hoisted one level up (in `App.kt`, alongside the
 * [com.mmg.manahub.core.ui.theme.MagicTheme] wrapper) rather than owned here, since the active
 * theme must be known BEFORE `MagicTheme` wraps this whole graph. Since web roadmap W4d, only
 * [ThemeRoute] reads/writes this state ([HomeRoute] renders the real [HomeScreen] instead) —
 * still hoisted at this level so a future theme picker moved elsewhere would not need rewiring.
 */
@Composable
fun WebNavGraph(
    navController: NavHostController,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateToTopLevel(route: Any) {
        navController.navigate(route) {
            // Standard top-level-tab pattern: never pile up a deep back stack across tab
            // switches, but keep each tab's own scroll/draft state via save/restoreState.
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    AdaptiveScaffold(
        navItems = listOf(
            AdaptiveNavItem(
                label = "Home",
                icon = Icons.Default.Home,
                selected = currentDestination?.route == HomeRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(HomeRoute) },
            ),
            AdaptiveNavItem(
                label = "Search",
                icon = Icons.Default.Search,
                selected = currentDestination?.route == SearchRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(SearchRoute) },
            ),
            AdaptiveNavItem(
                label = "Decks",
                icon = Icons.Default.Style,
                selected = currentDestination?.route == DecksRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(DecksRoute) },
            ),
            AdaptiveNavItem(
                label = "Collection",
                icon = Icons.Default.ViewModule,
                selected = currentDestination?.route == CollectionRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(CollectionRoute) },
            ),
            AdaptiveNavItem(
                label = "Trades",
                icon = Icons.Default.SwapHoriz,
                selected = currentDestination?.route == TradesRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(TradesRoute) },
            ),
            AdaptiveNavItem(
                label = "Theme",
                icon = Icons.Default.Palette,
                selected = currentDestination?.route == ThemeRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(ThemeRoute) },
            ),
            AdaptiveNavItem(
                label = "Account",
                icon = Icons.Default.AccountCircle,
                selected = currentDestination?.route == AccountRoute.serializer().descriptor.serialName,
                onClick = { navigateToTopLevel(AccountRoute) },
            ),
        ),
    ) { windowSizeClass ->
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    windowSizeClass = windowSizeClass,
                    onNavigateSearch = { navigateToTopLevel(SearchRoute) },
                    onNavigateDecks = { navigateToTopLevel(DecksRoute) },
                    onNavigateCollection = { navigateToTopLevel(CollectionRoute) },
                    onDeckClick = { deckId -> navController.navigate(DeckEditorRoute(deckId)) },
                    onCardClick = { scryfallId -> navController.navigate(CardDetailRoute(scryfallId)) },
                )
            }
            composable<SearchRoute> {
                CardSearchScreen(
                    windowSizeClass = windowSizeClass,
                    onCardClick = { scryfallId -> navController.navigate(CardDetailRoute(scryfallId)) },
                )
            }
            composable<DecksRoute> {
                DeckListScreen(onDeckClick = { deckId -> navController.navigate(DeckEditorRoute(deckId)) })
            }
            composable<CollectionRoute> {
                CollectionScreen(
                    windowSizeClass = windowSizeClass,
                    onCardClick = { scryfallId -> navController.navigate(CardDetailRoute(scryfallId)) },
                )
            }
            composable<ThemeRoute> {
                ThemeShowcaseScreen(
                    windowSizeClass = windowSizeClass,
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                )
            }
            composable<AccountRoute> {
                AuthScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                    onOpenFriends = { navController.navigate(FriendsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    onSignedOut = {
                        navController.navigate(AccountRoute) {
                            popUpTo(AccountRoute) { inclusive = true }
                        }
                    },
                )
            }
            composable<FriendsRoute> {
                FriendsScreen()
            }
            composable<TradesRoute> {
                TradesScreen(onProposalClick = { rootProposalId -> navController.navigate(TradeThreadRoute(rootProposalId)) })
            }
            composable<TradeThreadRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TradeThreadRoute>()
                TradeThreadScreen(rootProposalId = route.rootProposalId)
            }
            composable<CardDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CardDetailRoute>()
                CardDetailScreen(
                    scryfallId = route.scryfallId,
                    windowSizeClass = windowSizeClass,
                    onBack = { navController.navigateUp() },
                )
            }
            composable<DeckEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<DeckEditorRoute>()
                DeckEditorScreen(
                    deckId = route.deckId,
                    windowSizeClass = windowSizeClass,
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }

    // Declared AFTER NavHost (above) in this SAME composable -- see the ordering-bug note in the
    // class KDoc. By the time this LaunchedEffect's coroutine body runs, NavHost has already
    // attached its graph to navController during this same composition.
    LaunchedEffect(navController) {
        navController.bindWebBrowserNavigation()
    }
}
