package com.mmg.manahub.feature.home.presentation

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import com.mmg.manahub.core.ui.components.MagicSkeletonPulse

/** Crossfade from a widget's skeleton to its content. */
internal const val REVEAL_CROSSFADE_MS = 220

/** Duration of the first-reveal lift (content rises into place). */
internal const val REVEAL_LIFT_MS = 280

/** Distance of the first-reveal lift, in dp. */
internal const val REVEAL_LIFT_DP = 12f

/** Delay added per visible position on a first reveal. */
internal const val REVEAL_STAGGER_MS = 45L

/** Visible positions beyond this share the last stagger slot. */
internal const val REVEAL_STAGGER_CAP = 6

/** Reduced-motion reveal: a short plain fade, no lift, no stagger. */
internal const val REDUCED_MOTION_FADE_MS = 150

/** Placeholder items shown while the board itself is not ready. */
internal const val HOME_BOARD_SKELETON_COUNT = 4

/**
 * Remembers which widgets already played their first reveal in this process. Held by the
 * ViewModel so it survives navigation: returning to Home must never replay the reveal.
 */
class WidgetRevealTracker {
    private val revealed = mutableSetOf<HomeWidgetType>()

    /** True once [type] has been revealed in this process. */
    fun isRevealed(type: HomeWidgetType): Boolean = type in revealed

    /** Marks [type] revealed; returns true only the first time (the caller should animate). */
    fun markRevealed(type: HomeWidgetType): Boolean = revealed.add(type)
}

/** Stagger delay for a first reveal at [visibleIndex]; zero for an off-screen item (null). */
internal fun revealStaggerDelayMs(visibleIndex: Int?): Long =
    if (visibleIndex == null) 0L else REVEAL_STAGGER_MS * visibleIndex.coerceIn(0, REVEAL_STAGGER_CAP)

/**
 * Board-wide motion inputs handed to every widget slot: the reveal memory, the reduced-motion flag,
 * the shared skeleton pulse and the grid state used to find an item's on-screen position.
 */
@Stable
class HomeBoardMotion(
    val tracker: WidgetRevealTracker,
    val reducedMotion: Boolean,
    val pulse: MagicSkeletonPulse,
    private val gridState: LazyGridState?,
) {
    /** The item's index among the items currently inside the viewport, or null when off-screen. */
    fun visibleIndexOf(key: Any): Int? {
        val info = gridState?.layoutInfo ?: return null
        val onScreen = info.visibleItemsInfo.filter {
            it.offset.y + it.size.height > info.viewportStartOffset && it.offset.y < info.viewportEndOffset
        }
        return onScreen.indexOfFirst { it.key == key }.takeIf { it >= 0 }
    }
}

/** Grid key of a placed widget. */
internal fun homeWidgetKey(type: HomeWidgetType): String = "widget_${type.persistedId}"

/**
 * Whether [HomeWidgetType.CONTEXT_HERO] takes a board slot. The hero only ever draws the first-steps
 * welcome, so it is dropped (no empty grid gap) once the steps are done. While it is still loading it
 * reserves its slot only when the user has not finished the steps before, so a returning user never
 * sees a hero placeholder that then vanishes.
 *
 * @param holdCompleted true while the just-emptied welcome plays its completion card.
 */
internal fun heroTakesSlot(
    hero: HomeHeroState,
    firstStepsCompletionSeen: Boolean?,
    holdCompleted: Boolean,
): Boolean = when (hero) {
    HomeHeroState.Loading -> firstStepsCompletionSeen == false
    is HomeHeroState.Welcome -> hero.steps.isNotEmpty() || holdCompleted
    else -> false
}

/**
 * The placed widgets the board actually renders, in order. Widgets that would draw nothing are
 * dropped here, never inside the item, so they leave no empty grid gap.
 *
 * @param trendingEmpty true when the resolved trending snapshot has nothing to show.
 * @param puzzleEnabled the Daily Puzzle release flag.
 * @param holdCompletedHero see [heroTakesSlot].
 */
internal fun boardWidgetsToRender(
    state: HomeUiState,
    extras: HomeWidgetExtras,
    trendingEmpty: Boolean,
    puzzleEnabled: Boolean,
    holdCompletedHero: Boolean = false,
): List<WidgetInstance> = state.layout
    .distinctBy { it.type.persistedId }
    .filterNot { widget ->
        when (widget.type) {
            HomeWidgetType.CONTEXT_HERO ->
                !heroTakesSlot(state.hero, state.firstStepsCompletionSeen, holdCompletedHero)
            HomeWidgetType.DAILY_PUZZLE -> !puzzleEnabled
            // Keeps its slot while loading; only a confirmed empty result removes it.
            HomeWidgetType.TRENDING_COMMANDERS -> extras.trendingLoaded && trendingEmpty
            else -> widget.type.isGamification && !state.gamificationEnabled
        }
    }
