package com.mmg.manahub.feature.stats.presentation

import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure computed properties on [StatsUiState]'s Phase 2/3 (2026-07 stats
 * expansion) presentation model additions: [SetCompletion.completionRatio],
 * [ModeWinrateItem.winrate], and [PlayerCountWinrateItem.winrate]/[PlayerCountWinrateItem.isFourPlus].
 *
 * No mocks/coroutines needed -- these are plain data-class getters.
 */
class StatsUiStateTest {

    private fun magicSet(cardCount: Int) = MagicSet(
        code = "tst",
        name = "Test Set",
        setType = SetType.EXPANSION,
        releasedAt = "2020-01-01",
        cardCount = cardCount,
        iconSvgUri = "",
    )

    // ── SetCompletion.completionRatio ───────────────────────────────────────────

    @Test
    fun `completionRatio divides owned count by the set's card count`() {
        val completion = SetCompletion(set = magicSet(cardCount = 50), ownedCount = 25)

        assertEquals(0.5f, completion.completionRatio)
    }

    @Test
    fun `completionRatio guards division by zero when card count is unknown`() {
        val completion = SetCompletion(set = magicSet(cardCount = 0), ownedCount = 5)

        assertEquals(0f, completion.completionRatio)
    }

    @Test
    fun `completionRatio clamps to 100 percent when owned exceeds the set's card count`() {
        // Defensive coerceIn -- a stale Scryfall card_count or a miscounted owned total must
        // never render a completion ratio above 100%.
        val completion = SetCompletion(set = magicSet(cardCount = 10), ownedCount = 15)

        assertEquals(1f, completion.completionRatio)
    }

    @Test
    fun `completionRatio is exactly 100 percent when owned equals card count`() {
        val completion = SetCompletion(set = magicSet(cardCount = 10), ownedCount = 10)

        assertEquals(1f, completion.completionRatio)
    }

    // ── ModeWinrateItem.winrate ──────────────────────────────────────────────────

    @Test
    fun `ModeWinrateItem winrate is zero when no games were played`() {
        val item = ModeWinrateItem(mode = "STANDARD", totalGames = 0, wins = 0)

        assertEquals(0f, item.winrate)
    }

    @Test
    fun `ModeWinrateItem winrate divides wins by total games`() {
        val item = ModeWinrateItem(mode = "STANDARD", totalGames = 4, wins = 3)

        assertEquals(0.75f, item.winrate)
    }

    // ── PlayerCountWinrateItem.winrate / isFourPlus ─────────────────────────────

    @Test
    fun `PlayerCountWinrateItem winrate is zero when no games were played`() {
        val item = PlayerCountWinrateItem(playerCount = 2, totalGames = 0, wins = 0)

        assertEquals(0f, item.winrate)
    }

    @Test
    fun `PlayerCountWinrateItem winrate divides wins by total games`() {
        val item = PlayerCountWinrateItem(playerCount = 3, totalGames = 5, wins = 2)

        assertEquals(0.4f, item.winrate)
    }

    @Test
    fun `PlayerCountWinrateItem isFourPlus is false below 4 players`() {
        assertFalse(PlayerCountWinrateItem(playerCount = 2, totalGames = 1, wins = 1).isFourPlus)
        assertFalse(PlayerCountWinrateItem(playerCount = 3, totalGames = 1, wins = 1).isFourPlus)
    }

    @Test
    fun `PlayerCountWinrateItem isFourPlus is true at 4 or more players`() {
        assertTrue(PlayerCountWinrateItem(playerCount = 4, totalGames = 1, wins = 1).isFourPlus)
        assertTrue(PlayerCountWinrateItem(playerCount = 6, totalGames = 1, wins = 1).isFourPlus)
    }
}
