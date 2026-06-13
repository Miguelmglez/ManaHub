package com.mmg.manahub.core.gamification.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kotlin ↔ SQL parity for [LevelCurve] (ADR-002 §11, Phase 4).
 *
 * The Supabase `batch_upsert_xp_transactions` RPC recomputes `player_progression.level` server-side
 * using the SAME `round(100 * level^1.5)` curve, with these hardcoded cumulative thresholds (XP to
 * REACH a level): L1=0, L2=100, L5=1703, L10=11106, L20=67135. If the client and the SQL ever diverge,
 * a player's level would flip-flop on every sync. This test pins the boundary values so a change to
 * either side fails loudly — and per the Phase 4 brief, the FIX is to correct the SQL, never the
 * client (the client formula is canonical, ADR-002 §6).
 */
class LevelCurveParityTest {

    @Test
    fun `cumulative thresholds match the SQL backend constants`() {
        assertEquals("L1 cumulative XP", 0L, LevelCurve.cumulativeXpForLevel[1])
        assertEquals("L2 cumulative XP", 100L, LevelCurve.cumulativeXpForLevel[2])
        assertEquals("L5 cumulative XP", 1703L, LevelCurve.cumulativeXpForLevel[5])
        assertEquals("L10 cumulative XP", 11106L, LevelCurve.cumulativeXpForLevel[10])
        assertEquals("L20 cumulative XP", 67135L, LevelCurve.cumulativeXpForLevel[20])
    }

    @Test
    fun `levelForTotalXp agrees with the SQL boundaries`() {
        assertEquals("99 XP is still level 1", 1, LevelCurve.levelForTotalXp(99L))
        assertEquals("100 XP reaches level 2", 2, LevelCurve.levelForTotalXp(100L))
        assertEquals("1702 XP is still level 4", 4, LevelCurve.levelForTotalXp(1702L))
        assertEquals("1703 XP reaches level 5", 5, LevelCurve.levelForTotalXp(1703L))
        assertEquals("11106 XP reaches level 10", 10, LevelCurve.levelForTotalXp(11106L))
        assertEquals("67135 XP reaches level 20", 20, LevelCurve.levelForTotalXp(67135L))
    }
}
