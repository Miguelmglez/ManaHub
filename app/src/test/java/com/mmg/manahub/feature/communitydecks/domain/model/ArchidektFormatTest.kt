package com.mmg.manahub.feature.communitydecks.domain.model

import com.mmg.manahub.core.model.ArchidektFormat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [ArchidektFormat] — the mapping from Archidekt's numeric format ids
 * to ManaHub's internal format strings.
 */
class ArchidektFormatTest {

    // ── Group 1: fromApiId — known formats ──────────────────────────────────

    @Test
    fun `fromApiId returns STANDARD for id 1`() {
        assertEquals(ArchidektFormat.STANDARD, ArchidektFormat.fromApiId(1))
    }

    @Test
    fun `fromApiId returns MODERN for id 2`() {
        assertEquals(ArchidektFormat.MODERN, ArchidektFormat.fromApiId(2))
    }

    @Test
    fun `fromApiId returns COMMANDER for id 3`() {
        assertEquals(ArchidektFormat.COMMANDER, ArchidektFormat.fromApiId(3))
    }

    @Test
    fun `fromApiId returns LEGACY for id 4`() {
        assertEquals(ArchidektFormat.LEGACY, ArchidektFormat.fromApiId(4))
    }

    @Test
    fun `fromApiId returns VINTAGE for id 5`() {
        assertEquals(ArchidektFormat.VINTAGE, ArchidektFormat.fromApiId(5))
    }

    @Test
    fun `fromApiId returns PAUPER for id 6`() {
        assertEquals(ArchidektFormat.PAUPER, ArchidektFormat.fromApiId(6))
    }

    @Test
    fun `fromApiId returns PIONEER for id 15`() {
        assertEquals(ArchidektFormat.PIONEER, ArchidektFormat.fromApiId(15))
    }

    @Test
    fun `fromApiId returns BRAWL for id 13`() {
        assertEquals(ArchidektFormat.BRAWL, ArchidektFormat.fromApiId(13))
    }

    @Test
    fun `fromApiId returns OATHBREAKER for id 14`() {
        assertEquals(ArchidektFormat.OATHBREAKER, ArchidektFormat.fromApiId(14))
    }

    @Test
    fun `fromApiId returns HISTORIC for id 16`() {
        assertEquals(ArchidektFormat.HISTORIC, ArchidektFormat.fromApiId(16))
    }

    @Test
    fun `fromApiId returns PAUPER_COMMANDER for id 17`() {
        assertEquals(ArchidektFormat.PAUPER_COMMANDER, ArchidektFormat.fromApiId(17))
    }

    // ── Group 2: fromApiId — null for unknown ───────────────────────────────

    @Test
    fun `fromApiId returns null for unknown id 0`() {
        assertNull(ArchidektFormat.fromApiId(0))
    }

    @Test
    fun `fromApiId returns null for unknown id 99`() {
        assertNull(ArchidektFormat.fromApiId(99))
    }

    @Test
    fun `fromApiId returns null for negative id`() {
        assertNull(ArchidektFormat.fromApiId(-1))
    }

    // ── Group 3: toManaHubFormat — direct mappings ──────────────────────────

    @Test
    fun `toManaHubFormat maps standard correctly`() {
        assertEquals("standard", ArchidektFormat.toManaHubFormat(1))
    }

    @Test
    fun `toManaHubFormat maps modern correctly`() {
        assertEquals("modern", ArchidektFormat.toManaHubFormat(2))
    }

    @Test
    fun `toManaHubFormat maps commander correctly`() {
        assertEquals("commander", ArchidektFormat.toManaHubFormat(3))
    }

    @Test
    fun `toManaHubFormat maps legacy correctly`() {
        assertEquals("legacy", ArchidektFormat.toManaHubFormat(4))
    }

    @Test
    fun `toManaHubFormat maps vintage correctly`() {
        assertEquals("vintage", ArchidektFormat.toManaHubFormat(5))
    }

    @Test
    fun `toManaHubFormat maps pauper correctly`() {
        assertEquals("pauper", ArchidektFormat.toManaHubFormat(6))
    }

    @Test
    fun `toManaHubFormat maps pioneer correctly`() {
        assertEquals("pioneer", ArchidektFormat.toManaHubFormat(15))
    }

    // ── Group 4: toManaHubFormat — lossy mappings to casual ─────────────────

    @Test
    fun `toManaHubFormat maps Custom id 7 to casual`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(7))
    }

    @Test
    fun `toManaHubFormat maps Frontier id 8 to casual`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(8))
    }

    @Test
    fun `toManaHubFormat maps Penny Dreadful id 10 to casual`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(10))
    }

    // ── Group 5: toManaHubFormat — defaults to casual for unknown ───────────

    @Test
    fun `toManaHubFormat returns casual for unknown id`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(999))
    }

    @Test
    fun `toManaHubFormat returns casual for id 0`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(0))
    }

    @Test
    fun `toManaHubFormat returns casual for negative id`() {
        assertEquals("casual", ArchidektFormat.toManaHubFormat(-5))
    }

    // ── Group 6: Commander variants map to commander ────────────────────────

    @Test
    fun `toManaHubFormat maps Commander 1v1 id 11 to commander`() {
        assertEquals("commander", ArchidektFormat.toManaHubFormat(11))
    }

    @Test
    fun `toManaHubFormat maps Duel Commander id 12 to commander`() {
        assertEquals("commander", ArchidektFormat.toManaHubFormat(12))
    }

    // ── Group 7: Future Standard maps to standard ───────────────────────────

    @Test
    fun `toManaHubFormat maps Future Standard id 9 to standard`() {
        assertEquals("standard", ArchidektFormat.toManaHubFormat(9))
    }

    // ── Group 8: Pauper Commander maps to pauper ────────────────────────────

    @Test
    fun `toManaHubFormat maps Pauper Commander id 17 to pauper`() {
        assertEquals("pauper", ArchidektFormat.toManaHubFormat(17))
    }

    // ── Group 9: enum coverage — all entries have unique apiId ───────────────

    @Test
    fun `all enum entries have unique apiId values`() {
        val ids = ArchidektFormat.entries.map { it.apiId }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `all enum entries have a non-blank manahubFormat`() {
        ArchidektFormat.entries.forEach { entry ->
            assert(entry.manahubFormat.isNotBlank()) {
                "${entry.name} has a blank manahubFormat"
            }
        }
    }
}
