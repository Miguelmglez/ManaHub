package com.mmg.manahub.feature.tournament.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for [TournamentIdCodec] — the single source of truth for the hand-rolled match
 * encoding (audit M2). Covers empty / single / malformed inputs for both the id-list and the
 * life-totals map so the previously-duplicated `trim('[',']').split(",")` idiom can never diverge again.
 */
class TournamentIdCodecTest {

    // ── decodeIds ──────────────────────────────────────────────────────────────

    @Test
    fun `decodeIds of a normal pairing returns both ids in order`() {
        assertEquals(listOf(10L, 20L), TournamentIdCodec.decodeIds("[10,20]"))
    }

    @Test
    fun `decodeIds of a single-player bye returns one id`() {
        assertEquals(listOf(42L), TournamentIdCodec.decodeIds("[42]"))
    }

    @Test
    fun `decodeIds of empty brackets returns empty list`() {
        assertTrue(TournamentIdCodec.decodeIds("[]").isEmpty())
    }

    @Test
    fun `decodeIds of blank string returns empty list`() {
        assertTrue(TournamentIdCodec.decodeIds("").isEmpty())
        assertTrue(TournamentIdCodec.decodeIds("   ").isEmpty())
    }

    @Test
    fun `decodeIds tolerates surrounding whitespace on entries`() {
        assertEquals(listOf(1L, 2L), TournamentIdCodec.decodeIds("[ 1 , 2 ]"))
    }

    @Test
    fun `decodeIds drops non-numeric entries instead of throwing`() {
        // Malformed entries degrade gracefully — a garbage token is silently skipped.
        assertEquals(listOf(7L), TournamentIdCodec.decodeIds("[abc,7]"))
        assertTrue(TournamentIdCodec.decodeIds("[abc,def]").isEmpty())
    }

    @Test
    fun `decodeIds tolerates historic double-encoding`() {
        // trim('[',']') strips ALL leading/trailing brackets (defensive against past double-encoding).
        assertEquals(listOf(1L, 2L), TournamentIdCodec.decodeIds("[[1,2]]"))
    }

    // ── encodeIds round-trip ─────────────────────────────────────────────────────

    @Test
    fun `encodeIds produces the legacy on-disk format`() {
        assertEquals("[10,20]", TournamentIdCodec.encodeIds(listOf(10L, 20L)))
        assertEquals("[5]", TournamentIdCodec.encodeIds(listOf(5L)))
    }

    @Test
    fun `encode then decode ids is an identity`() {
        val ids = listOf(3L, 8L, 15L)
        assertEquals(ids, TournamentIdCodec.decodeIds(TournamentIdCodec.encodeIds(ids)))
    }

    // ── decodeLifeTotals ─────────────────────────────────────────────────────────

    @Test
    fun `decodeLifeTotals of a normal map returns both entries`() {
        assertEquals(mapOf(1L to 15, 2L to 0), TournamentIdCodec.decodeLifeTotals("{1:15,2:0}"))
    }

    @Test
    fun `decodeLifeTotals of blank or empty braces returns empty map`() {
        assertTrue(TournamentIdCodec.decodeLifeTotals("").isEmpty())
        assertTrue(TournamentIdCodec.decodeLifeTotals("{}").isEmpty())
    }

    @Test
    fun `decodeLifeTotals preserves negative life`() {
        assertEquals(mapOf(1L to -3), TournamentIdCodec.decodeLifeTotals("{1:-3}"))
    }

    @Test
    fun `decodeLifeTotals skips entries with no colon or non-numeric parts`() {
        // "bad" has no colon; "x:y" has non-numeric id/life — both skipped, the valid one survives.
        assertEquals(mapOf(2L to 5), TournamentIdCodec.decodeLifeTotals("{bad,x:y,2:5}"))
    }

    @Test
    fun `encode then decode life totals is an identity`() {
        val life = mapOf(1L to 20, 2L to 7)
        assertEquals(life, TournamentIdCodec.decodeLifeTotals(TournamentIdCodec.encodeLifeTotals(life)))
    }
}
