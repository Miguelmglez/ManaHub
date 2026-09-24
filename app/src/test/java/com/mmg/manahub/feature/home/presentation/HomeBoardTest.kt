package com.mmg.manahub.feature.home.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.WidgetSize
import com.mmg.manahub.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure board logic behind the Home loading/motion contract: reveal memory, filtering, readiness, size tokens. */
class HomeBoardTest {

    // ── Reveal once per process ────────────────────────────────────────────────

    @Test
    fun `reveal tracker reports a widget as new only the first time`() {
        val tracker = WidgetRevealTracker()

        assertFalse(tracker.isRevealed(HomeWidgetType.MTG_NEWS))
        assertTrue(tracker.markRevealed(HomeWidgetType.MTG_NEWS))
        assertTrue(tracker.isRevealed(HomeWidgetType.MTG_NEWS))
        assertFalse(tracker.markRevealed(HomeWidgetType.MTG_NEWS))
    }

    @Test
    fun `reveal tracker keeps widgets independent`() {
        val tracker = WidgetRevealTracker()
        tracker.markRevealed(HomeWidgetType.MTG_NEWS)

        assertFalse(tracker.isRevealed(HomeWidgetType.RULES_TIP))
        assertTrue(tracker.markRevealed(HomeWidgetType.RULES_TIP))
    }

    @Test
    fun `stagger grows per visible position and is capped`() {
        assertEquals(0L, revealStaggerDelayMs(null))
        assertEquals(0L, revealStaggerDelayMs(0))
        assertEquals(3 * REVEAL_STAGGER_MS, revealStaggerDelayMs(3))
        assertEquals(REVEAL_STAGGER_CAP * REVEAL_STAGGER_MS, revealStaggerDelayMs(REVEAL_STAGGER_CAP))
        assertEquals(REVEAL_STAGGER_CAP * REVEAL_STAGGER_MS, revealStaggerDelayMs(40))
    }

    // ── Board filtering ────────────────────────────────────────────────────────

    private fun layoutOf(vararg types: HomeWidgetType) = types.map { WidgetInstance(it, WidgetSize.MEDIUM) }

    private fun render(
        state: HomeUiState,
        extras: HomeWidgetExtras = HomeWidgetExtras(),
        trendingEmpty: Boolean = false,
        puzzleEnabled: Boolean = true,
        holdCompletedHero: Boolean = false,
    ) = boardWidgetsToRender(state, extras, trendingEmpty, puzzleEnabled, holdCompletedHero).map { it.type }

    @Test
    fun `trending keeps its slot while loading and leaves only on a confirmed empty result`() {
        val state = HomeUiState(layout = layoutOf(HomeWidgetType.TRENDING_COMMANDERS))

        assertEquals(
            listOf(HomeWidgetType.TRENDING_COMMANDERS),
            render(state, HomeWidgetExtras(trendingLoaded = false), trendingEmpty = true),
        )
        assertEquals(emptyList<HomeWidgetType>(), render(state, HomeWidgetExtras(trendingLoaded = true), trendingEmpty = true))
        assertEquals(
            listOf(HomeWidgetType.TRENDING_COMMANDERS),
            render(state, HomeWidgetExtras(trendingLoaded = true), trendingEmpty = false),
        )
    }

    @Test
    fun `flag-gated widgets are dropped instead of leaving an empty grid gap`() {
        val state = HomeUiState(
            layout = layoutOf(
                HomeWidgetType.DAILY_PUZZLE,
                HomeWidgetType.COMPETITIVE,
                HomeWidgetType.PROGRESSION_HUB,
                HomeWidgetType.MTG_NEWS,
            ),
            gamificationEnabled = false,
        )

        val rendered = render(state, HomeWidgetExtras(competitiveEnabled = false), puzzleEnabled = false)

        assertEquals(listOf(HomeWidgetType.MTG_NEWS), rendered)
    }

    @Test
    fun `competitive keeps its slot until its flag is known`() {
        val state = HomeUiState(layout = layoutOf(HomeWidgetType.COMPETITIVE))

        assertEquals(listOf(HomeWidgetType.COMPETITIVE), render(state, HomeWidgetExtras(competitiveEnabled = null)))
    }

    @Test
    fun `duplicate layout entries render once`() {
        val state = HomeUiState(layout = layoutOf(HomeWidgetType.MTG_NEWS, HomeWidgetType.MTG_NEWS))

        assertEquals(listOf(HomeWidgetType.MTG_NEWS), render(state))
    }

    @Test
    fun `hero reserves a slot while loading only for users who never finished the steps`() {
        assertTrue(heroTakesSlot(HomeHeroState.Loading, firstStepsCompletionSeen = false, holdCompleted = false))
        assertFalse(heroTakesSlot(HomeHeroState.Loading, firstStepsCompletionSeen = true, holdCompleted = false))
        assertFalse(heroTakesSlot(HomeHeroState.Loading, firstStepsCompletionSeen = null, holdCompleted = false))
    }

    @Test
    fun `hero with no steps leaves the board unless its completion card is playing`() {
        val done = HomeHeroState.Welcome(steps = emptyList())
        val pending = HomeHeroState.Welcome(steps = ALL_FIRST_STEPS.take(1))

        assertTrue(heroTakesSlot(pending, firstStepsCompletionSeen = true, holdCompleted = false))
        assertFalse(heroTakesSlot(done, firstStepsCompletionSeen = true, holdCompleted = false))
        assertTrue(heroTakesSlot(done, firstStepsCompletionSeen = true, holdCompleted = true))
        assertFalse(heroTakesSlot(HomeHeroState.Summary("Jace", 3), firstStepsCompletionSeen = true, holdCompleted = true))
    }

    // ── Readiness ──────────────────────────────────────────────────────────────

    private val card = DiscoverCard(id = "1", scryfallId = "s1", name = "Opt", imageUrl = null)

    @Test
    fun `discover stays ready through a background refresh that keeps its cards`() {
        val refreshing = HomeUiState(
            boardReady = true,
            auth = AuthGate.SignedOut,
            discoverCards = listOf(card),
            discoverLoadState = DiscoverLoadState.LOADING,
        )

        assertTrue(HomeWidgetType.DISCOVER_CARDS.isReady(refreshing, HomeWidgetExtras()))
    }

    @Test
    fun `discover is not ready while its first fetch is in flight`() {
        val firstLoad = HomeUiState(
            boardReady = true,
            auth = AuthGate.SignedOut,
            discoverCards = emptyList(),
            discoverLoadState = DiscoverLoadState.LOADING,
        )

        assertFalse(HomeWidgetType.DISCOVER_CARDS.isReady(firstLoad, HomeWidgetExtras()))
    }

    // ── Size tokens ────────────────────────────────────────────────────────────

    private fun lineHeights(scale: Float) = HomeLineHeights(
        titleMedium = 24.dp * scale,
        labelLarge = 18.dp * scale,
        labelMedium = 16.dp * scale,
        labelSmall = 14.dp * scale,
        bodyLarge = 24.dp * scale,
        bodyMedium = 20.dp * scale,
        bodySmall = 18.dp * scale,
        displayMedium = 36.dp * scale,
    )

    private val selfFramedTypes = setOf(HomeWidgetType.CARD_OF_THE_DAY, HomeWidgetType.RULES_TIP)

    @Test
    fun `every widget except the self-framed ones has a fixed body height`() {
        val metrics = computeHomeWidgetMetrics(lineHeights(1f), Spacing())

        HomeWidgetType.entries.forEach { type ->
            if (type in selfFramedTypes) {
                assertNull("$type", metrics.bodyHeight(type))
            } else {
                assertTrue("$type", (metrics.bodyHeight(type) ?: 0.dp) > 0.dp)
            }
        }
    }

    @Test
    fun `hub slides never drop below the floor`() {
        val metrics = computeHomeWidgetMetrics(lineHeights(1f), Spacing())

        listOf(metrics.gameStatsSlideHeight, metrics.collectionSlideHeight, metrics.tradesSlideHeight).forEach {
            assertTrue("$it", it >= HubSlideMinHeight)
        }
    }

    @Test
    fun `three recent trades fit the trades slide at the default font scale`() {
        val metrics = computeHomeWidgetMetrics(lineHeights(1f), Spacing())
        val sp = Spacing()
        // Each recent-trade row is a 48dp touch target.
        val threeRows = 16.dp + sp.xs + 48.dp * 3 + sp.xs * 3

        assertTrue(metrics.tradesSlideHeight >= threeRows)
    }

    @Test
    fun `text-bearing tokens grow with the font scale`() {
        val base = computeHomeWidgetMetrics(lineHeights(1f), Spacing())
        val large = computeHomeWidgetMetrics(lineHeights(1.3f), Spacing())

        assertTrue(large.newsCardHeight > base.newsCardHeight)
        assertTrue(large.collectionSlideHeight > base.collectionSlideHeight)
        HomeWidgetType.entries.filterNot { it in selfFramedTypes }.forEach { type ->
            assertTrue("$type", large.bodyHeight(type)!! >= base.bodyHeight(type)!!)
        }
    }

    @Test
    fun `news cards reserve a two-line title`() {
        val metrics = computeHomeWidgetMetrics(lineHeights(1f), Spacing())
        val thumbnail = HomeNewsCardWidth * 9f / 16f
        val twoLineCard: Dp = thumbnail + 12.dp * 2 + 24.dp * 2 + 6.dp + 14.dp

        assertTrue(metrics.newsCardHeight >= twoLineCard)
    }
}
