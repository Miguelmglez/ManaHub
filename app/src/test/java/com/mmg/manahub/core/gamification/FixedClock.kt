package com.mmg.manahub.core.gamification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A [Clock] implementation that always returns a fixed [instant].
 * Replaces `java.time.Clock.fixed(...)` in gamification unit tests.
 */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
