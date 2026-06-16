package com.mmg.manahub.core.gamification.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [IdempotencyKeyScoper] (ADR-002 §L3): device-scoped keys are prefixed per device,
 * globally-stable keys are returned verbatim, and the same device produces the same key (replay-safe).
 */
class IdempotencyKeyScoperTest {

    @Test
    fun `device-scoped key is prefixed with the device id`() {
        assertEquals(
            "dev:device-A:game:42:result",
            IdempotencyKeyScoper.scope("game:42:result", "device-A", deviceScoped = true),
        )
    }

    @Test
    fun `two devices produce different keys for the same raw key`() {
        val a = IdempotencyKeyScoper.scope("game:42:result", "device-A", deviceScoped = true)
        val b = IdempotencyKeyScoper.scope("game:42:result", "device-B", deviceScoped = true)
        assertEquals("dev:device-A:game:42:result", a)
        assertEquals("dev:device-B:game:42:result", b)
        assert(a != b)
    }

    @Test
    fun `globally-stable key is returned verbatim regardless of device`() {
        assertEquals(
            "achievement:GAMES_PLAYED_10:tier:1",
            IdempotencyKeyScoper.scope(
                "achievement:GAMES_PLAYED_10:tier:1", "device-A", deviceScoped = false,
            ),
        )
        // Same key on a different device — must be identical (dedupe to one grant after merge).
        assertEquals(
            IdempotencyKeyScoper.scope("app_open:2026-06-11", "device-A", deviceScoped = false),
            IdempotencyKeyScoper.scope("app_open:2026-06-11", "device-B", deviceScoped = false),
        )
    }

    @Test
    fun `the same device produces a stable key (replay-safe)`() {
        val first = IdempotencyKeyScoper.scope("survey:5", "device-A", deviceScoped = true)
        val second = IdempotencyKeyScoper.scope("survey:5", "device-A", deviceScoped = true)
        assertEquals(first, second)
    }
}
