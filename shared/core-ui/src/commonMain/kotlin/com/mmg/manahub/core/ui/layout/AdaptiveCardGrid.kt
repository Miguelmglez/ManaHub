package com.mmg.manahub.core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared reflowing grid: [LazyVerticalGrid] with [GridCells.Adaptive], sized per breakpoint so
 * column density scales with available width instead of a fixed column count.
 *
 * Replaces the inconsistent per-screen `Fixed`/`Adaptive` split found across the Android app today
 * (`CommunityDecksScreen`, `DeckEditorComponents`, `DeckStatsCard`, `DraftScreen`, `HomeScreen` use
 * `GridCells.Fixed(n)` and will NOT reflow on a wide browser window) — those screens migrate to
 * this shared grid as they port to web starting web roadmap W4; this component is designed
 * generically now so that migration is a drop-in swap.
 *
 * @param windowSizeClass drives the default [minSize] — callers rarely need to override it.
 * @param minSize explicit override for [GridCells.Adaptive]'s minimum cell size, when the default
 *   per-breakpoint value (100.dp compact / 140.dp medium+) doesn't fit a specific grid's content
 *   (e.g. a denser icon grid).
 */
@Composable
fun AdaptiveCardGrid(
    windowSizeClass: ManaWindowSizeClass,
    modifier: Modifier = Modifier,
    minSize: Dp = defaultMinSize(windowSizeClass),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        modifier = modifier,
        contentPadding = contentPadding,
        content = content,
    )
}

private fun defaultMinSize(sizeClass: ManaWindowSizeClass): Dp = when (sizeClass) {
    ManaWindowSizeClass.COMPACT -> 100.dp
    ManaWindowSizeClass.MEDIUM,
    ManaWindowSizeClass.EXPANDED,
    ManaWindowSizeClass.LARGE -> 140.dp
}
