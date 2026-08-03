package com.mmg.manahub.web

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mmg.manahub.core.ui.layout.AdaptiveNavItem
import com.mmg.manahub.core.ui.layout.AdaptiveScaffold
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.web.auth.AuthScreen
import com.mmg.manahub.web.collection.CollectionScreen
import com.mmg.manahub.web.decks.DeckListScreen
import com.mmg.manahub.web.search.CardSearchScreen
import com.mmg.manahub.web.theme.ThemeShowcaseScreen

/**
 * Root composable for `:webApp` (web roadmap W1, extended in W2a/W3b/W3c/W3d). Real
 * destination-level navigation (back stack, deep links) lands in W4 — the [AdaptiveNavItem]s here
 * are still simple index-switched placeholders exercising [AdaptiveScaffold]'s responsive nav
 * chrome (bottom bar / collapsed rail / expanded rail) end-to-end, EXCEPT "Account" (wired to
 * [AuthScreen], W2a), "Search" (wired to [CardSearchScreen], W3b — the first REAL MVP screen,
 * backed by `WebCardRepository`/live Scryfall data, not a showcase), "Decks" (wired to
 * [DeckListScreen], W3c — backed by `WebDeckRepository`, remote-first CRUD against real Supabase
 * data), and "Collection" (wired to [CollectionScreen], W3d — backed by `WebUserCardRepository`;
 * a card added via the Search tab's tap-to-add flow appears here immediately and survives a full
 * page reload).
 */
@Composable
fun App() {
    var selectedTheme by remember { mutableStateOf<AppTheme>(AppTheme.NeonVoid) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    MagicTheme(theme = selectedTheme) {
        AdaptiveScaffold(
            navItems = listOf(
                AdaptiveNavItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    selected = selectedNavIndex == 0,
                    onClick = { selectedNavIndex = 0 },
                ),
                AdaptiveNavItem(
                    label = "Search",
                    icon = Icons.Default.Search,
                    selected = selectedNavIndex == 1,
                    onClick = { selectedNavIndex = 1 },
                ),
                AdaptiveNavItem(
                    label = "Decks",
                    icon = Icons.Default.Style,
                    selected = selectedNavIndex == 2,
                    onClick = { selectedNavIndex = 2 },
                ),
                AdaptiveNavItem(
                    label = "Collection",
                    icon = Icons.Default.ViewModule,
                    selected = selectedNavIndex == 3,
                    onClick = { selectedNavIndex = 3 },
                ),
                AdaptiveNavItem(
                    label = "Theme",
                    icon = Icons.Default.Palette,
                    selected = selectedNavIndex == 4,
                    onClick = { selectedNavIndex = 4 },
                ),
                AdaptiveNavItem(
                    label = "Account",
                    icon = Icons.Default.AccountCircle,
                    selected = selectedNavIndex == 5,
                    onClick = { selectedNavIndex = 5 },
                ),
            ),
        ) { windowSizeClass ->
            when (selectedNavIndex) {
                1 -> CardSearchScreen(windowSizeClass = windowSizeClass)
                2 -> DeckListScreen()
                3 -> CollectionScreen(windowSizeClass = windowSizeClass)
                5 -> AuthScreen()
                else -> ThemeShowcaseScreen(
                    windowSizeClass = windowSizeClass,
                    selectedTheme = selectedTheme,
                    onThemeSelected = { selectedTheme = it },
                )
            }
        }
    }
}
