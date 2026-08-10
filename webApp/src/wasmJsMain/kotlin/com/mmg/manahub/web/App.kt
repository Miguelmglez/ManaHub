package com.mmg.manahub.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.web.navigation.WebNavGraph
import org.koin.compose.koinInject

/**
 * Root composable for `:webApp` (web roadmap W1, extended in W2a/W3b/W3c/W3d/W4a, and the Settings
 * expansion slice).
 *
 * **W4a note (scope boundary):** this module has its OWN, independent [WebNavGraph] built on the
 * JetBrains CMP `navigation-compose-multiplatform` artifact (`org.jetbrains.androidx.navigation`)
 * — real back stack + browser URL routing (see `WebNavGraph.kt`). This is entirely separate from,
 * and does NOT touch, Android's live production navigation graph
 * (`app/src/main/java/com/mmg/manahub/app/navigation/Screen.kt`/`AppNavGraph.kt`, still on the
 * Android-only `androidx.navigation:navigation-compose` artifact). Unifying the two onto one
 * shared 42-route table is an explicitly DEFERRED, separate, opt-in future task — do not start it
 * as a side effect of touching this file.
 *
 * Only creates the [androidx.navigation.NavHostController] here (`rememberNavController()`) — the
 * browser-history binding itself is owned by [WebNavGraph], co-located with its own [androidx.navigation.compose.NavHost]
 * call (see that file's KDoc for why: an earlier version bound it here instead and hit a real
 * `setGraph()`/`getGraph()` ordering race on a fresh deep-link page load).
 *
 * **Settings expansion slice**: provides [LocalPreferredCurrency] from
 * [UserPreferencesRepository.preferredCurrencyFlow] at the app root, mirroring Android's own
 * `MainActivity.kt` (`LocalPreferredCurrency provides (userPrefs?.preferredCurrency ?: ...)`).
 * Without this, [PreferredCurrency] was a genuinely dead setting on web -- `CardListItem`/
 * `CardGridItem`/`VariantSelectorSheet` (`shared/core-ui` commonMain) all read
 * [LocalPreferredCurrency] to decide which of a card's USD/EUR prices renders first, but nothing
 * on `:webApp` ever provided a non-default value before this, so the setting persisted correctly
 * but had zero visible effect anywhere. Read via [koinInject] (not a ViewModel) since this is a
 * one-shot app-root value, not screen-scoped state.
 */
@Composable
fun App() {
    var selectedTheme by remember { mutableStateOf<AppTheme>(AppTheme.NeonVoid) }
    val navController = rememberNavController()
    val userPreferencesRepository = koinInject<UserPreferencesRepository>()
    val preferredCurrency by userPreferencesRepository.preferredCurrencyFlow
        .collectAsState(initial = PreferredCurrency.EUR)

    MagicTheme(theme = selectedTheme) {
        CompositionLocalProvider(LocalPreferredCurrency provides preferredCurrency) {
            WebNavGraph(
                navController = navController,
                selectedTheme = selectedTheme,
                onThemeSelected = { selectedTheme = it },
            )
        }
    }
}
