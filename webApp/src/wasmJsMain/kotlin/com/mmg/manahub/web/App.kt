package com.mmg.manahub.web

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
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
import com.mmg.manahub.web.theme.ThemeShowcaseScreen

/**
 * Root composable for `:webApp` (web roadmap W1, extended in W2a). Real navigation destinations
 * land in W4 — the [AdaptiveNavItem]s here are still DEMO placeholders exercising
 * [AdaptiveScaffold]'s responsive nav chrome (bottom bar / collapsed rail / expanded rail)
 * end-to-end, EXCEPT "Account", which is now wired to the real [AuthScreen] (web roadmap W2a) so
 * guest sign-in can be exercised end-to-end inside the existing responsive shell.
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
                    label = "Theme",
                    icon = Icons.Default.Palette,
                    selected = selectedNavIndex == 2,
                    onClick = { selectedNavIndex = 2 },
                ),
                AdaptiveNavItem(
                    label = "Account",
                    icon = Icons.Default.AccountCircle,
                    selected = selectedNavIndex == 3,
                    onClick = { selectedNavIndex = 3 },
                ),
            ),
        ) { windowSizeClass ->
            when (selectedNavIndex) {
                3 -> AuthScreen()
                else -> ThemeShowcaseScreen(
                    windowSizeClass = windowSizeClass,
                    selectedTheme = selectedTheme,
                    onThemeSelected = { selectedTheme = it },
                )
            }
        }
    }
}
