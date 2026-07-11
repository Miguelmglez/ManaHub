package com.mmg.manahub.core.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for the client-side mirror of the Worker's canonical-key derivation (Phase 3.3) —
 * see `cloudflare/manahub-community/src/lib/archidekt.ts` and
 * `docs/adr/ADR-004-community-api-contracts.md`. `commonMain`/`commonTest` — no Android
 * dependency, runs on every KMP target.
 */
class CommunityAggregateKeysTest {

    @Test
    fun `canonical key is order-independent`() {
        val a = CommunityAggregateKeys.buildCanonicalKey(listOf("Krenko, Mob Boss", "Goblin Bombardment"))
        val b = CommunityAggregateKeys.buildCanonicalKey(listOf("Goblin Bombardment", "Krenko, Mob Boss"))
        assertEquals(a, b)
    }

    @Test
    fun `canonical key is slug-safe`() {
        val key = CommunityAggregateKeys.buildCanonicalKey(listOf("Krenko, Mob Boss"))
        assertTrue(key.matches(Regex("^[a-z0-9+-]+$")))
        assertEquals("krenko-mob-boss", key)
    }

    @Test
    fun `commander cache key is slugified and namespaced`() {
        assertEquals(
            "agg:commander:atraxa-praetors-voice",
            CommunityAggregateKeys.commanderCacheKey("Atraxa, Praetors' Voice"),
        )
    }

    @Test
    fun `sixty cache key includes the format and canonical key`() {
        assertEquals(
            "agg:3:goblin-bombardment+krenko-mob-boss",
            CommunityAggregateKeys.sixtyCacheKey(3, listOf("Krenko, Mob Boss", "Goblin Bombardment")),
        )
    }
}
