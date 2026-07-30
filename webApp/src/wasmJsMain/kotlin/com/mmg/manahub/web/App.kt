package com.mmg.manahub.web

import androidx.compose.material.icons.Icons
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
import com.mmg.manahub.web.theme.ThemeShowcaseScreen

/**
 * Root composable for `:webApp` (web roadmap W1). Real navigation destinations land in W4 — the
 * three [AdaptiveNavItem]s here are DEMO placeholders purely to exercise [AdaptiveScaffold]'s
 * responsive nav chrome (bottom bar / collapsed rail / expanded rail) end-to-end; they are not
 * wired to real screens yet, so selecting one just switches local highlight state.
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
            ),
        ) { windowSizeClass ->
            ThemeShowcaseScreen(
                windowSizeClass = windowSizeClass,
                selectedTheme = selectedTheme,
                onThemeSelected = { selectedTheme = it },
            )
        }
    }
}
