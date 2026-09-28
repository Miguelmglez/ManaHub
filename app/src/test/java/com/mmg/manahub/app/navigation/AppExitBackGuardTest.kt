package com.mmg.manahub.app.navigation

import com.mmg.manahub.app.navigation.AppExitBackGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the double-Back-to-exit window of [AppExitBackGuard]. */
class AppExitBackGuardTest {

    private var now = 10_000L
    private val guard = AppExitBackGuard(windowMs = 2_000L, clock = { now })

    @Test
    fun `first back shows the prompt`() {
        assertEquals(Decision.SHOW_PROMPT, guard.onBackPressed())
    }

    @Test
    fun `second back within the window exits`() {
        guard.onBackPressed()
        now += 1_500L
        assertEquals(Decision.EXIT, guard.onBackPressed())
    }

    @Test
    fun `second back exactly at the window edge still exits`() {
        guard.onBackPressed()
        now += 2_000L
        assertEquals(Decision.EXIT, guard.onBackPressed())
    }

    @Test
    fun `second back after the window prompts again and opens a new window`() {
        guard.onBackPressed()
        now += 2_001L
        assertEquals(Decision.SHOW_PROMPT, guard.onBackPressed())
        now += 500L
        assertEquals(Decision.EXIT, guard.onBackPressed())
    }

    @Test
    fun `an exit consumes the window`() {
        guard.onBackPressed()
        guard.onBackPressed()
        now += 100L
        assertEquals(Decision.SHOW_PROMPT, guard.onBackPressed())
    }

    @Test
    fun `reset forgets a pending prompt`() {
        guard.onBackPressed()
        guard.reset()
        now += 100L
        assertEquals(Decision.SHOW_PROMPT, guard.onBackPressed())
    }

    @Test
    fun `a clock that moves backwards never exits`() {
        guard.onBackPressed()
        now -= 500L
        assertEquals(Decision.SHOW_PROMPT, guard.onBackPressed())
    }
}
