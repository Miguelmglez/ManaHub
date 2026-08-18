package com.mmg.manahub.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A single tab in a [ManaTabRow]. [label] is expected to already be uppercased/localized by the
 * caller — this component never transforms it (mirrors [CardTagChip]'s "pass the already-resolved
 * text" convention), since callers on different platforms uppercase with different locale rules
 * (`Locale.getDefault()` on Android vs. a KMP-safe equivalent on web).
 */
data class ManaTabItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * ManaHub's canonical sub-tab strip — extracted (Phase E, E.8) from
 * `CollectionScreen`'s Cards/Decks/Trades [TabRow], which is the "global" visual reference the
 * design calls for: `backgroundSecondary` at 90% alpha, `primaryAccent` content color, no divider,
 * and `labelLarge`-styled, already-uppercased tab text. `commonMain` (pure Compose/tokens, no
 * Android-specific API) per the KMP-first directive — both current call sites
 * (`CollectionScreen`'s Cards/Decks/Trades row and `DraftSimulatorScreen`'s Picks/Deck row) are
 * Android-only today, but the component itself has zero platform coupling.
 *
 * @param items the tabs to render, in order. Each carries its own selected/onClick.
 */
@Composable
fun ManaTabRow(items: List<ManaTabItem>, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = mc.backgroundSecondary.copy(alpha = 0.9f),
        contentColor = mc.primaryAccent,
        divider = {},
        modifier = modifier,
    ) {
        items.forEach { item ->
            Tab(
                selected = item.selected,
                onClick = item.onClick,
                text = { Text(text = item.label, style = ty.labelLarge) },
            )
        }
    }
}
